package org.example.agent.component;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;
import java.util.ArrayList; // <-- 【新增】
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 【V3.0 重构版】
 * 1. 移除了对 WorkflowStateService 的所有依赖。
 * 2. 这是一个纯粹的会话状态管理器，由 ChatService 在启动时注入流程。
 */
@SessionScope
@Component
public class ProcessManager {

    public enum Status {
        PENDING, COMPLETED
    }

    private final Map<String, Status> processStatus = new ConcurrentHashMap<>();

    // 【修改】删除 WorkflowStateService
    // private final WorkflowStateService workflowStateService;

    // 【修改】构造函数变为空
    public ProcessManager() {
    }

    // 【修改】删除 @PostConstruct init()
    // 它现在由 ChatService 在构造时调用 updateProcesses
    // @PostConstruct
    // public void init() { ... }

    /**
     * 由 ChatService 在会话开始时调用，用于注入从 ConfigService 获取的流程
     */
    public void updateProcesses(List<String> newProcesses) {
        processStatus.clear();
        if (newProcesses != null) {
            newProcesses.forEach(p -> processStatus.put(p, Status.PENDING));
        }
    }

    public void completeProcess(String processName) {
        if (processStatus.containsKey(processName)) {
            processStatus.put(processName, Status.COMPLETED);
        }
    }

    public List<String> getUnfinishedProcesses() {
        return processStatus.entrySet().stream()
                .filter(entry -> entry.getValue() == Status.PENDING)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 【修改】
     * 此方法现在只返回当前会话中已知的流程
     */
    public List<String> getAllProcesses() {
        return new ArrayList<>(processStatus.keySet());
    }

    /**
     * 【修改】
     * Reset 也不再需要 workflowStateService
     * 真正的重置逻辑在 ChatService::resetProcessesAndSaveHistory 中
     * (它会调用 updateProcesses)
     */
    public void reset() {
        processStatus.clear();
    }
}