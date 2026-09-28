package ar.com.personalfinances.task;

import ar.com.personalfinances.configuration.ApplicationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
public class BackupPaths {

    private final static String DEFAULT_BACKUP_FOLDER = "pfin-backups";
    private final static String DEFAULT_ARCHIVE_FOLDER = "pfin-backups-archive";

    private final ApplicationProperties applicationProperties;

    public BackupPaths(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    public Path backupDir(String dbName) {
        return Paths.get(resolveBaseDir(applicationProperties.getBackupDir(), DEFAULT_BACKUP_FOLDER), dbName).toAbsolutePath().normalize();
    }

    public Path archiveDir(String dbName) {
        if (!StringUtils.hasText(applicationProperties.getBackupArchiveDir())) return null;
        return Paths.get(resolveBaseDir(applicationProperties.getBackupArchiveDir(), DEFAULT_ARCHIVE_FOLDER), dbName).toAbsolutePath().normalize();
    }

    private String resolveBaseDir(String configuredDir, String fallbackFolder) {
        if (StringUtils.hasText(configuredDir)) {
            Path configured = Paths.get(configuredDir);
            if (configured.isAbsolute()) return configuredDir;
            log.warn("[backupPaths] El directorio configurado {} no es absoluto, se usa el home del usuario", configuredDir);
        }
        String fallback = Paths.get(System.getProperty("user.home"), fallbackFolder).toString();
        log.warn("[backupPaths] No hay directorio configurado (app.backup.dir / app.backup.archive-dir), se usa el fallback {}", fallback);
        return fallback;
    }
}
