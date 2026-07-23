package cn.edu.gpnu.platform.business.video.support;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class DefaultVideoProbeProcessFactory implements VideoProbeProcessFactory {

    @Override
    public Process start(List<String> command) throws IOException {
        return new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }
}
