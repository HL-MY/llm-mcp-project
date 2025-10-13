package org.example.agent.service;

import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.tools.ToolBase;
import com.alibaba.dashscope.tools.ToolFunction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import org.example.agent.component.ProcessManager;
import org.example.agent.dto.ConfigurationRequest;
import org.example.agent.dto.UiState;
import org.example.agent.factory.TelecomToolFactory;
import org.example.agent.model.tool.ToolCall;
import org.example.agent.model.tool.ToolDefinition;
import org.example.agent.service.impl.QianwenServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@SessionScope
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final QianwenServiceImpl qianwenService;
    private final ProcessManager processManager;
    private final WorkflowStateService workflowStateService;
    private final ModelConfigurationService modelConfigurationService;
    private final HistoryService historyService;
    private final HttpSession httpSession;
    private final ToolService toolService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<ToolDefinition> tools;

    // 构造函数包含了所有需要的服务
    public ChatService(QianwenServiceImpl qianwenService, ProcessManager processManager,
                       WorkflowStateService workflowStateService, ModelConfigurationService modelConfigurationService,
                       HistoryService historyService, HttpSession httpSession, ToolService toolService) {
        this.qianwenService = qianwenService;
        this.processManager = processManager;
        this.workflowStateService = workflowStateService;
        this.modelConfigurationService = modelConfigurationService;
        this.historyService = historyService;
        this.httpSession = httpSession;
        this.toolService = toolService;
    }

    @PostConstruct
    public void initTools() {
        this.tools = new ArrayList<>(TelecomToolFactory.getAllToolDefinitions());
        log.info("成功初始化 {} 个电信工具。", tools.size());
    }

    private String getSessionId() {
        return httpSession.getId();
    }

    /**
     * 【核心方法】处理用户消息，融合了流程控制和工具调用
     */
    public String processUserMessage(String userMessage) {
        // 1. 检查流程是否已全部完成
        if (getAvailableProcesses().isEmpty() && processManager.getUnfinishedProcesses().isEmpty()) {
            return "🎉 恭喜！所有流程均已完成！";
        }

        // 2. 构建包含当前可用任务的动态人设 (来自流程控制版本)
        String persona = buildDynamicPersona();

        // 3. 准备模型参数和工具 (来自工具调用版本)
        String modelName = modelConfigurationService.getModelName();
        var parameters = modelConfigurationService.getParametersAsMap();
        List<ToolBase> sdkTools = convertToolsForSdk(this.tools);
        String openingMonologue = workflowStateService.getOpeningMonologue();

        // 4. 调用大模型，同时传递工具定义
        GenerationResult result = qianwenService.chat(
                getSessionId(), userMessage, modelName,
                persona, // 使用动态人设
                openingMonologue, parameters, sdkTools);

        String finalContent;
        boolean isToolCall = "tool_calls".equalsIgnoreCase(result.getOutput().getChoices().get(0).getFinishReason());

        // 5. 判断是否需要调用工具 (来自工具调用版本)
        if (isToolCall) {
            finalContent = handleToolCalls(result, modelName, parameters, sdkTools);
        } else {
            finalContent = result.getOutput().getChoices().get(0).getMessage().getContent();
            qianwenService.addAssistantMessageToHistory(getSessionId(), finalContent);
        }

        // 6. 【新增】在获得最终答复后，检查是否触发了流程推进
        checkForWorkflowCompletion(finalContent);

        return finalContent;
    }

    /**
     * 新增的私有方法，专门用于处理工具调用的逻辑，使主方法更清晰
     */
    private String handleToolCalls(GenerationResult result, String modelName, Map<String, Object> parameters, List<ToolBase> sdkTools) {
        Message toolCallMessage = result.getOutput().getChoices().get(0).getMessage();
        List<ToolCall> toolCalls;
        try {
            String toolCallsJson = objectMapper.writeValueAsString(toolCallMessage.getToolCalls());
            toolCalls = objectMapper.readValue(toolCallsJson, new TypeReference<List<ToolCall>>() {});
        } catch (Exception e) {
            log.error("手动转换ToolCall对象时出错", e);
            return "抱歉，模型返回的工具调用格式不兼容，转换失败。";
        }

        if (toolCalls == null || toolCalls.isEmpty()) {
            log.error("模型返回tool_calls，但解析后的toolCalls列表为空。");
            return "抱歉，模型响应出现内部错误，无法执行工具。";
        }

        // 暂只处理第一个工具调用
        ToolCall toolCall = toolCalls.get(0);
        String toolName = toolCall.getFunction().getName();
        String toolArgsString = toolCall.getFunction().getArguments();
        log.info("LLM决定调用工具: {}, 参数: {}", toolName, toolArgsString);

        JsonNode toolArgs;
        try {
            toolArgs = objectMapper.readTree(toolArgsString);
        } catch (JsonProcessingException e) {
            log.error("解析工具参数JSON时出错: {}", toolArgsString, e);
            return "抱歉，模型返回的工具参数格式不正确。";
        }

        String toolResultContent = executeTool(toolName, toolArgs);

        Message toolResultMessage = Message.builder()
                .role("tool")
                .content(toolResultContent)
                .toolCallId(toolCall.getId())
                .build();

        GenerationResult finalResult = qianwenService.callWithToolResult(
                getSessionId(), modelName, parameters, sdkTools, toolCallMessage, toolResultMessage);

        String finalContent = finalResult.getOutput().getChoices().get(0).getMessage().getContent();
        qianwenService.addAssistantMessageToHistory(getSessionId(), finalContent);
        return finalContent;
    }

    /**
     * 【新增】用于流程控制的方法，从LLM的回复中解析流程完成指令
     */
    private void checkForWorkflowCompletion(String llmResponse) {
        List<String> availableProcesses = getAvailableProcesses();
        for (String process : availableProcesses) {
            String sanitizedProcess = sanitizeProcessName(process);
            Pattern pattern = Pattern.compile("我已完成流程\\[(?:、.*→\\s*)?" + Pattern.quote(sanitizedProcess) + "\\]");
            if (pattern.matcher(llmResponse).find()) {
                log.info("检测到工作流步骤完成: {}", process);
                processManager.completeProcess(process);
                break; // 假设每次回复最多只完成一个流程
            }
        }
    }


    // --- 以下是两个版本中所有需要的辅助方法 ---

    // 来自工具调用版本：转换工具为SDK格式
    private List<ToolBase> convertToolsForSdk(List<ToolDefinition> customTools) {
        if (customTools == null || customTools.isEmpty()) { return new ArrayList<>(); }
        List<ToolBase> sdkTools = new ArrayList<>();
        for (ToolDefinition customTool : customTools) {
            try {
                org.example.agent.model.tool.FunctionDefinition customFunction = customTool.getFunction();
                String paramsJsonString = objectMapper.writeValueAsString(customFunction.getParameters());
                JsonObject parametersAsJsonObject = JsonParser.parseString(paramsJsonString).getAsJsonObject();
                com.alibaba.dashscope.tools.FunctionDefinition sdkFunction =
                        com.alibaba.dashscope.tools.FunctionDefinition.builder()
                                .name(customFunction.getName())
                                .description(customFunction.getDescription())
                                .parameters(parametersAsJsonObject)
                                .build();
                sdkTools.add(ToolFunction.builder().function(sdkFunction).build());
            } catch (JsonProcessingException e) {
                log.error("将自定义工具 '{}' 转换为SDK格式时失败", customTool.getFunction().getName(), e);
                throw new RuntimeException("工具定义转换失败，无法继续执行。", e);
            }
        }
        return sdkTools;
    }

    // 来自工具调用版本：执行具体工具
    private String executeTool(String toolName, JsonNode args) {
        switch (toolName) {
            case "queryAllPlans": return toolService.queryAllPlans();
            case "compareTwoPlans":
                String plan1 = args.get("planName1").asText();
                String plan2 = args.get("planName2").asText();
                return toolService.compareTwoPlans(plan1, plan2);
            default:
                log.warn("尝试调用一个未知的工具: {}", toolName);
                return "{\"error\": \"未知工具\"}";
        }
    }

    // 来自流程控制版本：构建动态人设
    private String buildDynamicPersona() {
        String personaTemplate = workflowStateService.getPersonaTemplate();
        List<String> availableProcesses = getAvailableProcesses();
        String availableTasksStr = availableProcesses.isEmpty() ? "无" : sanitizeProcessNames(availableProcesses).stream().collect(Collectors.joining("、"));
        List<String> allProcesses = workflowStateService.getCurrentProcesses();
        String workflowStr = sanitizeProcessNames(allProcesses).stream().collect(Collectors.joining(" → "));
        return personaTemplate
                .replace("{tasks}", availableTasksStr)
                .replace("{workflow}", workflowStr);
    }

    // 来自流程控制版本：获取当前可执行的流程
    private List<String> getAvailableProcesses() {
        List<String> unfinished = processManager.getUnfinishedProcesses();
        Map<String, List<String>> rules = workflowStateService.getDependencyRules();
        List<String> allProcesses = processManager.getAllProcesses();
        List<String> completed = new ArrayList<>(allProcesses);
        completed.removeAll(unfinished);
        List<String> availableFromPending = unfinished.stream().filter(task -> {
            List<String> prerequisites = rules.get(task);
            return (prerequisites == null || prerequisites.isEmpty() || completed.containsAll(prerequisites));
        }).collect(Collectors.toList());
        List<String> repeatableAndCompleted = completed.stream()
                .filter(task -> task.trim().endsWith("*"))
                .collect(Collectors.toList());
        return Stream.concat(availableFromPending.stream(), repeatableAndCompleted.stream())
                .distinct()
                .sorted((p1, p2) -> Integer.compare(allProcesses.indexOf(p1), allProcesses.indexOf(p2)))
                .collect(Collectors.toList());
    }

    // 来自流程控制版本：清理流程名
    private String sanitizeProcessName(String processName) {
        String name = processName.trim();
        if (name.endsWith("*")) { name = name.substring(0, name.length() - 1); }
        return name.replaceAll("^\\d+\\.?\\s*", "").trim();
    }

    private List<String> sanitizeProcessNames(List<String> processNames) {
        return processNames.stream().map(this::sanitizeProcessName).collect(Collectors.toList());
    }

    // --- UI状态和会话管理方法 ---
    public UiState getCurrentUiState() {
        var statuses = processManager.getAllProcesses().stream()
                .collect(Collectors.toMap(p -> p, p -> processManager.getUnfinishedProcesses().contains(p) ? "PENDING" : "COMPLETED", (v1, v2) -> v1, LinkedHashMap::new));
        String persona = buildDynamicPersona(); // 使用动态人设
        String rawTemplate = workflowStateService.getPersonaTemplate();
        String openingMonologue = workflowStateService.getOpeningMonologue();
        return new UiState(statuses, persona, rawTemplate, openingMonologue,
                modelConfigurationService.getModelName(),
                modelConfigurationService.getTemperature(),
                modelConfigurationService.getTopP());
    }

    public void resetProcessesAndSaveHistory() {
        List<Message> history = qianwenService.popConversationHistory(getSessionId());
        historyService.saveConversationToFile("", history);
        processManager.reset();
    }

    public void saveHistoryOnExit() {
        List<Message> history = qianwenService.getConversationHistory(getSessionId());
        historyService.saveConversationToFile("", history);
    }

    public void updateWorkflow(ConfigurationRequest config) {
        List<Message> history = qianwenService.popConversationHistory(getSessionId());
        historyService.saveConversationToFile("", history);
        workflowStateService.updateWorkflow(config.getProcesses(), config.getPersonaTemplate(), config.getDependencies(), config.getOpeningMonologue());
        processManager.updateProcesses(config.getProcesses());
        modelConfigurationService.updateModelName(config.getModelName());
        modelConfigurationService.updateTemperature(config.getTemperature());
        modelConfigurationService.updateTopP(config.getTopP());
        // 清空历史记录
    }
}