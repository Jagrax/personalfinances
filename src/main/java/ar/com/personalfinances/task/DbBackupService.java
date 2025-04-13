package ar.com.personalfinances.task;

import ar.com.personalfinances.entity.InstanceTask;
import ar.com.personalfinances.util.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service("DbBackupService")
public class DbBackupService extends BaseInstanceTaskService {

    private final static String BACKUP_DIRECTORY = Paths.get(new File("").getAbsolutePath(), "db", "backups").toString();

    @Override
    public CommonResult runTask(InstanceTask instanceTask) {
        // Obtener las configuraciones de la tarea
        Map<String, String> configs = instanceTask.getConfisAsMap();
        final String dbName = configs.get("dbName");
        final String dbUser = configs.get("dbUser");
        final String dbPassword = configs.get("dbPassword");
        final boolean dataOnly = Boolean.parseBoolean(configs.get("data-only"));
        final String filePattern = configs.get("filePattern");
        final String backupFilePattern = configs.get("backupFilePattern");

        // Obtener la fecha actual en formato yyyyMMdd
        final String currentDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
        final String fileName = filePattern.replace("$dbName", dbName).replace("$currentDate", currentDate);
        final String filePath = Paths.get(BACKUP_DIRECTORY, fileName).toString();

        // Asegurarse de que el directorio de salida exista
        File outputDirFile = new File(BACKUP_DIRECTORY);
        if (!outputDirFile.exists()) {
            if (outputDirFile.mkdirs()) {
                log.info("Output directory {} created", BACKUP_DIRECTORY);
            } else {
                log.error("Output directory {} could not be created", BACKUP_DIRECTORY);
                return CommonResult.error("Se produjo un error al intentar crear la carpeta donde se guardara el backup");
            }
        }

        final File currentBackupFile = new File(filePath);

        final List<String> commandParts = new ArrayList<>();
        commandParts.add("C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysqldump");
        commandParts.add("-u");
        commandParts.add(dbUser);
        commandParts.add("-p" + dbPassword);
        if (dataOnly) commandParts.add("--no-create-info");
        commandParts.add(dbName);

        final String command = String.join(" ", commandParts);

        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
        log.info("Por settear archivo de salida: {}", filePath);
        processBuilder.redirectOutput(currentBackupFile);

        try {
            log.info("Por ejecutar el comando: {}", command);
            // Ejecutar el backup
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("Backup exitoso: {}", filePath);
                handlePreviousBackup(dbName, currentBackupFile, backupFilePattern.replace("$dbName", dbName));
                return CommonResult.ok();
            } else {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        log.error("mysqldump error: {}", line);
                    }
                }
                try (BufferedReader stdOutput = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = stdOutput.readLine()) != null) {
                        log.info("mysqldump output: {}", line);
                    }
                }

                log.error("Error al ejecutar el backup, código de salida: {}", exitCode);
                return CommonResult.error("La ejecucion el comando [" + command + "] dio como resultado: " + exitCode);
            }
        } catch (Exception e) {
            log.error("Error durante la ejecución del backup", e);
            return CommonResult.error("Se produjo un error al ejecutar el comando: " + command);
        }
    }

    // Verificar y manejar el backup anterior
    private void handlePreviousBackup(String dbName, File currentBackupFile, String filePattern) {
        final File backupDir = new File(BACKUP_DIRECTORY);
        final File[] oldBackups = backupDir.listFiles((dir, name) ->
                name.matches(filePattern) && !name.equals(currentBackupFile.getName())
        );

        if (oldBackups != null) {
            for (File oldBackup : oldBackups) {
                try {
                    if (filesAreEqual(oldBackup, currentBackupFile)) {
                        if (oldBackup.delete()) {
                            log.info("Backup anterior eliminado: {}", oldBackup.getAbsolutePath());
                        } else {
                            log.warn("No se pudo eliminar el backup anterior: {}", oldBackup.getAbsolutePath());
                        }
                    }
                } catch (Exception e) {
                    log.error("Error al comparar los backups: {} | {}", oldBackup.getAbsolutePath(), currentBackupFile.getAbsolutePath(), e);
                }
            }
        }
    }

    // Método para comparar dos archivos basándonos en sus hashes (SHA-256)
    private boolean filesAreEqual(File file1, File file2) throws Exception {
        String hash1 = getFileHash(file1);
        String hash2 = getFileHash(file2);
        return hash1.equals(hash2);
    }

    // Obtener el hash SHA-256 de un archivo
    private String getFileHash(File file) throws Exception {
        // Inicializar MessageDigest con SHA-256
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] byteArray = new byte[1024]; // Buffer para leer el archivo
            int bytesRead;

            while ((bytesRead = bis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesRead); // Actualizar el digest con los bytes leídos
            }
        }

        // Obtener el hash en formato hexadecimal
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            hexString.append(String.format("%02x", b)); // Convertir a hexadecimal
        }

        return hexString.toString();
    }
}