package org.example.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import org.example.agent.component.ProcessManager;
import org.example.agent.dto.ConfigurationRequest;
import org.example.agent.dto.ToolCallInfo;
import org.example.agent.dto.UiState;
import org.example.agent.factory.TelecomToolFactory;
import org.example.llm.dto.llm.LlmMessage;
import org.example.llm.dto.llm.LlmResponse;
import org.example.llm.dto.llm.LlmToolCall;
import org.example.llm.dto.tool.ToolDefinition;
import org.example.llm.service.LlmService;
import org.example.llm.service.LlmServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@SessionScope
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final LlmServiceManager llmServiceManager;
    private final ProcessManager processManager;
    private final WorkflowStateService workflowStateService;
    private final ModelConfigurationService modelConfigurationService;
    private final HistoryService historyService;
    private final HttpSession httpSession;
    private final ToolService toolService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<ToolDefinition> tools;
    private int silentCount = 0;

    public ChatService(LlmServiceManager llmServiceManager, ProcessManager processManager,
                       WorkflowStateService workflowStateService, ModelConfigurationService modelConfigurationService,
                       HistoryService historyService, HttpSession httpSession, ToolService toolService) {
        this.llmServiceManager = llmServiceManager;
        this.processManager = processManager;
        this.workflowStateService = workflowStateService;
        this.modelConfigurationService = modelConfigurationService;
        this.historyService = historyService;
        this.httpSession = httpSession;
        this.toolService = toolService;
    }

    @PostConstruct
    public void initTools() {
        this.tools = TelecomToolFactory.getAllToolDefinitions();
    }

    private String getSessionId() {
        return httpSession.getId();
    }

    private LlmService getLlmService() {
        String modelName = modelConfigurationService.getModelName();
        return llmServiceManager.getService(modelName);
    }

    public ChatCompletion processUserMessage(String userMessage) throws IOException {
        long startTime = System.currentTimeMillis();

        if (" ".equals(userMessage)) {
            silentCount++;
            if (silentCount >= 4) {
                forceCompleteAllProcesses();
                silentCount = 0;
                return new ChatCompletion("好的，先不打扰您了，礼貌起见请您先挂机，祝您生活愉快，再见！", null);
            } else {
                List<String> cannedResponses = Arrays.asList("喂，您好，能听到说话么？", "我这边是中国移动流量卡渠道商的，能听到说话么？", "喂？您好，这边听不到您的声音，是信号不好吗？");
                return new ChatCompletion(cannedResponses.get(silentCount - 1), null);
            }
        } else {
            silentCount = 0;
        }

        if (getAvailableProcesses().isEmpty() && processManager.getUnfinishedProcesses().isEmpty()) {
            return new ChatCompletion("🎉 恭喜！所有流程均已完成！", null);
        }

        String persona = buildDynamicPersona();
        String modelName = modelConfigurationService.getModelName();
        var parameters = modelConfigurationService.getParametersAsMap();
        String openingMonologue = workflowStateService.getOpeningMonologue();

        long llm1StartTime = System.currentTimeMillis();
        LlmResponse result = getLlmService().chat(getSessionId(), userMessage, modelName, persona, openingMonologue, parameters, tools);
        long llm1EndTime = System.currentTimeMillis();
        long llmFirstCallTime = llm1EndTime - llm1StartTime;
        log.info("【LLM首次调用耗时】: {} 毫秒", llmFirstCallTime);


        log.info("【LLM原始响应】\n{}", result.getContent());
        String finalContent;
        ToolCallInfo toolCallInfo = null;
        if (result.hasToolCalls()) {
            ChatCompletion toolCallCompletion = handleToolCalls(result, modelName, parameters, tools, llmFirstCallTime);
            finalContent = toolCallCompletion.reply();
            toolCallInfo = toolCallCompletion.toolCallInfo();
        } else {
            finalContent = result.getContent();
        }

        checkForWorkflowCompletion(finalContent);

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;
        String finalReply = finalContent + "\n\n(LLM 响应耗时: " + responseTime + " 毫秒)";
        return new ChatCompletion(finalReply, toolCallInfo);
    }

    private void forceCompleteAllProcesses() {
        workflowStateService.getCurrentProcesses().forEach(processManager::completeProcess);
    }

    private ChatCompletion handleToolCalls(LlmResponse result, String modelName, Map<String, Object> parameters, List<ToolDefinition> tools, long llmFirstCallTime) {
        LlmToolCall toolCall = result.getToolCalls().get(0);
        String toolName = toolCall.getToolName();
        String toolArgsString = toolCall.getArguments();
        log.info("LLM决定调用工具: {}, 参数: {}", toolName, toolArgsString);

        JsonNode toolArgs;
        try {
            toolArgs = objectMapper.readTree(toolArgsString);
        } catch (JsonProcessingException e) {
            log.error("模型返回的工具参数格式不正确", e);
            return new ChatCompletion("抱歉，模型返回的工具参数格式不正确。", null);
        }

        long toolStartTime = System.currentTimeMillis();
        String toolResultContent = executeTool(toolName, toolArgs);
        long toolEndTime = System.currentTimeMillis();
        long toolExecutionTime = toolEndTime - toolStartTime;
        log.info("【Tool 执行耗时】: {} 毫秒", toolExecutionTime);


        LlmMessage toolResultMessage = LlmMessage.builder()
                .role(LlmMessage.Role.TOOL)
                .content(toolResultContent)
                .toolCallId(toolCall.getId())
                .build();

        long llm2StartTime = System.currentTimeMillis();
        LlmResponse finalResult = getLlmService().chatWithToolResult(getSessionId(), modelName, parameters, tools, toolResultMessage);
        long llm2EndTime = System.currentTimeMillis();
        long llmSecondCallTime = llm2EndTime - llm2StartTime;
        log.info("【LLM二次调用耗时】: {} 毫秒", llmSecondCallTime);

        ToolCallInfo toolCallInfo = new ToolCallInfo(toolName, toolArgsString, toolResultContent, toolExecutionTime, llmFirstCallTime, llmSecondCallTime);

        log.info("【LLM工具调用后原始响应】\n{}", finalResult.getContent());
        return new ChatCompletion(finalResult.getContent(), toolCallInfo);
    }

    private void checkForWorkflowCompletion(String llmResponse) {
        if (llmResponse == null || llmResponse.isEmpty()) return;
        Pattern pattern = Pattern.compile("我已完成流程\\[(?:.*[—→>]\\s*)?([^\\]]+)\\]");
        Matcher matcher = pattern.matcher(llmResponse);
        if (matcher.find()) {
            String targetProcessName = matcher.group(1).trim();
            if (targetProcessName.isEmpty()) return;
            getAvailableProcesses().stream()
                    .filter(process -> sanitizeProcessName(process).equals(targetProcessName))
                    .findFirst()
                    .ifPresent(processManager::completeProcess);
        }
    }

    private String executeTool(String toolName, JsonNode args) {
        switch (toolName) {
            case "compareTwoPlans":
                return toolService.compareTwoPlans(args.get("planName1").asText(), args.get("planName2").asText());
            case "queryMcpFaq":
                return toolService.queryMcpFaq(args.get("intent").asText());
            default:
                return "{\"error\": \"未知工具\"}";
        }
    }

    private String buildDynamicPersona() {
        String personaTemplate = workflowStateService.getPersonaTemplate();
        String availableTasksStr = sanitizeProcessNames(getAvailableProcesses()).stream().collect(Collectors.joining("→"));
        String workflowStr = sanitizeProcessNames(workflowStateService.getCurrentProcesses()).stream().collect(Collectors.joining(" → "));
        return personaTemplate.replace("{tasks}", availableTasksStr.isEmpty() ? "无" : availableTasksStr).replace("{workflow}", workflowStr);
    }

    private List<String> getAvailableProcesses() {
        List<String> unfinished = processManager.getUnfinishedProcesses();
        Map<String, List<String>> rules = workflowStateService.getDependencyRules();
        List<String> allProcesses = processManager.getAllProcesses();
        List<String> completed = new ArrayList<>(allProcesses);
        completed.removeAll(unfinished);

        List<String> available = unfinished.stream()
                .filter(task -> {
                    List<String> prerequisites = rules.get(task);
                    return prerequisites == null || completed.containsAll(prerequisites);
                })
                .collect(Collectors.toList());

        completed.stream()
                .filter(task -> task.trim().endsWith("*"))
                .forEach(available::add);

        return allProcesses.stream().filter(available::contains).collect(Collectors.toList());
    }

    private String sanitizeProcessName(String processName) {
        String name = processName.trim();
        return name.endsWith("*") ? name.substring(0, name.length() - 1).replaceAll("^\\d+\\.?\\s*", "").trim() : name.replaceAll("^\\d+\\.?\\s*", "").trim();
    }

    private List<String> sanitizeProcessNames(List<String> processNames) {
        return processNames.stream().map(this::sanitizeProcessName).collect(Collectors.toList());
    }


    public UiState getCurrentUiState() {
        Map<String, String> statuses = processManager.getAllProcesses().stream()
                .collect(Collectors.toMap(p -> p, p -> processManager.getUnfinishedProcesses().contains(p) ? "PENDING" : "COMPLETED", (v1, v2) -> v1, LinkedHashMap::new));

        return new UiState(statuses,
                buildDynamicPersona(),
                workflowStateService.getPersonaTemplate(),
                workflowStateService.getOpeningMonologue(),
                modelConfigurationService.getModelName(),
                modelConfigurationService.getTemperature(),
                modelConfigurationService.getTopP(),
                modelConfigurationService.getMaxTokens(),
                modelConfigurationService.getRepetitionPenalty(),
                modelConfigurationService.getPresencePenalty(),
                modelConfigurationService.getFrequencyPenalty()
        );
    }

    public void resetProcessesAndSaveHistory() {
        saveHistory(getLlmService().popConversationHistory(getSessionId()));
        processManager.reset();
        this.silentCount = 0;
    }

    public void saveHistoryOnExit() {
        saveHistory(getLlmService().getConversationHistory(getSessionId()));
    }

    private void saveHistory(List<LlmMessage> history) {
        if (history != null && !history.isEmpty()) {
            // historyService.saveConversationToFile("", history);
        }
    }

    public void updateWorkflow(ConfigurationRequest config) {
        saveHistoryOnExit();
        workflowStateService.updateWorkflow(config.getProcesses(), config.getPersonaTemplate(), config.getDependencies(), config.getOpeningMonologue());
        processManager.updateProcesses(config.getProcesses());

        modelConfigurationService.updateModelName(config.getModelName());
        modelConfigurationService.updateTemperature(config.getTemperature());
        modelConfigurationService.updateTopP(config.getTopP());
        modelConfigurationService.updateMaxTokens(config.getMaxTokens());
        modelConfigurationService.updateRepetitionPenalty(config.getRepetitionPenalty());
        modelConfigurationService.updatePresencePenalty(config.getPresencePenalty());
        modelConfigurationService.updateFrequencyPenalty(config.getFrequencyPenalty());

        getLlmService().popConversationHistory(getSessionId());
        this.silentCount = 0;
    }

    public static record ChatCompletion(String reply, ToolCallInfo toolCallInfo) {
    }
}