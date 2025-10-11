package org.example.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import java.util.*;

@Service
@SessionScope
public class WorkflowStateService {

    private List<String> currentProcesses = new ArrayList<>(Arrays.asList("1. 对话开始", "2. 任务执行", "3. 对话结束"));
    private String personaTemplate = "你是一个专业、热情的电信业务助手。请使用你被赋予的工具来准确地回答用户的问题。";
    private Map<String, List<String>> dependencyRules = new HashMap<>();
    private String openingMonologue = "您好！我是您的电信业务助手，有什么可以帮您？我可以为您查询和比较套餐。";

    public List<String> getCurrentProcesses() {
        return currentProcesses;
    }

    public String getPersonaTemplate() {
        return personaTemplate;
    }

    public Map<String, List<String>> getDependencyRules() {
        return dependencyRules;
    }

    public String getOpeningMonologue() {
        return openingMonologue;
    }

    public void updateWorkflow(List<String> newProcesses, String newPersonaTemplate, String dependencies, String openingMonologue) {
        if (newProcesses != null) {
            this.currentProcesses = new ArrayList<>(newProcesses);
        }
        if (newPersonaTemplate != null && !newPersonaTemplate.trim().isEmpty()) {
            this.personaTemplate = newPersonaTemplate;
        }
        if (openingMonologue != null) {
            this.openingMonologue = openingMonologue;
        }
        if (dependencies != null) { /* 解析依赖的逻辑可以保留或简化 */ }
    }
}