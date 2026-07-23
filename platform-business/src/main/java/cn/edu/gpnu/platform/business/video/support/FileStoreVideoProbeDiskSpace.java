package cn.edu.gpnu.platform.business.video.support;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FileStoreVideoProbeDiskSpace implements VideoProbeDiskSpace {

    @Override
    public long usableSpace(Path directory) throws IOException {
        return Files.getFileStore(directory).getUsableSpace();
    }
}
