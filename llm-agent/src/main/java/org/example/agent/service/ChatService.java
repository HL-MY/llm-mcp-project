package org.example.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct; // <-- 【关键】 导入 PostConstruct
import jakarta.servlet.http.HttpSession;
import org.example.agent.component.ProcessManager;
import org.example.agent.dto.*;
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
    private final ConfigService configService;
    private final HistoryService historyService;
    private final HttpSession httpSession;
    private final ToolService toolService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<ToolDefinition> tools;
    private int silentCount = 0;

    private DecisionProcessInfo lastDecisionProcess;

    public static record ChatCompletion(String reply, ToolCallInfo toolCallInfo, DecisionProcessInfo decisionProcessInfo, String personaUsed) {}

    // --- 【重构】构造函数 ---
    // (只进行依赖注入，不执行任何逻辑)
    public ChatService(LlmServiceManager llmServiceManager, ProcessManager processManager,
                       ConfigService configService,
                       HistoryService historyService, HttpSession httpSession, ToolService toolService) {
        this.llmServiceManager = llmServiceManager;
        this.processManager = processManager;
        this.configService = configService;
        this.historyService = historyService;
        this.httpSession = httpSession;
        this.toolService = toolService;
    }


    /**
     * 【新增】@PostConstruct 初始化方法
     * 此方法在所有依赖注入完成后执行，避开了构造函数循环依赖
     */
    @PostConstruct
    public void init() {
        // 【修改】初始化 ProcessManager 的逻辑移到这里
        this.processManager.updateProcesses(configService.getProcessList());

        // 【修改】初始化 Tools 的逻辑也移到这里
        this.tools = TelecomToolFactory.getAllToolDefinitions();
    }

    private String getSessionId() {
        return httpSession.getId();
    }

    private LlmService getLlmService(String modelName) {
        return llmServiceManager.getService(modelName);
    }

    private LlmService getLlmServiceForMainModel() {
        String modelName = configService.getModelParams(ConfigService.KEY_MAIN_MODEL).getModelName();
        return getLlmService(modelName);
    }

    // --- 【重构】processUserMessage 方法 ---
    public ChatCompletion processUserMessage(String userMessage) throws IOException {
        long startTime = System.currentTimeMillis();

        // 1. --- 手动回复检查 (打断、空格) ---
        boolean isInterrupted = userMessage != null && userMessage.contains("打断");
        boolean isSpaceMessage = " ".equals(userMessage);

        String personaForUiUpdate;

        if (isInterrupted) {
            log.info("检测到用户输入'打断'，执行手动回复，不调用LLM。");
            personaForUiUpdate = buildDynamicPersona("2");
            String manualReply = "您请说，";
            return new ChatCompletion(manualReply, null, null, personaForUiUpdate);
        }

        if (isSpaceMessage) {
            log.info("检测到用户输入'空格'，使用 code=3 并执行手动回复，不调用LLM。");
            personaForUiUpdate = buildDynamicPersona("3");
            silentCount++;
            String manualReply;
            if (silentCount >= 4) {
                forceCompleteAllProcesses();
                silentCount = 0;
                manualReply = "好的，先不打扰您了，礼貌起见请您先挂机，祝您生活愉快，再见！";
            } else {
                List<String> cannedResponses = Arrays.asList("喂，您好，能听到说话么？", "我这边是中国移动流量卡渠道商的，能听到说话么？", "喂？您好，这边听不到您的声音，是信号不好吗？");
                manualReply = cannedResponses.get(silentCount - 1);
            }
            LlmMessage userSpaceMessage = LlmMessage.builder().role(LlmMessage.Role.USER).content(userMessage).build();
            LlmMessage botSilentReply = LlmMessage.builder().role(LlmMessage.Role.ASSISTANT).content(manualReply).build();
            try {
                getLlmServiceForMainModel().addMessagesToHistory(getSessionId(), userSpaceMessage, botSilentReply);
                log.info("已将'空格'和'沉默回复'添加到会话历史。");
            } catch (Exception e) {
                log.error("手动添加会话历史失败", e);
            }
            return new ChatCompletion(manualReply, null, null, personaForUiUpdate);
        }

        if (getAvailableProcesses().isEmpty() && processManager.getUnfinishedProcesses().isEmpty()) {
            String defaultPersona = buildDynamicPersona("1");
            return new ChatCompletion("🎉 恭喜！所有流程均已完成！", null, null, defaultPersona);
        }

        log.info("检测到正常消息，重置 silentCount 并开始预处理。");
        silentCount = 0;

        // --- "思考链" DTO ---
        this.lastDecisionProcess = new DecisionProcessInfo();
        long preStartTime = System.currentTimeMillis();

        // 2. --- "小模型" 预处理调用 ---
        PreProcessingResult preResult = preProcessInput(userMessage, this.lastDecisionProcess);
        long preEndTime = System.currentTimeMillis();
        this.lastDecisionProcess.setPreProcessingTimeMs(preEndTime - preStartTime);
        log.info("预处理结果: Emotion={}, Intent={}, Sensitive={}", preResult.getEmotion(), preResult.getIntent(), preResult.isSensitive());

        // 3. --- 检查 敏感词 和 兜底阈值 ---
        if (preResult.isSensitive()) {
            this.lastDecisionProcess.setSelectedStrategy("SENSITIVE_FALLBACK");
            return new ChatCompletion(configService.getSensitiveResponse(), null, this.lastDecisionProcess, buildDynamicPersona("1"));
        }

        // 【修改】获取激活的策略 (每次都从数据库获取，保证实时)
        Map<String, String> activeIntentStrategies = configService.getActiveStrategies("INTENT");
        Map<String, String> activeEmotionStrategies = configService.getActiveStrategies("EMOTION");

        // 【修改】如果意图不在激活列表，也视为 "unknown"
        String finalIntent = preResult.getIntent();
        // 注意：我们数据库存的是中文key
        if (!activeIntentStrategies.containsKey(finalIntent)) {
            log.warn("意图 '{}' 被检测到，但未在激活列表中，回退到 '意图不明'", finalIntent);
            finalIntent = "意图不明"; // 使用中文键
        }

        if ("意图不明".equals(finalIntent)) {
            this.lastDecisionProcess.setSelectedStrategy("UNKNOWN_FALLBACK");
            return new ChatCompletion(configService.getFallbackResponse(), null, this.lastDecisionProcess, buildDynamicPersona("1"));
        }

        // 4. --- 选择策略并构建最终人设 ---
        String intentStrategy = activeIntentStrategies.getOrDefault(finalIntent, "");
        String emotionStrategy = activeEmotionStrategies.getOrDefault(preResult.getEmotion(), "");

        String strategyPrompt = intentStrategy;
        if (emotionStrategy != null && !emotionStrategy.isEmpty()) {
            strategyPrompt += "\n" + emotionStrategy;
        }

        this.lastDecisionProcess.setSelectedStrategy(strategyPrompt);

        String persona = buildDynamicPersona("1", strategyPrompt);
        log.info("最终发送给LLM的人设 (含策略):\n{}", persona);

        // 5. --- "主模型"调用 ---
        ModelParameters mainParams = configService.getModelParams(ConfigService.KEY_MAIN_MODEL);
        String modelName = mainParams.getModelName();
        var parameters = mainParams.getParametersAsMap();
        String openingMonologue = configService.getOpeningMonologue();

        long llm1StartTime = System.currentTimeMillis();
        LlmResponse result = getLlmService(modelName).chat(getSessionId(), userMessage, modelName, persona, openingMonologue, parameters, tools);
        long llm1EndTime = System.currentTimeMillis();
        long llmFirstCallTime = llm1EndTime - llm1StartTime;
        log.info("【LLM主调用耗时】: {} 毫秒", llmFirstCallTime);

        log.info("【LLM原始响应】\n{}", result.getContent());
        String finalContent;
        ToolCallInfo toolCallInfo = null;
        if (result.hasToolCalls()) {
            return handleToolCalls(result, modelName, parameters, tools, llmFirstCallTime, this.lastDecisionProcess, persona);
        } else {
            finalContent = result.getContent();
        }

        processResponseKeywords(finalContent);

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;
        String finalReply = finalContent + "\n\n(LLM 响应耗时: " + responseTime + " 毫秒)";
        return new ChatCompletion(finalReply, toolCallInfo, this.lastDecisionProcess, persona);
    }

    // --- 【重构】preProcessInput 方法 ---
    private PreProcessingResult preProcessInput(String userMessage, DecisionProcessInfo decisionProcess) {
        String tempSessionId = getSessionId() + "_preprocessing";

        String preProcessingPrompt = configService.getPreProcessingPrompt();
        String fullPrompt = preProcessingPrompt + "\n输入: \"" + userMessage + "\"";

        ModelParameters preParams = configService.getModelParams(ConfigService.KEY_PRE_MODEL);
        String preProcessorModelName = preParams.getModelName();
        var parameters = preParams.getParametersAsMap();

        decisionProcess.setPreProcessingModel(preProcessorModelName);

        LlmService llmService;
        try {
            llmService = getLlmService(preProcessorModelName);
        } catch (Exception e) {
            log.error("获取预处理模型 '{}' 失败。回退使用主模型。", preProcessorModelName, e);
            ModelParameters mainParams = configService.getModelParams(ConfigService.KEY_MAIN_MODEL);
            llmService = getLlmService(mainParams.getModelName());
            preProcessorModelName = mainParams.getModelName();
            decisionProcess.setPreProcessingModel(preProcessorModelName + " (回退)");
        }

        try {
            LlmResponse preResponse = llmService.chat(tempSessionId, userMessage, preProcessorModelName, fullPrompt, null, parameters, null);
            llmService.popConversationHistory(tempSessionId);

            String jsonResponse = preResponse.getContent();
            if (jsonResponse.contains("```json")) {
                jsonResponse = jsonResponse.substring(jsonResponse.indexOf('{'), jsonResponse.lastIndexOf('}') + 1);
            }

            // 【修改】使用中文键
            PreProcessingResult result = objectMapper.readValue(jsonResponse, PreProcessingResult.class);

            decisionProcess.setDetectedEmotion(result.getEmotion());
            decisionProcess.setDetectedIntent(result.getIntent());
            decisionProcess.setIsSensitive(result.isSensitive());

            return result;

        } catch (Exception e) {
            log.error("预处理调用失败 (模型: {}) 或解析JSON失败", preProcessorModelName, e);
            // 【修改】使用中文键
            PreProcessingResult fallbackResult = new PreProcessingResult("中性", "意图不明", "false");
            decisionProcess.setDetectedIntent("意图不明 (解析失败)");
            return fallbackResult;
        }
    }


    private void forceCompleteAllProcesses() {
        configService.getProcessList().forEach(processManager::completeProcess);
    }

    // --- (handleToolCalls, processResponseKeywords, executeTool 保持不变) ---
    private ChatCompletion handleToolCalls(LlmResponse result, String modelName, Map<String, Object> parameters, List<ToolDefinition> tools,
                                           long llmFirstCallTime, DecisionProcessInfo decisionProcessInfo, String personaUsedInFirstCall) {
        LlmToolCall toolCall = result.getToolCalls().get(0);
        String toolName = toolCall.getToolName();
        String toolArgsString = toolCall.getArguments();
        log.info("LLM决定调用工具: {}, 参数: {}", toolName, toolArgsString);

        JsonNode toolArgs;
        try {
            toolArgs = objectMapper.readTree(toolArgsString);
        } catch (JsonProcessingException e) {
            log.error("模型返回的工具参数格式不正确", e);
            return new ChatCompletion("抱歉，模型返回的工具参数格式不正确。", null, decisionProcessInfo, personaUsedInFirstCall);
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
        LlmResponse finalResult = getLlmService(modelName).chatWithToolResult(getSessionId(), modelName, parameters, tools, toolResultMessage);
        long llm2EndTime = System.currentTimeMillis();
        long llmSecondCallTime = llm2EndTime - llm2StartTime;
        log.info("【LLM二次调用耗时】: {} 毫秒", llmSecondCallTime);

        ToolCallInfo toolCallInfo = new ToolCallInfo(toolName, toolArgsString, toolResultContent, toolExecutionTime, llmFirstCallTime, llmSecondCallTime);

        log.info("【LLM工具调用后原始响应】\n{}", finalResult.getContent());
        processResponseKeywords(finalResult.getContent());

        return new ChatCompletion(finalResult.getContent(), toolCallInfo, decisionProcessInfo, personaUsedInFirstCall);
    }

    private void processResponseKeywords(String llmResponse) {
        if (llmResponse == null || llmResponse.isEmpty()) return;
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
                // 【修改】ToolService 已经返回了 String，我们不再需要
                // objectMapper.writeValueAsString() 来二次序列化
                try {
                    // 【旧代码 - 错误】
                    // return objectMapper.writeValueAsString(toolService.compareTwoPlans(args.get("planName1").asText(), args.get("planName2").asText()));

                    // 【新代码 - 正确】
                    // 直接返回 ToolService 的结果
                    return toolService.compareTwoPlans(args.get("planName1").asText(), args.get("planName2").asText());

                } catch (/* JsonProcessingException */ Exception e) { // 异常类型可能需要改一下，或者保持 Exception
                    log.error("序列化 PlanService 结果失败", e);
                    return "{\"error\": \"无法序列化套餐对比结果\"}";
                }
            case "queryMcpFaq":
                // （这部分你写的是对的，保持不变）
                return toolService.queryMcpFaq(args.get("intent").asText());
            default:
                return "{\"error\": \"未知工具\"}";
        }
    }

    // --- 【重构】buildDynamicPersona 方法 ---
    private String buildDynamicPersona(String codeValue) {
        return buildDynamicPersona(codeValue, null);
    }
    private String buildDynamicPersona(String codeValue, String strategyPrompt) {
        String personaTemplate = configService.getPersonaTemplate();

        String statusDesc;
        switch (codeValue) {
            case "1": statusDesc = "正常"; break;
            case "2": statusDesc = "打断"; break;
            case "3": statusDesc = "空格沉默"; break;
            default: statusDesc = "未知(" + codeValue + ")"; break;
        }
        log.info("动态替换人设: {{code}} -> {} (状态: {})", codeValue, statusDesc);
        String personaWithCode = personaTemplate.replace("{code}", codeValue);

        String availableTasksStr = sanitizeProcessNames(getAvailableProcesses()).stream().collect(Collectors.joining("→"));
        String workflowStr = sanitizeProcessNames(configService.getProcessList()).stream().collect(Collectors.joining(" → "));

        String finalPersona = personaWithCode
                .replace("{tasks}", availableTasksStr.isEmpty() ? "无" : availableTasksStr)
                .replace("{workflow}", workflowStr);

        if (strategyPrompt != null && !strategyPrompt.isEmpty()) {
            finalPersona += "\n\n--- 当前策略 ---\n" + strategyPrompt;
        }
        return finalPersona;
    }


    // --- 【重构】getAvailableProcesses, sanitize... ---
    private List<String> getAvailableProcesses() {
        List<String> unfinished = processManager.getUnfinishedProcesses();
        Map<String, List<String>> rules = parseAndSetDependencies(configService.getDependencies());
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

    // --- 【重构】getCurrentUiState 方法 ---
    public UiState getCurrentUiState(String actualPersonaUsed) {
        Map<String, String> statuses = processManager.getAllProcesses().stream()
                .collect(Collectors.toMap(p -> p, p -> processManager.getUnfinishedProcesses().contains(p) ? "PENDING" : "COMPLETED", (v1, v2) -> v1, LinkedHashMap::new));

        // 只返回会话状态
        return new UiState(statuses, actualPersonaUsed, null);
    }

    public UiState getInitialUiState() {
        String openingMonologue = configService.getOpeningMonologue();
        String persona = buildDynamicPersona("1"); // 默认预览

        Map<String, String> statuses = processManager.getAllProcesses().stream()
                .collect(Collectors.toMap(p -> p, p -> processManager.getUnfinishedProcesses().contains(p) ? "PENDING" : "COMPLETED", (v1, v2) -> v1, LinkedHashMap::new));

        return new UiState(statuses, persona, openingMonologue);
    }

    // --- (reset, saveHistory 保持不变) ---
    public void resetProcessesAndSaveHistory() {
        saveHistory(getLlmServiceForMainModel().popConversationHistory(getSessionId()));
        // 【修改】重置时也从 ConfigService 重新加载流程
        processManager.updateProcesses(configService.getProcessList());
        this.silentCount = 0;
    }
    public void saveHistoryOnExit() {
        saveHistory(getLlmServiceForMainModel().getConversationHistory(getSessionId()));
    }
    private void saveHistory(List<LlmMessage> history) {
        if (history != null && !history.isEmpty()) {
            // historyService.saveConversationToFile("", history);
        }
    }

    // --- 【新增】用于解析依赖的辅助方法 (从旧 WorkflowStateService 移入) ---
    private Map<String, List<String>> parseAndSetDependencies(String dependencies) {
        Map<String, List<String>> rules = new HashMap<>();
        if (dependencies == null || dependencies.trim().isEmpty()) {
            return rules;
        }
        String[] lines = dependencies.split("\\r?\\n");
        for (String line : lines) {
            if (!line.contains("->")) continue;
            String[] parts = line.split("->");
            if (parts.length < 2) continue;
            String process = findProcessByName(parts[0].trim());
            if (process == null) continue;
            List<String> prerequisites = Arrays.stream(parts[1].split(","))
                    .map(String::trim).map(this::findProcessByName)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            if (!prerequisites.isEmpty()) { rules.put(process, prerequisites); }
        }
        return rules;
    }

    private String findProcessByName(String nameOrId) {
        List<String> currentProcesses = configService.getProcessList();
        for (String p : currentProcesses) {
            if (p.trim().equalsIgnoreCase(nameOrId)) return p;
        }
        for (String p : currentProcesses) {
            if (p.trim().matches("^" + Pattern.quote(nameOrId) + "[.\\s].*")) return p;
        }
        for (String p : currentProcesses) {
            String sanitizedName = p.replaceAll("^\\d+\\.?\\s*", "").replace("*","").trim();
            if(sanitizedName.equalsIgnoreCase(nameOrId)) return p;
        }
        return null;
    }
}