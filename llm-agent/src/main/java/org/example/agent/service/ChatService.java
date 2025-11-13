package org.example.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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
    private List<ToolDefinition> allTools;
    private int silentCount = 0;

    private DecisionProcessInfo lastDecisionProcess;

    private final RuleEngineService ruleEngineService; // 【新增】注入规则引擎

    public static record ChatCompletion(String reply, ToolCallInfo toolCallInfo, DecisionProcessInfo decisionProcessInfo, String personaUsed) {}

    // 【修改】构造函数
    public ChatService(LlmServiceManager llmServiceManager, ProcessManager processManager,
                       ConfigService configService,
                       HistoryService historyService, HttpSession httpSession, ToolService toolService,
                       RuleEngineService ruleEngineService) { // 【新增】
        this.llmServiceManager = llmServiceManager;
        this.processManager = processManager;
        this.configService = configService;
        this.historyService = historyService;
        this.httpSession = httpSession;
        this.toolService = toolService;
        this.ruleEngineService = ruleEngineService; // 【新增】
    }


    /**
     * 【新增】@PostConstruct 初始化方法
     */
    @PostConstruct
    public void init() {
        this.processManager.updateProcesses(configService.getProcessList());
        this.allTools = TelecomToolFactory.getAllToolDefinitions();
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
        // ... (此部分逻辑保持不变)
        if (" ".equals(userMessage)) {
            // ... (省略)
        }

        // ... (流程检查保持不变)

        String persona;
        List<ToolDefinition> toolsToUse;

        // 【关键修复】检查策略总开关
        if (!configService.getEnableStrategy()) {
            // Path 1: 策略已禁用 - 直接调用主模型
            this.lastDecisionProcess = null;
            persona = buildDynamicPersona("1", null);
            toolsToUse = Collections.emptyList();
            log.warn("策略/预处理功能已禁用。跳过意图分析，直接使用默认人设调用主模型。");

        } else {
            // Path 2: 策略已启用 - 完整流程

            // --- "思考链" DTO ---
            this.lastDecisionProcess = new DecisionProcessInfo();
            long preStartTime = System.currentTimeMillis();

            // 2. --- "小模型" 预处理调用 ---
            PreProcessingResult preResult = preProcessInput(userMessage, this.lastDecisionProcess);
            long preEndTime = System.currentTimeMillis();
            this.lastDecisionProcess.setPreProcessingTimeMs(preEndTime - preStartTime);
            log.info("预处理结果: Emotion={}, Intent={}, Sensitive={}", preResult.getEmotion(), preResult.getIntent(), preResult.isSensitive());

            // 3. --- 检查 敏感词 ---
            if (preResult.isSensitive()) {
                // 【硬拦截】敏感词立即返回
                this.lastDecisionProcess.setSelectedStrategy("敏感词兜底");
                return new ChatCompletion(configService.getSensitiveResponse(), null, this.lastDecisionProcess, buildDynamicPersona("1"));
            }

            // 【核心修改】使用规则引擎代替旧的策略逻辑
            // 4. --- 调用规则引擎 ---
            String strategyPrompt = ruleEngineService.selectBestStrategy(
                    preResult.getIntent(),
                    preResult.getEmotion()
            );

            // 如果规则引擎返回空（即便是“意图不明”也没有匹配到规则），则不设置策略，继续调用主模型
            if (strategyPrompt.isEmpty() && "意图不明".equals(preResult.getIntent())) {
                this.lastDecisionProcess.setSelectedStrategy("意图不明 (无规则匹配)");
            } else {
                this.lastDecisionProcess.setSelectedStrategy(strategyPrompt);
            }

            // 5. --- 动态筛选出激活的 Tools ---
            toolsToUse = this.allTools.stream()
                    .filter(tool -> {
                        String toolName = tool.getFunction().getName();
                        String configKey = "enable_tool_" + toolName;
                        String isActiveStr = configService.getGlobalSetting(configKey, "false");
                        return "true".equalsIgnoreCase(isActiveStr);
                    })
                    .collect(Collectors.toList());

            // 映射 IntentKey 到 ToolName（简化处理）
            String compareToolName = "compareTwoPlans";
            String faqToolName = "queryMcpFaq";

            // 【核心逻辑】如果规则引擎选中的策略是一个工具，但该工具被禁用了，覆盖策略
            if (strategyPrompt.contains(compareToolName) && toolsToUse.stream().noneMatch(t -> t.getFunction().getName().equals(compareToolName))) {
                strategyPrompt = "由于工具 " + compareToolName + " 已禁用，请直接用文本回复。";
            }
            if (strategyPrompt.contains(faqToolName) && toolsToUse.stream().noneMatch(t -> t.getFunction().getName().equals(faqToolName))) {
                strategyPrompt = "由于工具 " + faqToolName + " 已禁用，请直接用文本回复。";
            }

            persona = buildDynamicPersona("1", strategyPrompt);
        }

        // 6. --- "主模型"调用 (合并路径 1 和 2) ---
        ModelParameters mainParams = configService.getModelParams(ConfigService.KEY_MAIN_MODEL);
        String modelName = mainParams.getModelName();
        var parameters = mainParams.getParametersAsMap();
        String openingMonologue = configService.getOpeningMonologue();

        long llm1StartTime = System.currentTimeMillis();
        LlmResponse result = getLlmService(modelName).chat(getSessionId(), userMessage, modelName, persona, openingMonologue, parameters, toolsToUse);
        long llm1EndTime = System.currentTimeMillis();
        long llmFirstCallTime = llm1EndTime - llm1StartTime;
        log.info("【LLM主调用耗时】: {} 毫秒", llmFirstCallTime);

        log.info("【LLM原始响应】\n{}", result.getContent());
        String finalContent;
        ToolCallInfo toolCallInfo = null;
        if (result.hasToolCalls()) {
            return handleToolCalls(result, modelName, parameters, toolsToUse, llmFirstCallTime, this.lastDecisionProcess, persona);
        } else {
            finalContent = result.getContent();
        }

        processResponseKeywords(finalContent);

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        // 【UX 修复】将 LLM 响应耗时包装在 span 中
        String finalReply = finalContent + "<span class='llm-time-meta'>(LLM 响应耗时: " + responseTime + " 毫秒)</span>";
        return new ChatCompletion(finalReply, toolCallInfo, this.lastDecisionProcess, persona);
    }

    // --- 【重构】preProcessInput 方法 ---
    private PreProcessingResult preProcessInput(String userMessage, DecisionProcessInfo decisionProcess) {
        String tempSessionId = getSessionId() + "_preprocessing";

        // 【新增】根据情绪识别开关，动态选择 Prompt
        String fullPrompt;
        if (configService.getEnableEmotionRecognition()) {
            // 1. 包含情绪、意图、敏感词
            String basePrompt = configService.getPreProcessingPrompt();
            fullPrompt = basePrompt + "\n输入: \"" + userMessage + "\"";
        } else {
            // 2. 仅包含意图、敏感词
            log.info("情绪识别已禁用，使用仅意图分析的 Prompt。");
            String simplifiedPrompt = """
                你是一个专门用于分析用户输入的小模型。
                请严格按照 JSON 格式输出分析结果，不需要任何解释或额外文字。
                
                分析结果必须包含两个字段：
                1. "intent": 识别用户的意图。可选值：比较套餐, 查询FAQ, 有升级意向, 用户抱怨, 闲聊, 意图不明。
                2. "is_sensitive": 判断用户输入是否包含敏感词。可选值："true" 或 "false"。
                
                示例输出:
                { "intent": "意图不明", "is_sensitive": "false" }
                
                请对下面的用户输入进行分析：
                """;
            fullPrompt = simplifiedPrompt + "\n输入: \"" + userMessage + "\"";
        }


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

            // 如果禁用了情绪识别，JSON 中不会返回 emotion 字段，默认为 null
            if (result.getEmotion() == null) {
                result.setEmotion("N/A (已禁用)");
            }

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
                // 【新代码 - 正确】
                // 直接返回 ToolService 的结果
                try {
                    return toolService.compareTwoPlans(args.get("planName1").asText(), args.get("planName2").asText());
                } catch (Exception e) {
                    log.error("调用 PlanService compareTwoPlans 失败", e);
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

        // 【新增】注入安全红线
        String redlines = configService.getSafetyRedlines();
        if (redlines != null && !redlines.isEmpty()) {
            finalPersona += "\n\n--- 安全红线 (绝对禁止) ---\n" +
                    "你绝对不允许在回复中说出以下任何词汇或短语：\n" +
                    redlines;
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