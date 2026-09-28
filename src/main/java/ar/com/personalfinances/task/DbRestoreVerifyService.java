package ar.com.personalfinances.task;

import ar.com.personalfinances.entity.InstanceTask;
import ar.com.personalfinances.util.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service("DbRestoreVerifyService")
public class DbRestoreVerifyService extends BaseInstanceTaskService {

    private final static DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final static Pattern FILE_NAME_PATTERN = Pattern.compile("^.+_(\\d{8})\\.sql$");
    private final static String REQUIRED_SCRATCH_DB_SUFFIX = "_restorecheck";

    private final BackupPaths backupPaths;
    private final MysqlClient mysqlClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DbRestoreVerifyService(BackupPaths backupPaths, MysqlClient mysqlClient) {
        this.backupPaths = backupPaths;
        this.mysqlClient = mysqlClient;
    }

    @Override
    public CommonResult runTask(InstanceTask instanceTask) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[dbRestoreVerify] Ya hay una verificacion en ejecucion, se omite la corrida");
            return CommonResult.warn("Ya hay una verificacion en ejecucion");
        }
        try {
            return doRun(instanceTask);
        } finally {
            running.set(false);
        }
    }

    private CommonResult doRun(InstanceTask instanceTask) {
        Map<String, String> configs = instanceTask.getConfisAsMap();
        final String dbName = configs.get("dbName");
        if (!StringUtils.hasText(dbName)) {
            log.error("[dbRestoreVerify] La tarea {} no tiene el parametro dbName", instanceTask.getTaskKey());
            return CommonResult.error("La tarea no tiene el parametro dbName configurado");
        }
        final String dbUser = configs.get("dbUser");
        final String dbPassword = configs.get("dbPassword");
        if (!StringUtils.hasText(dbUser) || !StringUtils.hasText(dbPassword)) {
            log.error("[dbRestoreVerify] La tarea {} no tiene dbUser y dbPassword configurados", instanceTask.getTaskKey());
            return CommonResult.error("La tarea no tiene las credenciales configuradas");
        }

        String scratchDb = StringUtils.hasText(configs.get("scratchDb")) ? configs.get("scratchDb") : dbName + REQUIRED_SCRATCH_DB_SUFFIX;
        String scratchDbProblem = validateScratchDb(dbName, scratchDb);
        if (scratchDbProblem != null) {
            log.error("[dbRestoreVerify] Configuracion de scratchDb invalida: {}", scratchDbProblem);
            return CommonResult.error(scratchDbProblem);
        }

        try {
            return verify(dbName, scratchDb, dbUser, dbPassword);
        } catch (UncheckedIOException | IllegalStateException e) {
            log.error("[dbRestoreVerify] Error inesperado durante la verificacion", e);
            return CommonResult.error("Se produjo un error durante la verificacion: " + e.getMessage());
        }
    }

    private String validateScratchDb(String dbName, String scratchDb) {
        if (!scratchDb.equals(dbName + REQUIRED_SCRATCH_DB_SUFFIX)) {
            return "La base scratch debe llamarse exactamente " + dbName + REQUIRED_SCRATCH_DB_SUFFIX;
        }
        return null;
    }

    private CommonResult verify(String dbName, String scratchDb, String dbUser, String dbPassword) {
        Path backupFile = findLatestBackup(dbName);
        if (backupFile == null) {
            log.error("[dbRestoreVerify] No se encontro ningun backup en {}", backupPaths.backupDir(dbName));
            return CommonResult.error("No se encontro ningun backup para verificar en " + backupPaths.backupDir(dbName));
        }
        log.info("[dbRestoreVerify] Verificando el backup {} sobre la base {}", backupFile, scratchDb);

        Path schemaFile = createSchemaDump(dbName, dbUser, dbPassword);
        try {
            CommonResult recreateResult = recreateScratchDb(scratchDb, dbUser, dbPassword);
            if (recreateResult.isError()) return recreateResult;

            CommonResult schemaResult = loadInto(scratchDb, dbUser, dbPassword, schemaFile, "el schema");
            if (schemaResult.isError()) return schemaResult;

            CommonResult dataResult = loadInto(scratchDb, dbUser, dbPassword, backupFile, "los datos");
            if (dataResult.isError()) return dataResult;

            return compareCounts(scratchDb, dbUser, dbPassword, backupFile);
        } finally {
            deleteQuietly(schemaFile);
        }
    }

    private Path findLatestBackup(String dbName) {
        Path backupDir = backupPaths.backupDir(dbName);
        if (!Files.isDirectory(backupDir)) return null;
        try (Stream<Path> files = Files.list(backupDir)) {
            return files.filter(path -> FILE_NAME_PATTERN.matcher(path.getFileName().toString()).matches())
                    .filter(path -> sizeOf(path) > 0L)
                    .max(Comparator.comparing(this::dateOf))
                    .orElse(null);
        } catch (IOException e) {
            log.error("[dbRestoreVerify] No se pudo listar el directorio de backups {}", backupDir, e);
            return null;
        }
    }

    private LocalDate dateOf(Path path) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) return LocalDate.MIN;
        try {
            return LocalDate.parse(matcher.group(1), FILE_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return LocalDate.MIN;
        }
    }

    private Path createSchemaDump(String dbName, String dbUser, String dbPassword) {
        Path schemaFile = createTempFile("pfin-schema-", ".sql");
        List<String> options = new ArrayList<>(List.of(
                "--no-data",
                "--routines",
                "--triggers",
                "--events",
                "--no-tablespaces",
                "--set-gtid-purged=OFF",
                "--default-character-set=utf8mb4"
        ));
        options.add(dbName);

        CommonResult result = MysqlClient.toResult(mysqlClient.dump(options, schemaFile.toFile(), null, dbUser, dbPassword), "mysqldump --no-data");
        if (result.isError()) {
            deleteQuietly(schemaFile);
            throw new IllegalStateException(result.getMessage());
        }
        return schemaFile;
    }

    private CommonResult recreateScratchDb(String scratchDb, String dbUser, String dbPassword) {
        String statement = "DROP DATABASE IF EXISTS `" + scratchDb + "`; CREATE DATABASE `" + scratchDb + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;";
        MysqlClient.ProcessResult processResult = mysqlClient.execute(List.of("--execute=" + statement), null, null, dbUser, dbPassword);
        if (processResult.isSuccess()) {
            log.info("[dbRestoreVerify] Base scratch {} recreada", scratchDb);
            return CommonResult.ok();
        }
        return MysqlClient.toResult(processResult, "La recreacion de la base " + scratchDb);
    }

    private CommonResult loadInto(String scratchDb, String dbUser, String dbPassword, Path file, String description) {
        MysqlClient.ProcessResult processResult = mysqlClient.execute(List.of("--database=" + scratchDb), file.toFile(), null, dbUser, dbPassword);
        if (processResult.isSuccess()) {
            log.info("[dbRestoreVerify] {} restaurados desde {}", description, file.getFileName());
            return CommonResult.ok();
        }
        return MysqlClient.toResult(processResult, "La restauracion de " + description + " desde " + file.getFileName());
    }

    private CommonResult compareCounts(String scratchDb, String dbUser, String dbPassword, Path backupFile) {
        BackupSnapshot.Result snapshot = BackupSnapshot.read(backupFile);
        if (!snapshot.isUsable()) {
            log.error("[dbRestoreVerify] No se pudo usar el snapshot de conteos de {}: {}", backupFile.getFileName(), snapshot.problem());
            return CommonResult.error("El backup " + backupFile.getFileName() + " se restauro, pero no se pudo verificar: " + snapshot.problem());
        }

        List<String> losses = new ArrayList<>();
        for (Map.Entry<String, Long> entry : snapshot.counts().entrySet()) {
            String table = entry.getKey();
            long expected = entry.getValue();
            Long actual = countRows(scratchDb, table, dbUser, dbPassword);
            if (actual == null) {
                losses.add(table + " (no existe en la base restaurada)");
            } else if (actual < expected) {
                losses.add(table + " (esperado al menos " + expected + ", restaurado " + actual + ")");
            } else if (actual > expected) {
                log.info("[dbRestoreVerify] {} tiene {} registros, mas que los {} del snapshot porque el dump es posterior", table, actual, expected);
            } else {
                log.info("[dbRestoreVerify] {} con {} registros coincide con el snapshot", table, expected);
            }
        }

        if (!losses.isEmpty()) {
            log.error("[dbRestoreVerify] El backup {} perdio filas al restaurar: {}", backupFile, losses);
            return CommonResult.error("El backup " + backupFile.getFileName() + " no restaura correctamente. Diferencias: " + String.join("; ", losses));
        }

        log.info("[dbRestoreVerify] El backup {} restaura correctamente en {}. La base scratch queda disponible para inspeccion", backupFile, scratchDb);
        return CommonResult.ok(backupFile.toString(), "El backup " + backupFile.getFileName() + " restaura correctamente");
    }

    private Long countRows(String dbName, String table, String dbUser, String dbPassword) {
        MysqlClient.ProcessResult processResult = mysqlClient.query(dbName, "SELECT COUNT(*) FROM `" + table + "`", dbUser, dbPassword);
        if (!processResult.isSuccess() || processResult.stdout().isBlank()) {
            log.warn("[dbRestoreVerify] No se pudo leer la tabla {}.{}: {}", dbName, table, processResult.stderr());
            return null;
        }
        try {
            return Long.parseLong(processResult.stdout().trim());
        } catch (NumberFormatException e) {
            log.warn("[dbRestoreVerify] Conteo ilegible de {}.{}: {}", dbName, table, processResult.stdout());
            return null;
        }
    }

    private Path createTempFile(String prefix, String suffix) {
        try {
            return Files.createTempFile(prefix, suffix);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("[dbRestoreVerify] No se pudo eliminar el archivo temporal {}", file, e);
        }
    }
}
