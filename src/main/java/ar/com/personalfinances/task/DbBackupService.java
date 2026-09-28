package ar.com.personalfinances.task;

import ar.com.personalfinances.entity.InstanceTask;
import ar.com.personalfinances.util.CommonResult;
import ar.com.personalfinances.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service("DbBackupService")
public class DbBackupService extends BaseInstanceTaskService {

    private final static DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final static Pattern FILE_NAME_PATTERN = Pattern.compile("^.+_(\\d{8})\\.sql$");
    private final static String DUMP_HEADER_PREFIX = "-- MySQL dump";
    private final static String DEFAULT_FILE_PATTERN = "$dbName_$currentDate.sql";
    private final static String TEMP_SUFFIX = ".tmp";
    private final static String ARCHIVE_SUFFIX = ".gz";
    private final static List<String> COUNTED_TABLES = List.of("expenses", "expense_items", "tags", "accounts", "expense_mappings", "users");
    private final static int KEEP_RECENT = 10;
    private final static int KEEP_MONTHLY = 12;
    private final static int TAIL_CHECK_BYTES = 4096;

    private final static int MIN_HOURS_BETWEEN_RUNS = 6;

    private final BackupPaths backupPaths;
    private final MysqlClient mysqlClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DbBackupService(BackupPaths backupPaths, MysqlClient mysqlClient) {
        this.backupPaths = backupPaths;
        this.mysqlClient = mysqlClient;
    }

    @Override
    public int getMinHoursBetweenRuns() {
        return MIN_HOURS_BETWEEN_RUNS;
    }

