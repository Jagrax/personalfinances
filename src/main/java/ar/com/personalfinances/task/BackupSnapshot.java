package ar.com.personalfinances.task;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public final class BackupSnapshot {

    public static final String SUFFIX = ".counts";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String TABLES_HEADER = "#tables=";

    private BackupSnapshot() {
    }

    public static Path pathFor(Path dumpFile) {
        return dumpFile.resolveSibling(dumpFile.getFileName() + SUFFIX);
    }

    public static void write(Path dumpFile, Map<String, Long> counts) {
        if (counts.isEmpty()) {
            log.warn("[backupSnapshot] No se genero snapshot de conteos para {}", dumpFile);
            return;
        }

        Path target = pathFor(dumpFile);
        Path temp = dumpFile.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        StringBuilder content = new StringBuilder(TABLES_HEADER).append(counts.size()).append(System.lineSeparator());
        counts.forEach((table, count) -> content.append(table).append('=').append(count).append(System.lineSeparator()));

        try {
            Files.writeString(temp, content.toString(), StandardCharsets.UTF_8);
            moveIntoPlace(temp, target);
            log.info("[backupSnapshot] Snapshot de conteos generado: {}", target);
        } catch (IOException | RuntimeException e) {
            log.warn("[backupSnapshot] No se pudo guardar el snapshot {}: {}", target, e.getMessage());
            deleteQuietly(temp);
        }
    }

    public static Result read(Path dumpFile) {
        Path snapshotFile = pathFor(dumpFile);
        if (!Files.isRegularFile(snapshotFile)) {
            return new Result(Map.of(), "el backup no tiene snapshot de conteos");
        }

        int declaredTables = -1;
        Map<String, Long> counts = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(snapshotFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.startsWith(TABLES_HEADER)) {
                    declaredTables = Integer.parseInt(line.substring(TABLES_HEADER.length()).trim());
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator <= 0) continue;
                counts.put(line.substring(0, separator), Long.parseLong(line.substring(separator + 1).trim()));
            }
        } catch (IOException | NumberFormatException e) {
            log.error("[backupSnapshot] No se pudo leer el snapshot {}", snapshotFile, e);
            return new Result(Map.of(), "el snapshot de conteos esta corrupto: " + e.getMessage());
        }

        if (counts.isEmpty()) {
            return new Result(Map.of(), "el snapshot de conteos esta vacio");
        }
        if (declaredTables >= 0 && declaredTables != counts.size()) {
            return new Result(Map.of(), "el snapshot de conteos esta incompleto (decia " + declaredTables + " tablas y tiene " + counts.size() + ")");
        }
        return new Result(counts, null);
    }

    private static void moveIntoPlace(Path tempFile, Path finalFile) throws IOException {
        try {
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("[backupSnapshot] No se pudo eliminar {}: {}", file, e.getMessage());
        }
    }

    public record Result(Map<String, Long> counts, String problem) {
        public boolean isUsable() {
            return problem == null;
        }
    }
}
