package org.example.agent.service;


import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agent.model.tool.*; // 导入我们手写的所有工具模型
import org.example.agent.component.ProcessManager;
import org.example.agent.dto.ConfigurationRequest;
import org.example.agent.dto.UiState;
import org.example.agent.service.impl.QianwenServiceImpl;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        this.tools = new ArrayList<>();

        ToolDefinition queryAllPlansTool = ToolDefinition.builder()
                .function(FunctionDefinition.builder()
                        .name("queryAllPlans")
                        .description("查询所有可用的电信套餐列表。")
                        .parameters(ParameterSchema.builder().type("object").properties(Map.of()).build())
                        .build())
                .build();

        ToolDefinition compareTwoPlansTool = ToolDefinition.builder()
                .function(FunctionDefinition.builder()
                        .name("compareTwoPlans")
                        .description("获取两个指定套餐的详细信息以进行比较。")
                        .parameters(ParameterSchema.builder()
                                .properties(Map.of(
                                        "planName1", ParameterProperty.builder().type("string").description("第一个套餐的完整名称").build(),
                                        "planName2", ParameterProperty.builder().type("string").description("第二个套餐的完整名称").build()
                                ))
                                .required(List.of("planName1", "planName2"))
                                .build())
                        .build())
                .build();

        tools.add(queryAllPlansTool);
        tools.add(compareTwoPlansTool);
    }

    private String getSessionId() {
        return httpSession.getId();
    }

    public String processUserMessage(String userMessage) {
        String modelName = modelConfigurationService.getModelName();
        var parameters = modelConfigurationService.getParametersAsMap();

        GenerationResult result = qianwenService.chat(
                getSessionId(), userMessage, modelName,
                workflowStateService.getPersonaTemplate(),
                workflowStateService.getOpeningMonologue(),
                parameters, tools);

        boolean isToolCall = "tool_calls".equalsIgnoreCase(result.getOutput().getChoices().get(0).getFinishReason());

        if (isToolCall) {
            Message toolCallMessage = result.getOutput().getChoices().get(0).getMessage();

            // 【终极核心改造】通过JSON作为中间桥梁，解决类型不兼容问题
            List<org.example.agent.model.tool.ToolCall> toolCalls;
            try {
                // 1. 先将SDK返回的、类型不明的对象列表，序列化为JSON字符串
                String toolCallsJson = objectMapper.writeValueAsString(toolCallMessage.getToolCalls());
                // 2. 再将JSON字符串，反序列化为我们手写的、干净的ToolCall对象列表
                toolCalls = objectMapper.readValue(toolCallsJson, new TypeReference<List<org.example.agent.model.tool.ToolCall>>() {});
            } catch (Exception e) {
                log.error("手动转换ToolCall对象时出错", e);
                return "抱歉，模型返回的工具调用格式不兼容，转换失败。";
            }

            if (toolCalls == null || toolCalls.isEmpty()) {
                log.error("模型返回tool_calls，但解析后的toolCalls列表为空。");
                return "抱歉，模型响应出现内部错误，无法执行工具。";
            }

            org.example.agent.model.tool.ToolCall toolCall = toolCalls.get(0);
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

            // 构造返回给SDK的消息
            Message toolResultMessage = Message.builder()
                    .role("tool")
                    .content(toolResultContent)
                    .toolCallId(toolCall.getId()) // 使用我们自己对象的ID
                    .build();

            GenerationResult finalResult = qianwenService.callWithToolResult(
                    getSessionId(), modelName, parameters, tools, toolCallMessage, toolResultMessage);

            String finalContent = finalResult.getOutput().getChoices().get(0).getMessage().getContent();
            qianwenService.addAssistantMessageToHistory(getSessionId(), finalContent);
            return finalContent;
        } else {
            String directReply = result.getOutput().getChoices().get(0).getMessage().getContent();
            qianwenService.addAssistantMessageToHistory(getSessionId(), directReply);
            return directReply;
        }
    }

    private String executeTool(String toolName, JsonNode args) {
        switch (toolName) {
            case "queryAllPlans":
                return toolService.queryAllPlans();
            case "compareTwoPlans":
                String plan1 = args.get("planName1").asText();
                String plan2 = args.get("planName2").asText();
                return toolService.compareTwoPlans(plan1, plan2);
            default:
                log.warn("尝试调用一个未知的工具: {}", toolName);
                return "{\"error\": \"未知工具\"}";
        }
    }

    // ... 其他方法保持不变 ...
    public UiState getCurrentUiState() {
        var statuses = processManager.getAllProcesses().stream()
                .collect(Collectors.toMap(p -> p, p -> processManager.getUnfinishedProcesses().contains(p) ? "PENDING" : "COMPLETED", (v1, v2) -> v1, LinkedHashMap::new));
        String persona = workflowStateService.getPersonaTemplate();
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
        qianwenService.popConversationHistory(getSessionId());
    }
}