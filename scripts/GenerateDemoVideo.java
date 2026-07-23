import org.jcodec.api.FrameGrab;
import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

import java.io.File;

/**
 * 生成带真实 H.264 视频帧的 MP4 样例。
 *
 * 用法：
 *   javac -cp jcodec-0.2.5.jar scripts/GenerateDemoVideo.java
 *   java -cp "scripts;jcodec-0.2.5.jar" GenerateDemoVideo <output.mp4> <durationSeconds>
 *
 * 仅依赖项目已锁定的 JCodec 核心包；默认 1fps，900 秒样例只编码 900 帧，
 * 兼顾真实时长与仓库体积。Linux/macOS 请把 classpath 分隔符改为冒号。
 */
public final class GenerateDemoVideo {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;

    private GenerateDemoVideo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: GenerateDemoVideo <output.mp4> <durationSeconds>");
        }
        File output = new File(args[0]);
        int durationSeconds = Integer.parseInt(args[1]);
        if (durationSeconds < 1) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("cannot create output directory");
        }

        SequenceEncoder encoder = SequenceEncoder.createSequenceEncoder(output, 1);
        for (int second = 0; second < durationSeconds; second++) {
            encoder.encodeNativeFrame(frame(second, durationSeconds));
        }
        encoder.finish();
        Picture decoded = FrameGrab.getFrameFromFile(output, 0);
        if (decoded == null || decoded.getWidth() < WIDTH || decoded.getHeight() < HEIGHT) {
            throw new IllegalStateException("generated video cannot decode its first frame: "
                    + (decoded == null ? "null" : decoded.getWidth() + "x" + decoded.getHeight()));
        }
        System.out.printf("wrote %s (%d seconds, %d bytes)%n",
                output.getPath(), durationSeconds, output.length());
    }

    private static Picture frame(int second, int totalSeconds) {
        Picture picture = Picture.create(WIDTH, HEIGHT, ColorSpace.RGB);
        byte[] rgb = picture.getPlaneData(0);
        int progressWidth = Math.max(1, (int) ((second + 1L) * WIDTH / totalSeconds));
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                boolean progress = y >= 145 && y < 153 && x < progressWidth;
                boolean pulse = x >= 40 && x < 280 && y >= 55 && y < 115
                        && ((x / 12 + y / 12 + second) & 1) == 0;
                int red = progress ? 56 : (pulse ? 230 : 24);
                int green = progress ? 189 : (pulse ? 240 : 72);
                int blue = progress ? 248 : (pulse ? 255 : 40 + second % 80);
                int offset = (y * WIDTH + x) * 3;
                // JCodec 的 8-bit Picture 使用以 -128 为零点的有符号字节。
                rgb[offset] = (byte) (red - 128);
                rgb[offset + 1] = (byte) (green - 128);
                rgb[offset + 2] = (byte) (blue - 128);
            }
        }
        return picture;
    }
}
