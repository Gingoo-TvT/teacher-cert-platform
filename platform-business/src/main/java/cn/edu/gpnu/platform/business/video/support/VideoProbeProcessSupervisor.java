package cn.edu.gpnu.platform.business.video.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 记录未能在强杀宽限期内确认退出的 worker；存在活孤儿时阻止新探测继续超配。
 */
@Component
@Slf4j
public class VideoProbeProcessSupervisor {

    private final ConcurrentMap<Long, Process> orphanProcesses = new ConcurrentHashMap<>();

    public void registerOrphan(Process process) {
        long pid = process.pid();
        orphanProcesses.put(pid, process);
        log.error("视频探测工作进程未能确认退出，已登记孤儿并暂停新探测: pid={}", pid);
    }

    public boolean hasLiveOrphans() {
        orphanProcesses.entrySet().removeIf(entry -> !entry.getValue().isAlive());
        return !orphanProcesses.isEmpty();
    }
}
