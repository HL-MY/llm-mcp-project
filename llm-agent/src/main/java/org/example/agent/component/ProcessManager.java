package org.example.agent.component;

import org.example.agent.service.WorkflowStateService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SessionScope
@Component
public class ProcessManager {

    public enum Status {
        PENDING, COMPLETED
    }

    private final Map<String, Status> processStatus = new ConcurrentHashMap<>();
    private final WorkflowStateService workflowStateService;

    public ProcessManager(WorkflowStateService workflowStateService) {
        this.workflowStateService = workflowStateService;
    }

    @PostConstruct
    public void init() {
        updateProcesses(workflowStateService.getCurrentProcesses());
    }

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

    public List<String> getAllProcesses() {
        return workflowStateService.getCurrentProcesses();
    }

    public void reset() {
        updateProcesses(workflowStateService.getCurrentProcesses());
    }
}