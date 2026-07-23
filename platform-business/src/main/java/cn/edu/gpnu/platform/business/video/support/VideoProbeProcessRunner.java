package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * 有界子进程执行器。墙钟超时后强制终止 JVM，确保单次 JCodec 阻塞不能占用主进程资源。
 */
@Component
@RequiredArgsConstructor
public class VideoProbeProcessRunner {

    private static final Duration TERMINATION_GRACE = Duration.ofSeconds(5);
    private static final String PROPERTIES_LAUNCHER =
            "org.springframework.boot.loader.launch.PropertiesLauncher";
    private static final String PROPERTIES_LAUNCHER_ENTRY =
            "org/springframework/boot/loader/launch/PropertiesLauncher.class";

    private final VideoProbeProperties properties;

    public ProcessResult execute(String mainClass, List<String> arguments, Duration timeout) {
        Path argumentFile = null;
        Process process = null;
        try {
            Path directory = properties.getTempDirectory().toAbsolutePath().normalize();
            Files.createDirectories(directory);
            argumentFile = Files.createTempFile(directory, "video-worker-", ".args");
            Files.writeString(argumentFile, argumentFileContent(mainClass), StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(javaExecutable().toString());
            command.add("@" + argumentFile);
            command.addAll(arguments);
            process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(TERMINATION_GRACE.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                return new ProcessResult(-1, true);
            }
            return new ProcessResult(process.exitValue(), false);
        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw new BizException("视频媒体探测被中断");
        } catch (IOException e) {
            throw new BizException("无法启动视频媒体探测工作进程");
        } finally {
            if (argumentFile != null) {
                try {
                    Files.deleteIfExists(argumentFile);
                } catch (IOException ignored) {
                    // 独立临时卷由生命周期清理兜底。
                }
            }
        }
    }

    private String argumentFileContent(String mainClass) {
        // Surefire 会以 booter JAR 作为 java.class.path；测试子进程需使用其展开后的完整测试类路径。
        String classPath = System.getProperty(
                "surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> arguments = new ArrayList<>();
        arguments.add("-Xms32m");
        arguments.add("-Xmx" + properties.getWorkerMaxHeapMb() + "m");
        arguments.add("-XX:+ExitOnOutOfMemoryError");
        if (isSpringBootArchive(classPath)) {
            arguments.add("-Dloader.main=" + mainClass);
            arguments.add("-cp");
            arguments.add(classPath);
            arguments.add(PROPERTIES_LAUNCHER);
        } else {
            arguments.add("-cp");
            arguments.add(classPath);
            arguments.add(mainClass);
        }
        return arguments.stream().map(this::quoteArgument).collect(java.util.stream.Collectors.joining("\n"));
    }

    private boolean isSpringBootArchive(String classPath) {
        if (classPath == null || classPath.indexOf(java.io.File.pathSeparatorChar) >= 0) {
            return false;
        }
        Path candidate = Path.of(classPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(candidate) || !candidate.getFileName().toString().endsWith(".jar")) {
            return false;
        }
        try (JarFile jar = new JarFile(candidate.toFile())) {
            return jar.getJarEntry(PROPERTIES_LAUNCHER_ENTRY) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private String quoteArgument(String value) {
        String normalized = value.replace('\\', '/').replace("\"", "\\\"");
        return "\"" + normalized + "\"";
    }

    private Path javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

    public record ProcessResult(int exitCode, boolean timedOut) {
    }
}
