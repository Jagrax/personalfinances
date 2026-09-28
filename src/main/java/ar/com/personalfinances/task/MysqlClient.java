package ar.com.personalfinances.task;

import ar.com.personalfinances.configuration.ApplicationProperties;
import ar.com.personalfinances.util.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MysqlClient {

    public static final String DUMP_COMPLETED_MARKER = "Dump completed on";

    private static final String DEFAULTS_FILE_OPTION = "--defaults-extra-file=";
    private static final int PROCESS_TIMEOUT_MINUTES = 60;

    private final ApplicationProperties applicationProperties;

    public MysqlClient(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    public ProcessResult dump(List<String> options, File output, File errorOutput, String user, String password) {
        return run(buildCommand("mysqldump", user, password, options), output, errorOutput, null);
    }

    public ProcessResult execute(List<String> options, File input, File errorOutput, String user, String password) {
        return run(buildCommand("mysql", user, password, options), null, errorOutput, input);
    }

    public ProcessResult query(String dbName, String sql, String user, String password) {
        return execute(List.of("--database=" + dbName, "--batch", "--skip-column-names", "--execute=" + sql), null, null, user, password);
    }

    private List<String> buildCommand(String executable, String user, String password, List<String> options) {
        List<String> command = new ArrayList<>();
        command.add(resolveExecutable(executable));
        command.add(DEFAULTS_FILE_OPTION + createDefaultsFile(user, password));
        command.addAll(options);
        return command;
    }

    private String resolveExecutable(String executable) {
        String name = isWindows() ? executable + ".exe" : executable;
        if (!StringUtils.hasText(applicationProperties.getMysqlBinPath())) return name;
        return Paths.get(applicationProperties.getMysqlBinPath(), name).toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private Path createDefaultsFile(String user, String password) {
        try {
            Path defaultsFile = Files.createTempFile("pfin-mysql-", ".cnf");
            StringBuilder content = new StringBuilder("[client]").append(System.lineSeparator());
            if (user != null && !user.isBlank()) content.append("user=").append(user).append(System.lineSeparator());
            if (password != null && !password.isBlank()) content.append("password=").append(password).append(System.lineSeparator());
            Files.writeString(defaultsFile, content.toString(), StandardCharsets.UTF_8);
            restrictPermissions(defaultsFile);
            return defaultsFile;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el archivo de credenciales temporal", e);
        }
    }

    private void restrictPermissions(Path defaultsFile) {
        if (isWindows()) return;
        try {
            Files.setPosixFilePermissions(defaultsFile, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (IOException | UnsupportedOperationException e) {
            log.warn("[mysqlClient] No se pudieron restringir los permisos de {}: {}", defaultsFile, e.getMessage());
        }
    }

    private ProcessResult run(List<String> command, File output, File errorOutput, File input) {
        Path defaultsFile = null;
        Path outputFile = null;
        Path errorFile = null;
        boolean outputProvided = output != null;
        try {
            for (String argument : command) {
                if (argument.startsWith(DEFAULTS_FILE_OPTION)) {
                    defaultsFile = Paths.get(argument.substring(DEFAULTS_FILE_OPTION.length()));
                    break;
                }
            }
            if (output == null) {
                outputFile = Files.createTempFile("pfin-mysql-out-", ".log");
                output = outputFile.toFile();
            }
            if (errorOutput == null) {
                errorFile = Files.createTempFile("pfin-mysql-err-", ".log");
                errorOutput = errorFile.toFile();
            }

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectOutput(output);
            processBuilder.redirectError(errorOutput);
            if (input != null) processBuilder.redirectInput(input);

            log.info("[mysqlClient] {} {}", command.get(0), String.join(" ", command.subList(2, command.size())));

            Process process = processBuilder.start();
            if (!process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.MINUTES);
                return new ProcessResult(-1, "", "El proceso supero los " + PROCESS_TIMEOUT_MINUTES + " minutos de tiempo maximo y fue terminado");
            }

            String stdout = outputProvided ? "" : readFile(output).trim();
            return new ProcessResult(process.exitValue(), stdout, readFile(errorOutput).trim());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProcessResult(-1, "", "La ejecucion fue interrumpida");
        } catch (IOException e) {
            log.error("[mysqlClient] Error ejecutando {}. Verificar app.mysql.bin.path o que este en el PATH", command.get(0), e);
            return new ProcessResult(-1, "", "No se pudo ejecutar " + command.get(0) + ": " + e.getMessage());
        } finally {
            deleteQuietly(defaultsFile);
            deleteQuietly(outputFile);
            deleteQuietly(errorFile);
        }
    }

    private static String readFile(File file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) content.append(line).append(System.lineSeparator());
            return content.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("[mysqlClient] No se pudo eliminar el archivo temporal {}", path, e);
        }
    }

    public static CommonResult toResult(ProcessResult processResult, String context) {
        if (processResult.isSuccess()) return CommonResult.ok();
        String detail = processResult.stderr().isBlank() ? processResult.stdout() : processResult.stderr();
        return CommonResult.error(context + " fallo con codigo " + processResult.exitCode() + (detail.isBlank() ? "" : ": " + detail));
    }

    public record ProcessResult(int exitCode, String stdout, String stderr) {

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
