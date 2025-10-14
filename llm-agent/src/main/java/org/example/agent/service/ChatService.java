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
import org.example.agent.dto.ToolCallInfo;
import org.example.agent.dto.UiState;
import org.example.agent.factory.TelecomToolFactory;
import org.example.agent.model.tool.ToolCall;
import org.example.agent.model.tool.ToolDefinition;
import org.example.agent.service.impl.QianwenServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Arrays;

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
    private int silentCount = 0;

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
    }

    private String getSessionId() {
        return httpSession.getId();
    }

    public ChatCompletion processUserMessage(String userMessage) throws IOException {
        long startTime = System.currentTimeMillis();

        if (" ".equals(userMessage)) {
            silentCount++;
            log.info("检测到用户无声，当前连续无声次数: {}", silentCount);

            if (silentCount >= 4) {
                log.warn("用户连续无声达到 {} 次，强制结束对话。", silentCount);
                forceCompleteAllProcesses();
                silentCount = 0;
                return new ChatCompletion("好的，先不打扰您了，礼貌起见请您先挂机，祝您生活愉快，再见！", null);
            } else {
                List<String> cannedResponses = Arrays.asList(
                        "喂，您好，能听到说话么？",
                        "我这边是中国移动流量卡渠道商的，能听到说话么？",
                        "喂？您好，这边听不到您的声音，是信号不好吗？"
                );
                return new ChatCompletion(cannedResponses.get(silentCount - 1), null);
            }
        } else {
            if (silentCount > 0) {
                log.info("用户有正常回应，无声计数器重置。");
                silentCount = 0;
            }
        }

        if (getAvailableProcesses().isEmpty() && processManager.getUnfinishedProcesses().isEmpty()) {
            return new ChatCompletion("🎉 恭喜！所有流程均已完成！", null);
        }

        String persona = buildDynamicPersona();
        String modelName = modelConfigurationService.getModelName();
        var parameters = modelConfigurationService.getParametersAsMap();
        List<ToolBase> sdkTools = convertToolsForSdk(this.tools);
        String openingMonologue = workflowStateService.getOpeningMonologue();

        GenerationResult result = qianwenService.chat(
                getSessionId(), userMessage, modelName,
                persona, openingMonologue, parameters, sdkTools);

        String finalContent;
        ToolCallInfo toolCallInfo = null;
        Message message = result.getOutput().getChoices().get(0).getMessage();
        boolean isToolCall = message.getToolCalls() != null && !message.getToolCalls().isEmpty();

        if (isToolCall) {
            ChatCompletion toolCallCompletion = handleToolCalls(result, modelName, parameters, sdkTools);
            finalContent = toolCallCompletion.reply();
            toolCallInfo = toolCallCompletion.toolCallInfo();
        } else {
            finalContent = message.getContent();
        }

        checkForWorkflowCompletion(finalContent);

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;
        log.info("ChatService 总处理耗时: {} ms", responseTime);

        String finalReply = finalContent + "\n\n(LLM 响应耗时: " + responseTime + " 毫秒)";
        return new ChatCompletion(finalReply, toolCallInfo);
    }

    private void forceCompleteAllProcesses() {
        List<String> allProcesses = workflowStateService.getCurrentProcesses();
        for (String process : allProcesses) {
            log.info("强制完成流程: {}", process);
            processManager.completeProcess(process);
        }
    }

    private ChatCompletion handleToolCalls(GenerationResult result, String modelName, Map<String, Object> parameters, List<ToolBase> sdkTools) {
        Message toolCallMessage = result.getOutput().getChoices().get(0).getMessage();
        List<ToolCall> toolCalls;
        try {
            String toolCallsJson = objectMapper.writeValueAsString(toolCallMessage.getToolCalls());
            toolCalls = objectMapper.readValue(toolCallsJson, new TypeReference<List<ToolCall>>() {});
        } catch (Exception e) {
            log.error("手动转换ToolCall对象时出错", e);
            return new ChatCompletion("抱歉，模型返回的工具调用格式不兼容，转换失败。", null);
        }

        if (toolCalls == null || toolCalls.isEmpty()) {
            log.error("模型返回tool_calls，但解析后的toolCalls列表为空。");
            return new ChatCompletion("抱歉，模型响应出现内部错误，无法执行工具。", null);
        }

        ToolCall toolCall = toolCalls.get(0);
        String toolName = toolCall.getFunction().getName();
        String toolArgsString = toolCall.getFunction().getArguments();
        log.info("LLM决定调用工具: {}, 参数: {}", toolName, toolArgsString);

        JsonNode toolArgs;
        try {
            toolArgs = objectMapper.readTree(toolArgsString);
        } catch (JsonProcessingException e) {
            log.error("解析工具参数JSON时出错: {}", toolArgsString, e);
            return new ChatCompletion("抱歉，模型返回的工具参数格式不正确。", null);
        }

        String toolResultContent = executeTool(toolName, toolArgs);

        ToolCallInfo toolCallInfo = new ToolCallInfo(toolName, toolArgsString, toolResultContent);

        Message toolResultMessage = Message.builder()
                .role("tool")
                .content(toolResultContent)
                .toolCallId(toolCall.getId())
                .build();

        GenerationResult finalResult = qianwenService.callWithToolResult(
                getSessionId(), modelName, parameters, sdkTools, toolCallMessage, toolResultMessage);

        String finalReply = finalResult.getOutput().getChoices().get(0).getMessage().getContent();
        return new ChatCompletion(finalReply, toolCallInfo);
    }

    private void checkForWorkflowCompletion(String llmResponse) {
        if (llmResponse == null || llmResponse.isEmpty()) {
            return;
        }

        // 使用能够直接捕获最后一个流程名的正则表达式
        Pattern pattern = Pattern.compile("我已完成流程\\[(?:.*[—→>]\\s*)?([^\\]]+)\\]");
        Matcher matcher = pattern.matcher(llmResponse);

        if (matcher.find()) {
            // 直接从捕获组1中获取最终的目标流程名
            String targetProcessName = matcher.group(1);
            if (targetProcessName == null || targetProcessName.trim().isEmpty()) {
                return;
            }
            log.info("检测到工作流指令，并解析出目标流程名为: '{}'", targetProcessName);

            // 遍历所有当前可执行的流程，进行精确匹配
            List<String> availableProcesses = getAvailableProcesses();
            for (String process : availableProcesses) {
                String sanitizedProcessName = sanitizeProcessName(process);
                if (targetProcessName.equals(sanitizedProcessName)) {
                    log.info("目标流程 '{}' 匹配到可用流程 '{}' (原始名: '{}')。正在完成该流程。",
                            targetProcessName, sanitizedProcessName, process);
                    processManager.completeProcess(process);
                    break; // 假设每次只完成一个流程
                }
            }
        }
    }

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
                throw new RuntimeException("工具定义转换失败。", e);
            }
        }
        return sdkTools;
    }

    private String executeTool(String toolName, JsonNode args) {
        switch (toolName) {
            case "queryAllPlans": return toolService.queryAllPlans();
            case "compareTwoPlans":
                String plan1 = args.get("planName1").asText();
                String plan2 = args.get("planName2").asText();
                return toolService.compareTwoPlans(plan1, plan2);
            case "getPlanDetails":
                String planName = args.get("planName").asText();
                return toolService.getPlanDetails(planName);
            default:
                log.warn("尝试调用一个未知的工具: {}", toolName);
                return "{\"error\": \"未知工具\"}";
        }
    }

    private String buildDynamicPersona() {
        String personaTemplate = workflowStateService.getPersonaTemplate();
        List<String> availableProcesses = getAvailableProcesses();
        String availableTasksStr = availableProcesses.isEmpty() ? "无" : sanitizeProcessNames(availableProcesses).stream().collect(Collectors.joining("→"));
        List<String> allProcesses = workflowStateService.getCurrentProcesses();
        String workflowStr = sanitizeProcessNames(allProcesses).stream().collect(Collectors.joining(" → "));
        return personaTemplate
                .replace("{tasks}", availableTasksStr)
                .replace("{workflow}", workflowStr);
    }

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

        List<String> combinedAvailable = Stream.concat(availableFromPending.stream(), repeatableAndCompleted.stream())
                .distinct().collect(Collectors.toList());

        return allProcesses.stream()
                .filter(combinedAvailable::contains)
                .collect(Collectors.toList());
    }

    private String sanitizeProcessName(String processName) {
        String name = processName.trim();
        if (name.endsWith("*")) { name = name.substring(0, name.length() - 1); }
        return name.replaceAll("^\\d+\\.?\\s*", "").trim();
    }

    private List<String> sanitizeProcessNames(List<String> processNames) {
        return processNames.stream().map(this::sanitizeProcessName).collect(Collectors.toList());
    }

    public UiState getCurrentUiState() {
        var statuses = processManager.getAllProcesses().stream()
                .collect(Collectors.toMap(p -> p, p -> processManager.getUnfinishedProcesses().contains(p) ? "PENDING" : "COMPLETED", (v1, v2) -> v1, LinkedHashMap::new));
        String persona = buildDynamicPersona();
        String rawTemplate = workflowStateService.getPersonaTemplate();
        String openingMonologue = workflowStateService.getOpeningMonologue();
        return new UiState(statuses, persona, rawTemplate, openingMonologue,
                modelConfigurationService.getModelName(),
                modelConfigurationService.getTemperature(),
                modelConfigurationService.getTopP());
    }

    public void resetProcessesAndSaveHistory() {
        List<Message> history = qianwenService.popConversationHistory(getSessionId());
        if (history != null && !history.isEmpty()) {
            historyService.saveConversationToFile("", history);
        }
        processManager.reset();
        this.silentCount = 0;
    }

    public void saveHistoryOnExit() {
        List<Message> history = qianwenService.getConversationHistory(getSessionId());
        if (history != null && !history.isEmpty()) {
            historyService.saveConversationToFile("", history);
        }
    }

    public void updateWorkflow(ConfigurationRequest config) {
        saveHistoryOnExit();
        workflowStateService.updateWorkflow(config.getProcesses(), config.getPersonaTemplate(), config.getDependencies(), config.getOpeningMonologue());
        processManager.updateProcesses(config.getProcesses());
        modelConfigurationService.updateModelName(config.getModelName());
        modelConfigurationService.updateTemperature(config.getTemperature());
        modelConfigurationService.updateTopP(config.getTopP());
        qianwenService.popConversationHistory(getSessionId());
        this.silentCount = 0;
    }

    public static record ChatCompletion(String reply, ToolCallInfo toolCallInfo) {}
}