    @Override
    public CommonResult runTask(InstanceTask instanceTask) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[dbBackup] Ya hay un backup en ejecucion, se omite la corrida");
            return CommonResult.warn("Ya hay un backup en ejecucion");
        }
        try {
            return doRun(instanceTask);
        } catch (UncheckedIOException | IllegalStateException e) {
            log.error("[dbBackup] Error inesperado durante el backup", e);
            return CommonResult.error("Se produjo un error durante el backup: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    private CommonResult doRun(InstanceTask instanceTask) {
        Map<String, String> configs = instanceTask.getConfisAsMap();
        final String dbName = configs.get("dbName");
        if (!StringUtils.hasText(dbName)) {
            log.error("[dbBackup] La tarea {} no tiene el parametro dbName", instanceTask.getTaskKey());
            return CommonResult.error("La tarea no tiene el parametro dbName configurado");
        }
        final String dbUser = configs.get("dbUser");
        final String dbPassword = configs.get("dbPassword");
        if (!StringUtils.hasText(dbUser) || !StringUtils.hasText(dbPassword)) {
            log.error("[dbBackup] La tarea {} no tiene dbUser y dbPassword configurados", instanceTask.getTaskKey());
            return CommonResult.error("La tarea no tiene las credenciales configuradas");
        }
        final boolean dataOnly = Boolean.parseBoolean(configs.get("data-only"));
        final String filePattern = StringUtils.hasText(configs.get("filePattern")) ? configs.get("filePattern") : DEFAULT_FILE_PATTERN;

        Path backupDir = backupPaths.backupDir(dbName);
        Path archiveDir = backupPaths.archiveDir(dbName);
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            log.error("[dbBackup] No se pudo crear el directorio de backups {}", backupDir, e);
            return CommonResult.error("No se pudo crear el directorio de backups: " + backupDir);
        }

        log.info("[dbBackup] Destino de backups: {}. Archivo de archivo: {}", backupDir, archiveDir == null ? "deshabilitado" : archiveDir);
        String currentDate = DateUtils.format(LocalDate.now(), FILE_DATE_FORMAT);
        Path finalFile = backupDir.resolve(filePattern.replace("$dbName", dbName).replace("$currentDate", currentDate));
        if (!FILE_NAME_PATTERN.matcher(finalFile.getFileName().toString()).matches()) {
            log.error("[dbBackup] El filePattern {} no genera nombres con fecha, la retencion no podria aplicarse", filePattern);
            return CommonResult.error("El filePattern configurado no genera nombres con fecha: " + filePattern);
        }
        Path tempFile = backupDir.resolve(finalFile.getFileName() + TEMP_SUFFIX);

        cleanUpTempFiles(backupDir);
        if (archiveDir != null) cleanUpTempFiles(archiveDir);

        Map<String, Long> countsSnapshot = queryCounts(dbName, dbUser, dbPassword);

        CommonResult dumpResult = executeDump(dbName, dbUser, dbPassword, dataOnly, tempFile);
        if (dumpResult.isError()) {
            deleteQuietly(tempFile);
            return dumpResult;
        }

        CommonResult validationResult = validate(tempFile);
        if (validationResult.isError()) {
            deleteQuietly(tempFile);
            return validationResult;
        }

        try {
            moveIntoPlace(tempFile, finalFile);
        } catch (IOException e) {
            log.error("[dbBackup] No se pudo publicar el backup {}", finalFile, e);
            deleteQuietly(tempFile);
            return CommonResult.error("No se pudo publicar el backup: " + e.getMessage());
        }

        log.info("[dbBackup] Backup generado: {} ({} bytes)", finalFile, sizeOf(finalFile));
        BackupSnapshot.write(finalFile, countsSnapshot);
        removeDuplicatedBackup(backupDir, finalFile);
        boolean isMonthly = applyRetention(backupDir, finalFile);
        archiveIfMonthly(archiveDir, finalFile, isMonthly);

        return CommonResult.ok(finalFile.toString(), "Backup generado: " + finalFile);
    }

    private CommonResult executeDump(String dbName, String dbUser, String dbPassword, boolean dataOnly, Path tempFile) {
        List<String> options = new ArrayList<>(List.of(
                "--single-transaction",
                "--quick",
                "--routines",
                "--triggers",
                "--events",
                "--no-tablespaces",
                "--set-gtid-purged=OFF",
                "--default-character-set=utf8mb4"
        ));
        if (dataOnly) options.add("--no-create-info");
        options.add(dbName);

        try {
            return MysqlClient.toResult(mysqlClient.dump(options, tempFile.toFile(), null, dbUser, dbPassword), "mysqldump");
        } catch (UncheckedIOException | IllegalStateException e) {
            log.error("[dbBackup] No se pudo ejecutar mysqldump", e);
            return CommonResult.error("No se pudo ejecutar mysqldump: " + e.getMessage());
        }
    }

    private CommonResult validate(Path file) {
        if (sizeOf(file) == 0L) {
            return CommonResult.error("El dump generado esta vacio");
        }
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            String firstLine = lines.findFirst().orElse("");
            if (!firstLine.startsWith(DUMP_HEADER_PREFIX)) {
                return CommonResult.error("El dump generado no tiene el encabezado esperado de mysqldump");
            }
        } catch (IOException e) {
            return CommonResult.error("No se pudo leer el dump generado: " + e.getMessage());
        }
        if (!containsMarkerAtEnd(file, MysqlClient.DUMP_COMPLETED_MARKER, TAIL_CHECK_BYTES)) {
            return CommonResult.error("El dump generado esta truncado: no se encontro el cierre '" + MysqlClient.DUMP_COMPLETED_MARKER + "'");
        }
        return CommonResult.ok();
    }

    private boolean containsMarkerAtEnd(Path file, String marker, int tailBytes) {
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            long size = channel.size();
            int readSize = (int) Math.min(size, tailBytes);
            ByteBuffer buffer = ByteBuffer.allocate(readSize);
            channel.position(size - readSize);
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
            }
            return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8).contains(marker);
        } catch (IOException e) {
            log.error("[dbBackup] No se pudo leer el final del dump {}", file, e);
            return false;
        }
    }

    private Map<String, Long> queryCounts(String dbName, String dbUser, String dbPassword) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : COUNTED_TABLES) {
            try {
                MysqlClient.ProcessResult processResult = mysqlClient.query(dbName, "SELECT COUNT(*) FROM `" + table + "`", dbUser, dbPassword);
                if (processResult.isSuccess() && !processResult.stdout().isBlank()) {
                    counts.put(table, Long.parseLong(processResult.stdout().trim()));
                } else {
                    log.warn("[dbBackup] No se pudo obtener el conteo de {}: {}", table, processResult.stderr());
                }
            } catch (NumberFormatException | UncheckedIOException | IllegalStateException e) {
                log.warn("[dbBackup] No se pudo obtener el conteo de {}: {}", table, e.getMessage());
            }
        }
        return counts;
    }

    private void moveIntoPlace(Path tempFile, Path finalFile) throws IOException {
        try {
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void removeDuplicatedBackup(Path backupDir, Path currentFile) {
        LocalDate currentDate = currentDateOf(currentFile);
        Optional<BackupFile> previous = listBackups(backupDir).stream()
                .filter(backup -> backup.date().isBefore(currentDate))
                .max(Comparator.comparing(BackupFile::date));
        if (previous.isEmpty()) return;

        try {
            if (hash(previous.get().path()).equals(hash(currentFile))) {
                if (deleteQuietly(previous.get().path())) {
                    deleteQuietly(BackupSnapshot.pathFor(previous.get().path()));
                    log.info("[dbBackup] Backup identico a {}, se elimino {}", currentFile, previous.get().path());
                }
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("[dbBackup] No se pudieron comparar los hashes de {} y {}", previous.get().path(), currentFile, e);
        }
    }

    private boolean applyRetention(Path backupDir, Path currentFile) {
        List<BackupFile> backups = listBackups(backupDir);
        List<BackupFile> monthly = new ArrayList<>();
        List<BackupFile> recent = new ArrayList<>();
        YearMonth currentMonth = null;
        for (BackupFile backup : backups) {
            YearMonth month = YearMonth.from(backup.date());
            if (!month.equals(currentMonth)) {
                monthly.add(backup);
                currentMonth = month;
            } else {
                recent.add(backup);
            }
        }

        Set<Path> toKeep = new HashSet<>();
        keepLast(monthly, KEEP_MONTHLY, toKeep);
        keepLast(recent, KEEP_RECENT, toKeep);

        int deleted = 0;
        for (BackupFile backup : backups) {
            if (!toKeep.contains(backup.path()) && deleteQuietly(backup.path())) {
                deleteQuietly(BackupSnapshot.pathFor(backup.path()));
                deleted++;
                log.info("[dbBackup] Backup eliminado por retencion: {}", backup.path());
            }
        }

        boolean isMonthly = monthly.stream().anyMatch(backup -> backup.path().equals(currentFile));
        log.info("[dbBackup] Retencion aplicada. Mensuales: {}, recientes: {}, eliminados: {}, total en disco: {}",
                monthly.size(), recent.size(), deleted, listBackups(backupDir).size());
        return isMonthly;
    }

    private void keepLast(List<BackupFile> backups, int keep, Set<Path> toKeep) {
        int added = 0;
        for (int i = backups.size() - 1; i >= 0 && added < keep; i--) {
            toKeep.add(backups.get(i).path());
            added++;
        }
    }

    private void archiveIfMonthly(Path archiveDir, Path file, boolean isMonthly) {
        if (archiveDir == null || !isMonthly) return;

        Path tempTarget = archiveDir.resolve(file.getFileName() + ARCHIVE_SUFFIX + TEMP_SUFFIX);
        Path target = archiveDir.resolve(file.getFileName() + ARCHIVE_SUFFIX);
        try {
            Files.createDirectories(archiveDir);
            gzip(file, tempTarget);
            moveIntoPlace(tempTarget, target);
            log.info("[dbBackup] Copia mensual archivada en {} ({} bytes)", target, sizeOf(target));
        } catch (IOException | UncheckedIOException e) {
            log.warn("[dbBackup] No se pudo archivar la copia mensual de {}: {}", file, e.getMessage());
            deleteQuietly(tempTarget);
        }
    }

    private void gzip(Path source, Path target) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(source));
             OutputStream output = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
            input.transferTo(output);
        }
    }

    private void cleanUpTempFiles(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(TEMP_SUFFIX))
                    .toList()
                    .forEach(this::deleteQuietly);
        } catch (IOException e) {
            log.warn("[dbBackup] No se pudieron limpiar los temporales de {}: {}", directory, e.getMessage());
        }
    }

    private List<BackupFile> listBackups(Path backupDir) {
        if (!Files.isDirectory(backupDir)) return List.of();
        try (Stream<Path> files = Files.list(backupDir)) {
            return files.map(this::toBackupFile)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(BackupFile::date))
                    .toList();
        } catch (IOException e) {
            log.error("[dbBackup] No se pudo listar el directorio de backups {}", backupDir, e);
            return List.of();
        }
    }

    private BackupFile toBackupFile(Path path) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) return null;
        try {
            return new BackupFile(path, LocalDate.parse(matcher.group(1), FILE_DATE_FORMAT));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDate currentDateOf(Path file) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(file.getFileName().toString());
        return matcher.matches() ? LocalDate.parse(matcher.group(1), FILE_DATE_FORMAT) : LocalDate.MIN;
    }

    private String hash(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file));
             DigestInputStream digestStream = new DigestInputStream(input, digest)) {
            digestStream.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    private boolean deleteQuietly(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException | UncheckedIOException e) {
            log.warn("[dbBackup] No se pudo eliminar {}: {}", file, e.getMessage());
            return false;
        }
    }

    private record BackupFile(Path path, LocalDate date) {
    }
}
