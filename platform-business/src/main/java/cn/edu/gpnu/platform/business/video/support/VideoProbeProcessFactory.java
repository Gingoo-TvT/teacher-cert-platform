package cn.edu.gpnu.platform.business.video.support;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface VideoProbeProcessFactory {

    Process start(List<String> command) throws IOException;
}
