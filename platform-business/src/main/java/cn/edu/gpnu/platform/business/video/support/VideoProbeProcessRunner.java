package cn.edu.gpnu.platform.business.video.support;

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
    private final VideoProbeTempArtifactManager artifactManager;
    private final VideoProbeProcessFactory processFactory;
    private final VideoProbeProcessSupervisor processSupervisor;

    public ProcessResult execute(String mainClass, List<String> arguments, Duration timeout) {
        Process process = null;
        try (VideoProbeTempArtifactManager.Artifact artifact =
                     artifactManager.create(VideoProbeTempArtifactManager.ArtifactKind.ARGS, ".args")) {
            Path argumentFile = artifact.path();
            java.nio.file.Files.writeString(
                    argumentFile, argumentFileContent(mainClass), StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(javaExecutable().toString());
            command.add("@" + argumentFile);
            command.addAll(arguments);
            process = processFactory.start(command);
            boolean finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                if (!terminateAndConfirm(process)) {
                    artifact.preserveForReaper();
                    processSupervisor.registerOrphan(process);
                    throw new VideoProbeInfrastructureException(
                            "视频媒体探测工作进程无法终止，请稍后重试");
                }
                return new ProcessResult(-1, true, process.pid());
            }
            return new ProcessResult(process.exitValue(), false, process.pid());
        } catch (InterruptedException e) {
            if (process != null) {
                if (!terminateAndConfirmAfterInterrupt(process)) {
                    processSupervisor.registerOrphan(process);
                }
            }
            Thread.currentThread().interrupt();
            throw new VideoProbeInfrastructureException("视频媒体探测被中断，请稍后重试", e);
        } catch (IOException e) {
            throw new VideoProbeInfrastructureException("无法启动视频媒体探测工作进程，请稍后重试", e);
        }
    }

    private boolean terminateAndConfirm(Process process) throws InterruptedException {
        process.destroyForcibly();
        boolean exited = process.waitFor(
                TERMINATION_GRACE.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        return exited && !process.isAlive();
    }

    private boolean terminateAndConfirmAfterInterrupt(Process process) {
        process.destroyForcibly();
        try {
            boolean exited = process.waitFor(
                    TERMINATION_GRACE.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            return exited && !process.isAlive();
        } catch (InterruptedException secondInterrupt) {
            return !process.isAlive();
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

    public record ProcessResult(int exitCode, boolean timedOut, long processId) {
    }
}
