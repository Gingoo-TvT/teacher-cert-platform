package cn.edu.gpnu.platform.business.video.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产打包路径门禁：package/repackage 后直接以 PropertiesLauncher 执行 fat-JAR 中的媒体 worker。
 */
class FatJarVideoProbeWorkerIT {

    private static final String PROPERTIES_LAUNCHER =
            "org.springframework.boot.loader.launch.PropertiesLauncher";

    @TempDir
    Path tempDirectory;

    @Test
    void packagedWorkerRunsThroughPropertiesLauncher() throws Exception {
        Path fatJar = Path.of(System.getProperty("video.probe.fat-jar"))
                .toAbsolutePath().normalize();
        Path media = Path.of(getClass().getClassLoader()
                .getResource("media/short-video.mp4").toURI());
        Path result = tempDirectory.resolve("worker-result.properties");
        Path stdout = tempDirectory.resolve("worker.stdout.log");
        Path stderr = tempDirectory.resolve("worker.stderr.log");

        assertThat(fatJar).isRegularFile();
        List<String> command = List.of(
                javaExecutable().toString(),
                "-Dloader.main=" + VideoProbeWorkerMain.class.getName(),
                "-cp", fatJar.toString(),
                PROPERTIES_LAUNCHER,
                media.toAbsolutePath().normalize().toString(),
                result.toString(),
                "H264",
                "2",
                "100000");
        Process process = new ProcessBuilder(command)
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            boolean terminated = process.waitFor(5, TimeUnit.SECONDS);
            assertThat(terminated && !process.isAlive())
                    .as("fat-JAR worker 超时且无法确认终止，stderr=%s", readQuietly(stderr))
                    .isTrue();
        }
        assertThat(finished)
                .as("fat-JAR worker 应在硬时限内自然退出，stderr=%s", readQuietly(stderr))
                .isTrue();
        assertThat(process.exitValue())
                .as("fat-JAR worker stderr=%s", readQuietly(stderr))
                .isZero();
        assertThat(result).isNotEmptyFile();

        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(result, StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        assertThat(values.getProperty("valid")).isEqualTo("true");
        assertThat(values.getProperty("codec")).isEqualTo("H264");
        assertThat(values.getProperty("durationSeconds")).isEqualTo("3");
        assertThat(values.getProperty("frameCount")).isEqualTo("3");
    }

    private Path javaExecutable() {
        String name = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", name);
    }

    private String readQuietly(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file) : "";
        } catch (Exception ignored) {
            return "";
        }
    }
}
