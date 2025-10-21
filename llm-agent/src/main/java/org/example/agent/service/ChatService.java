package org.example.agent.service;

// ... (imports remain the same)
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

    // ... (fields remain the same)
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

    // --- 修改：ChatCompletion record 定义 ---
    public static record ChatCompletion(String reply, ToolCallInfo toolCallInfo, String personaUsed) {}


    // (构造函数 ... 保持不变)
    public ChatService(LlmServiceManager llmServiceManager, ProcessManager processManager,
                       WorkflowStateService workflowStateService, ModelConfigurationService modelConfigurationService,
                       HistoryService historyService, HttpSession httpSession, ToolService toolService) {
        // ... assignment ...
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

    // --- 核心逻辑更新 ---
    public ChatCompletion processUserMessage(String userMessage) throws IOException {
        long startTime = System.currentTimeMillis();
        String defaultPersona = buildDynamicPersona(false); // 先生成默认persona

        if (" ".equals(userMessage)) {
            // (静默处理逻辑 ... 保持不变, 但返回默认persona)
            silentCount++;
            if (silentCount >= 4) {
                forceCompleteAllProcesses();
                silentCount = 0;
                return new ChatCompletion("好的，先不打扰您了，礼貌起见请您先挂机，祝您生活愉快，再见！", null, defaultPersona);
            } else {
                List<String> cannedResponses = Arrays.asList("喂，您好，能听到说话么？", "我这边是中国移动流量卡渠道商的，能听到说话么？", "喂？您好，这边听不到您的声音，是信号不好吗？");
                return new ChatCompletion(cannedResponses.get(silentCount - 1), null, defaultPersona);
            }
        } else {
            silentCount = 0;
        }

        if (getAvailableProcesses().isEmpty() && processManager.getUnfinishedProcesses().isEmpty()) {
            return new ChatCompletion("🎉 恭喜！所有流程均已完成！", null, defaultPersona);
        }

        // --- 核心逻辑：先检测用户输入，再构建人设 ---
        // 1. 检测用户输入是否为“打断”
        boolean isInterrupted = userMessage != null && userMessage.contains("打断");

        // --- 变更：如果用户打断，则不调用LLM，直接返回 ---
        if (isInterrupted) {
            log.info("检测到用户输入'打断'，执行手动回复，不调用LLM。");

            // 2a. 构建人设 (用于UI更新，code=2)
            // 传入 true 来生成 "code=2" (打断状态) 的人设
            String personaForUiUpdate = buildDynamicPersona(true);

            // 2b. 手动回复
            String manualReply = "您请说，";

            // 2c. 返回 ChatCompletion，跳过 LLM 调用
            // WebController 将使用 personaForUiUpdate 来更新UI
            return new ChatCompletion(manualReply, null, personaForUiUpdate);
        }
        // --- 变更结束 ---


        // 2. (若未打断) 根据检测结果，动态构建人设 (替换占位符)
        // 'isInterrupted' 在这里一定是 false
        String persona = buildDynamicPersona(isInterrupted);
        log.info("最终发送给LLM的人设:\n{}", persona);

        // 3. (后续步骤) 拼接上下文并发送给大模型
        String modelName = modelConfigurationService.getModelName();
        var parameters = modelConfigurationService.getParametersAsMap();
        String openingMonologue = workflowStateService.getOpeningMonologue();

        long llm1StartTime = System.currentTimeMillis();
        // 传入的是动态构建好的人设 (persona)
        LlmResponse result = getLlmService().chat(getSessionId(), userMessage, modelName, persona, openingMonologue, parameters, tools);
        long llm1EndTime = System.currentTimeMillis();
        long llmFirstCallTime = llm1EndTime - llm1StartTime;
        log.info("【LLM首次调用耗时】: {} 毫秒", llmFirstCallTime);


        log.info("【LLM原始响应】\n{}", result.getContent());
        String finalContent;
        ToolCallInfo toolCallInfo = null;
        if (result.hasToolCalls()) {
            // handleToolCalls 现在也返回 ChatCompletion
            return handleToolCalls(result, modelName, parameters, tools, llmFirstCallTime, persona); // <-- 传递 persona
        } else {
            finalContent = result.getContent();
        }

        // --- 只检查流程关键字 ---
        processResponseKeywords(finalContent);

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;
        String finalReply = finalContent + "\n\n(LLM 响应耗时: " + responseTime + " 毫秒)";
        // --- 修改：在返回的 ChatCompletion 中包含实际使用的 persona ---
        return new ChatCompletion(finalReply, toolCallInfo, persona); // <--- 返回 persona
    }


    private void forceCompleteAllProcesses() {
        workflowStateService.getCurrentProcesses().forEach(processManager::completeProcess);
    }

    // --- 修改：handleToolCalls 接收并返回 personaUsed ---
    private ChatCompletion handleToolCalls(LlmResponse result, String modelName, Map<String, Object> parameters, List<ToolDefinition> tools, long llmFirstCallTime, String personaUsedInFirstCall) { // <--- 接收 persona
        LlmToolCall toolCall = result.getToolCalls().get(0);
        String toolName = toolCall.getToolName();
        String toolArgsString = toolCall.getArguments();
        log.info("LLM决定调用工具: {}, 参数: {}", toolName, toolArgsString);

        JsonNode toolArgs;
        try {
            toolArgs = objectMapper.readTree(toolArgsString);
        } catch (JsonProcessingException e) {
            log.error("模型返回的工具参数格式不正确", e);
            // 错误时返回传入的人设
            return new ChatCompletion("抱歉，模型返回的工具参数格式不正确。", null, personaUsedInFirstCall);
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
        // 第二次调用 LLM，通常不动态修改 persona
        LlmResponse finalResult = getLlmService().chatWithToolResult(getSessionId(), modelName, parameters, tools, toolResultMessage);
        long llm2EndTime = System.currentTimeMillis();
        long llmSecondCallTime = llm2EndTime - llm2StartTime;
        log.info("【LLM二次调用耗时】: {} 毫秒", llmSecondCallTime);

        ToolCallInfo toolCallInfo = new ToolCallInfo(toolName, toolArgsString, toolResultContent, toolExecutionTime, llmFirstCallTime, llmSecondCallTime);

        log.info("【LLM工具调用后原始响应】\n{}", finalResult.getContent());

        processResponseKeywords(finalResult.getContent());

        // --- 返回传入的人设，因为这次调用没有动态修改 ---
        return new ChatCompletion(finalResult.getContent(), toolCallInfo, personaUsedInFirstCall); // <--- 返回 persona
    }


    // --- 只检查流程关键字 ---
    private void processResponseKeywords(String llmResponse) {
        if (llmResponse == null || llmResponse.isEmpty()) return;

        // 1. 检查流程完成
        Pattern pattern = Pattern.compile("我已完成流程\\[(?:.*[—→>]\\s*)?([^\\]]+)\\]");
        Matcher matcher = pattern.matcher(llmResponse);
        if (matcher.find()) {
            String targetProcessName = matcher.group(1).trim();
            if (!targetProcessName.isEmpty()) {
                getAvailableProcesses().stream()
                        .filter(process -> sanitizeProcessName(process).equals(targetProcessName))
                        .findFirst()
                        .ifPresent(processManager::completeProcess);
            }
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

    // --- 根据 isInterrupted 动态替换 {code} 为 "1" 或 "2" ---
    private String buildDynamicPersona(boolean isInterrupted) {
        // 1. 获取包含 {code} 的原始模板
        String personaTemplate = workflowStateService.getPersonaTemplate();

        // 2. 准备替换值
        String codeValue;
        if (isInterrupted) {
            // 当用户说"打断"时，替换为 "2"
            codeValue = "2";
            log.info("动态替换人设: {{code}} -> 2 (打断)");

        } else {
            // 正常情况，替换为 "1"
            codeValue = "1";
            log.info("动态替换人设: {{code}} -> 1 (正常)");
        }

        // 3. 执行替换
        String personaWithCode = personaTemplate.replace("{code}", codeValue);

        // 4. 替换其他占位符 ({tasks}, {workflow})
        String availableTasksStr = sanitizeProcessNames(getAvailableProcesses()).stream().collect(Collectors.joining("→"));
        String workflowStr = sanitizeProcessNames(workflowStateService.getCurrentProcesses()).stream().collect(Collectors.joining(" → "));

        String finalPersona = personaWithCode
                .replace("{tasks}", availableTasksStr.isEmpty() ? "无" : availableTasksStr)
                .replace("{workflow}", workflowStr);

        return finalPersona;
    }


    // (getAvailableProcesses, sanitizeProcessName, sanitizeProcessNames ... 保持不变)
    private List<String> getAvailableProcesses() {
        // ... implementation ...
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
        // ... implementation ...
        String name = processName.trim();
        return name.endsWith("*") ? name.substring(0, name.length() - 1).replaceAll("^\\d+\\.?\\s*", "").trim() : name.replaceAll("^\\d+\\.?\\s*", "").trim();
    }

    private List<String> sanitizeProcessNames(List<String> processNames) {
        // ... implementation ...
        return processNames.stream().map(this::sanitizeProcessName).collect(Collectors.toList());
    }


    // --- 新增：重载方法，用于 reset, configure, index 等场景 ---
    public UiState getCurrentUiState() {
        // 默认生成非打断状态的预览人设
        String previewPersona = buildDynamicPersona(false);
        return getCurrentUiState(previewPersona);
    }

    // --- 修改：接收实际使用的人设作为参数 ---
    public UiState getCurrentUiState(String actualPersonaUsed) { // <--- 接收参数
        Map<String, String> statuses = processManager.getAllProcesses().stream()
                .collect(Collectors.toMap(p -> p, p -> processManager.getUnfinishedProcesses().contains(p) ? "PENDING" : "COMPLETED", (v1, v2) -> v1, LinkedHashMap::new));

        return new UiState(statuses,
                actualPersonaUsed, // <--- 使用传入的、实际发送给 LLM 的人设
                workflowStateService.getPersonaTemplate(), // 原始模板
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


    // (resetProcessesAndSaveHistory, saveHistoryOnExit, saveHistory, updateWorkflow ... 保持不变)
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
}