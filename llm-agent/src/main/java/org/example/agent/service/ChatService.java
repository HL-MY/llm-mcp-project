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

    // 将流程完成的正则表达式定义为常量
    private static final Pattern PROCESS_COMPLETE_PATTERN =
            Pattern.compile("我已完成流程\\[(?:.*[—→>]\\s*)?([^\\]]+)\\]");

    private final LlmServiceManager llmServiceManager;
    private final ProcessManager processManager;
    private final ConfigService configService;
    private final HistoryService historyService;
    private final HttpSession httpSession;
    private final ToolService toolService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<ToolDefinition> allTools; // 存储所有工具定义
    private int silentCount = 0;

    private DecisionProcessInfo lastDecisionProcess;

    private final RuleEngineService ruleEngineService; // 注入规则引擎

    public static record ChatCompletion(String reply, ToolCallInfo toolCallInfo, DecisionProcessInfo decisionProcessInfo, String personaUsed) {}

    // 构造函数
    public ChatService(LlmServiceManager llmServiceManager, ProcessManager processManager,
                       ConfigService configService,
                       HistoryService historyService, HttpSession httpSession, ToolService toolService,
                       RuleEngineService ruleEngineService) {
        this.llmServiceManager = llmServiceManager;
        this.processManager = processManager;
        this.configService = configService;
        this.historyService = historyService;
        this.httpSession = httpSession;
        this.toolService = toolService;
        this.ruleEngineService = ruleEngineService;
    }


    /**
     * @PostConstruct 初始化方法：加载流程和工具定义
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

    /**
     * 处理用户消息的核心方法，包含预处理、策略选择和LLM调用。
     * @param userMessage 用户输入的消息
     * @return 聊天完成结果 (回复、工具信息、决策信息)
     * @throws IOException
     */
    public ChatCompletion processUserMessage(String userMessage) throws IOException {
        long startTime = System.currentTimeMillis();

        // 1. --- 手动回复检查 (打断、空格) ---
        boolean isInterrupted = userMessage != null && userMessage.contains("打断");
        boolean isSpaceMessage = " ".equals(userMessage);

        String personaForUiUpdate;

        if (isInterrupted) {
            log.info("检测到用户输入'打断'，执行手动回复，不调用LLM。");
            personaForUiUpdate = buildDynamicPersona("2", null, null);
            String manualReply = "您请说，";
            return new ChatCompletion(manualReply, null, null, personaForUiUpdate);
        }

        if (isSpaceMessage) {
            log.info("检测到用户输入'空格'，使用 code=3 并执行手动回复，不调用LLM。");
            personaForUiUpdate = buildDynamicPersona("3", null, null);
            silentCount++;
            String manualReply;
            if (silentCount >= 4) {
                // 连续多次沉默，强制结束所有流程并挂机
                forceCompleteAllProcesses();
                silentCount = 0;
                manualReply = "好的，先不打扰您了，礼貌起见请您先挂机，祝您生活愉快，再见！";
            } else {
                List<String> cannedResponses = Arrays.asList("喂，您好，能听到说话么？", "我这边是中国移动流量卡渠道商的，能听到说话么？", "喂？您好，这边听不到您的声音，是信号不好吗？");
                manualReply = cannedResponses.get(silentCount - 1);
            }
            // 将'空格'和'沉默回复'添加到会话历史
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

        // 检查是否有可用流程，如果没有则回复完成信息
        if (getAvailableProcesses().isEmpty() && processManager.getUnfinishedProcesses().isEmpty()) {
            String defaultPersona = buildDynamicPersona("1", null, null);
            return new ChatCompletion("🎉 恭喜！所有流程均已完成！", null, null, defaultPersona);
        }

        log.info("检测到正常消息，重置 silentCount 并开始预处理。");
        silentCount = 0;

        String persona;
        List<ToolDefinition> toolsToUse;
        String finalIntent = "N/A";

        // 检查策略总开关
        if (!configService.getEnableStrategy()) {
            // Path 1: 策略已禁用 - 直接调用主模型
            this.lastDecisionProcess = null;
            persona = buildDynamicPersona("1", null, null);
            toolsToUse = Collections.emptyList();
            log.warn("策略/预处理功能已禁用。跳过意图分析，直接使用默认人设调用主模型。");

        } else {
            // Path 2: 策略已启用 - 完整流程

            // 初始化决策过程信息
            this.lastDecisionProcess = new DecisionProcessInfo();
            long preStartTime = System.currentTimeMillis();

            // 2. --- "小模型" 预处理调用 (意图、情绪、敏感词) ---
            PreProcessingResult preResult = preProcessInput(userMessage, this.lastDecisionProcess);
            long preEndTime = System.currentTimeMillis();
            this.lastDecisionProcess.setPreProcessingTimeMs(preEndTime - preStartTime);
            log.info("预处理结果: Emotion={}, Intent={}, Sensitive={}", preResult.getEmotion(), preResult.getIntent(), preResult.isSensitive());

            // 3. --- 检查 敏感词 ---
            if (preResult.isSensitive()) {
                this.lastDecisionProcess.setSelectedStrategy("敏感词兜底");
                return new ChatCompletion(configService.getSensitiveResponse(), null, this.lastDecisionProcess, buildDynamicPersona("1", null, null));
            }

            // 4. --- 调用规则引擎进行策略选择 ---
            Map<String, String> activeIntentStrategies = configService.getActiveStrategies("INTENT");

            finalIntent = preResult.getIntent();
            // 意图白名单检查
            if (!activeIntentStrategies.containsKey(finalIntent)) {
                log.warn("意图 '{}' 被检测到，但未在激活列表中，回退到 '意图不明'", finalIntent);
                finalIntent = "意图不明";
            }

            // 规则引擎根据意图和情绪选择最佳策略Prompt
            String strategyPrompt = ruleEngineService.selectBestStrategy(
                    finalIntent,
                    preResult.getEmotion()
            );

            // 5. --- 动态筛选出激活的 Tools ---
            toolsToUse = this.allTools.stream()
                    .filter(tool -> {
                        String toolName = tool.getFunction().getName();
                        String configKey = "enable_tool_" + toolName;
                        String isActiveStr = configService.getGlobalSetting(configKey, "false");
                        return "true".equalsIgnoreCase(isActiveStr);
                    })
                    .collect(Collectors.toList());

            // 工具名称常量
            String compareToolName = "compareTwoPlans";
            String faqToolName = "queryMcpFaq";
            String weatherToolName = "getWeather";
            String webSearchToolName = "webSearch";

            // 如果意图是工具调用，但规则库为空，则手动创建指令（防止规则配置缺失）
            if (strategyPrompt.isEmpty()) {
                if (finalIntent.equals("比较套餐") && toolsToUse.stream().anyMatch(t -> t.getFunction().getName().equals(compareToolName))) {
                    strategyPrompt = "用户想比较套餐。请主动调用 compareTwoPlans 工具。";
                } else if (finalIntent.equals("查询FAQ") && toolsToUse.stream().anyMatch(t -> t.getFunction().getName().equals(faqToolName))) {
                    strategyPrompt = "用户在问FAQ。请主动调用 queryMcpFaq 工具。";
                } else if (finalIntent.equals("查询天气") && toolsToUse.stream().anyMatch(t -> t.getFunction().getName().equals(weatherToolName))) {
                    strategyPrompt = "用户想查询天气。请主动调用 getWeather 工具。";
                } else if (finalIntent.equals("联网搜索") && toolsToUse.stream().anyMatch(t -> t.getFunction().getName().equals(webSearchToolName))) {
                    strategyPrompt = "用户想联网搜索。请主动调用 webSearch 工具。";
                } else if ("意图不明".equals(finalIntent)) {
                    this.lastDecisionProcess.setSelectedStrategy("意图不明 (无规则匹配)");
                }
            }

            // 如果规则引擎选中的策略是一个工具，但该工具被禁用了，则覆盖策略为文本回复
            if (strategyPrompt.contains(compareToolName) && toolsToUse.stream().noneMatch(t -> t.getFunction().getName().equals(compareToolName))) {
                strategyPrompt = "由于工具 " + compareToolName + " 已禁用，请直接用文本回复。";
            }
            if (strategyPrompt.contains(faqToolName) && toolsToUse.stream().noneMatch(t -> t.getFunction().getName().equals(faqToolName))) {
                strategyPrompt = "由于工具 " + faqToolName + " 已禁用，请直接用文本回复。";
            }
            if (strategyPrompt.contains(weatherToolName) && toolsToUse.stream().noneMatch(t -> t.getFunction().getName().equals(weatherToolName))) {
                strategyPrompt = "由于工具 " + weatherToolName + " 已禁用，请直接用文本回复。";
            }
            if (strategyPrompt.contains(webSearchToolName) && toolsToUse.stream().noneMatch(t -> t.getFunction().getName().equals(webSearchToolName))) {
                strategyPrompt = "由于工具 " + webSearchToolName + " 已禁用，请直接用文本回复。";
            }

            // 添加情绪策略（如果启用）
            if (configService.getEnableEmotionRecognition()) {
                Map<String, String> activeEmotionStrategies = configService.getActiveStrategies("EMOTION");
                String emotionStrategy = activeEmotionStrategies.getOrDefault(preResult.getEmotion(), "");
                if (emotionStrategy != null && !emotionStrategy.isEmpty()) {
                    strategyPrompt += "\n" + emotionStrategy;
                }
            }

            this.lastDecisionProcess.setSelectedStrategy(strategyPrompt);
            persona = buildDynamicPersona("1", strategyPrompt, finalIntent);
        }

        // 6. --- "主模型"调用 ---
        ModelParameters mainParams = configService.getModelParams(ConfigService.KEY_MAIN_MODEL);
        String modelName = mainParams.getModelName();
        var parameters = mainParams.getParametersAsMap();
        String openingMonologue = configService.getOpeningMonologue();

        long llm1StartTime = System.currentTimeMillis();
        // 第一次调用LLM
        LlmResponse result = getLlmService(modelName).chat(getSessionId(), userMessage, modelName, persona, openingMonologue, parameters, toolsToUse);
        long llm1EndTime = System.currentTimeMillis();
        long llmFirstCallTime = llm1EndTime - llm1StartTime;
        log.info("【LLM主调用耗时】: {} 毫秒", llmFirstCallTime);

        log.info("【LLM原始响应】\n{}", result.getContent());
        String finalContent;
        ToolCallInfo toolCallInfo = null;

        // 检查LLM响应是否包含工具调用
        if (result.hasToolCalls()) {
            // 处理工具调用
            return handleToolCalls(result, modelName, parameters, toolsToUse, llmFirstCallTime, this.lastDecisionProcess, persona);
        } else {
            finalContent = result.getContent();
        }

        // 处理响应中的流程完成关键字
        processResponseKeywords(finalContent);

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        // 将 LLM 响应耗时包装在 span 中
        String finalReply = finalContent + "<span class='llm-time-meta'>(LLM 响应耗时: " + responseTime + " 毫秒)</span>";
        return new ChatCompletion(finalReply, toolCallInfo, this.lastDecisionProcess, persona);
    }

    /**
     * 调用小模型进行用户输入预处理（意图、情绪、敏感词）。
     * @param userMessage 用户输入
     * @param decisionProcess 决策过程信息 DTO
     * @return 预处理结果
     */
    private PreProcessingResult preProcessInput(String userMessage, DecisionProcessInfo decisionProcess) {
        // 使用临时 sessionId 确保预处理模型的历史记录不影响主模型
        String tempSessionId = getSessionId() + "_preprocessing";

        // 根据情绪识别开关，动态选择 Prompt
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
                1. "intent": 识别用户的意图。可选值：比较套餐, 查询FAQ, 有升级意向, 用户抱怨, 闲聊, 意图不明, 查询天气, 联网搜索。
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
            // 如果获取预处理模型失败，回退使用主模型
            log.error("获取预处理模型 '{}' 失败。回退使用主模型。", preProcessorModelName, e);
            ModelParameters mainParams = configService.getModelParams(ConfigService.KEY_MAIN_MODEL);
            llmService = getLlmService(mainParams.getModelName());
            preProcessorModelName = mainParams.getModelName();
            decisionProcess.setPreProcessingModel(preProcessorModelName + " (回退)");
        }

        try {
            // 调用预处理模型
            LlmResponse preResponse = llmService.chat(tempSessionId, userMessage, preProcessorModelName, fullPrompt, null, parameters, null);
            // 清除临时会话历史
            llmService.popConversationHistory(tempSessionId);

            String jsonResponse = preResponse.getContent();
            // 提取 JSON 内容
            if (jsonResponse.contains("```json")) {
                jsonResponse = jsonResponse.substring(jsonResponse.indexOf('{'), jsonResponse.lastIndexOf('}') + 1);
            }

            PreProcessingResult result = objectMapper.readValue(jsonResponse, PreProcessingResult.class);

            if (result.getEmotion() == null) {
                result.setEmotion("N/A (已禁用)");
            }

            // 记录决策信息
            decisionProcess.setDetectedEmotion(result.getEmotion());
            decisionProcess.setDetectedIntent(result.getIntent());
            decisionProcess.setIsSensitive(result.isSensitive());

            return result;

        } catch (Exception e) {
            log.error("预处理调用失败 (模型: {}) 或解析JSON失败", preProcessorModelName, e);
            PreProcessingResult fallbackResult = new PreProcessingResult("中性", "意图不明", "false");
            decisionProcess.setDetectedIntent("意图不明 (解析失败)");
            return fallbackResult;
        }
    }

    /**
     * 强制完成所有流程，用于处理多次沉默或特殊打断情况。
     */
    private void forceCompleteAllProcesses() {
        configService.getProcessList().forEach(processManager::completeProcess);
    }

    /**
     * 处理 LLM 返回的工具调用请求。
     */
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

        // 执行工具
        long toolStartTime = System.currentTimeMillis();
        String toolResultContent = executeTool(toolName, toolArgs);
        long toolEndTime = System.currentTimeMillis();
        long toolExecutionTime = toolEndTime - toolStartTime;
        log.info("【Tool 执行耗时】: {} 毫秒", toolExecutionTime);

        // 将工具执行结果作为消息返回给 LLM
        LlmMessage toolResultMessage = LlmMessage.builder()
                .role(LlmMessage.Role.TOOL)
                .content(toolResultContent)
                .toolCallId(toolCall.getId())
                .build();

        long llm2StartTime = System.currentTimeMillis();
        // 第二次调用 LLM，生成最终回复
        LlmResponse finalResult = getLlmService(modelName).chatWithToolResult(getSessionId(), modelName, parameters, tools, toolResultMessage);
        long llm2EndTime = System.currentTimeMillis();
        long llmSecondCallTime = llm2EndTime - llm2StartTime;
        log.info("【LLM二次调用耗时】: {} 毫秒", llmSecondCallTime);

        ToolCallInfo toolCallInfo = new ToolCallInfo(toolName, toolArgsString, toolResultContent, toolExecutionTime, llmFirstCallTime, llmSecondCallTime);

        log.info("【LLM工具调用后原始响应】\n{}", finalResult.getContent());
        processResponseKeywords(finalResult.getContent());

        return new ChatCompletion(finalResult.getContent(), toolCallInfo, decisionProcessInfo, personaUsedInFirstCall);
    }

    /**
     * 检查 LLM 响应中是否包含流程完成关键字，并更新 ProcessManager 状态。
     * @param llmResponse LLM的回复内容
     */
    private void processResponseKeywords(String llmResponse) {
        if (llmResponse == null || llmResponse.isEmpty()) return;

        Matcher matcher = PROCESS_COMPLETE_PATTERN.matcher(llmResponse);

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

    /**
     * 执行具体的工具调用逻辑。
     * - 提取并传递所有必需的参数
     * @param toolName 工具名称
     * @param args 工具参数 JSON
     * @return 工具执行结果（JSON 字符串）
     */
    private String executeTool(String toolName, JsonNode args) {
        switch (toolName) {
            case "compareTwoPlans":
                try {
                    return toolService.compareTwoPlans(args.get("planName1").asText(), args.get("planName2").asText());
                } catch (Exception e) {
                    log.error("调用 PlanService compareTwoPlans 失败", e);
                    return "{\"error\": \"无法序列化套餐对比结果\"}";
                }
            case "queryMcpFaq":
                return toolService.queryMcpFaq(args.get("intent").asText());

            case "getWeather":
                try {
                    String city = args.get("city").asText();
                    // date 是可选的
                    String date = args.has("date") ? args.get("date").asText() : "today";
                    return toolService.getWeather(city, date);
                } catch (Exception e) {
                    log.error("解析 getWeather 参数(city, date)失败", e);
                    return "{\"error\": \"解析 'getWeather' 参数失败\", \"details\": \"" + e.getMessage() + "\"}";
                }

            case "webSearch":
                try {
                    String query = args.get("query").asText();
                    // count 是可选的
                    Integer count = args.has("count") ? args.get("count").asInt() : 5;
                    return toolService.webSearch(query, count);
                } catch (Exception e) {
                    log.error("解析 webSearch 参数(query, count)失败", e);
                    return "{\"error\": \"解析 'webSearch' 参数失败\", \"details\": \"" + e.getMessage() + "\"}";
                }

            default:
                return "{\"error\": \"未知工具\"}";
        }
    }

    /**
     * 构建动态人设 Prompt。
     * @param codeValue 状态码 (1:正常, 2:打断, 3:沉默)
     * @return 完整的人设 Prompt 字符串
     */
    private String buildDynamicPersona(String codeValue) {
        return buildDynamicPersona(codeValue, null, null);
    }

    private String buildDynamicPersona(String codeValue, String strategyPrompt, String finalIntent) {
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

        // 注入当前可用任务和完整工作流
        String availableTasksStr = sanitizeProcessNames(getAvailableProcesses()).stream().collect(Collectors.joining("→"));
        String workflowStr = sanitizeProcessNames(configService.getProcessList()).stream().collect(Collectors.joining(" → "));

        String finalPersona = personaWithCode
                .replace("{tasks}", availableTasksStr.isEmpty() ? "无" : availableTasksStr)
                .replace("{workflow}", workflowStr);

        // 注入策略指令
        if (strategyPrompt != null && !strategyPrompt.isEmpty()) {
            finalPersona += "\n\n--- 当前策略 ---\n" + strategyPrompt;
        }

        // 注入安全红线
        String redlines = configService.getSafetyRedlines();
        if (redlines != null && !redlines.isEmpty()) {
            finalPersona += "\n\n--- 安全红线 (绝对禁止) ---\n" +
                    "你绝对不允许在回复中说出以下任何词汇或短语：\n" +
                    redlines;
        }

        // 如果意图是工具调用，添加更强的指令，强制 LLM 调用工具
        if (finalIntent != null && (finalIntent.equals("比较套餐") || finalIntent.equals("查询FAQ") || finalIntent.equals("查询天气") || finalIntent.equals("联网搜索"))) {
            finalPersona += "\n\n--- 强制工具调用指令 ---\n" +
                    "检测到意图 '" + finalIntent + "'。如果工具列表中存在相应的工具，你必须调用该工具来回答，不得直接编造答案。";
        }

        return finalPersona;
    }


    /**
     * 获取当前可执行的流程列表，会根据依赖规则进行判断。
     */
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

        // 处理 "始终可用" 的流程 (以 * 结尾)
        completed.stream()
                .filter(task -> task.trim().endsWith("*"))
                .forEach(available::add);

        return allProcesses.stream().filter(available::contains).collect(Collectors.toList());
    }

    /**
     * 清理流程名称，去除序号和特殊标记 (*)。
     */
    private String sanitizeProcessName(String processName) {
        String name = processName.trim();
        return name.endsWith("*") ? name.substring(0, name.length() - 1).replaceAll("^\\d+\\.?\\s*", "").trim() : name.replaceAll("^\\d+\\.?\\s*", "").trim();
    }
    private List<String> sanitizeProcessNames(List<String> processNames) {
        return processNames.stream().map(this::sanitizeProcessName).collect(Collectors.toList());
    }

    /**
     * 获取当前的 UI 状态（流程状态和人设）。
     */
    public UiState getCurrentUiState(String actualPersonaUsed) {
        Map<String, String> statuses = processManager.getAllProcesses().stream()
                .collect(Collectors.toMap(p -> p, p -> processManager.getUnfinishedProcesses().contains(p) ? "PENDING" : "COMPLETED", (v1, v2) -> v1, LinkedHashMap::new));

        // 只返回会话状态
        return new UiState(statuses, actualPersonaUsed, null);
    }

    /**
     * 获取初始 UI 状态（流程状态、初始人设和开场白）。
     */
    public UiState getInitialUiState() {
        String openingMonologue = configService.getOpeningMonologue();
        String persona = buildDynamicPersona("1", null, null);
        Map<String, String> statuses = processManager.getAllProcesses().stream()
                .collect(Collectors.toMap(p -> p, p -> processManager.getUnfinishedProcesses().contains(p) ? "PENDING" : "COMPLETED", (v1, v2) -> v1, LinkedHashMap::new));

        return new UiState(statuses, persona, openingMonologue);
    }

    /**
     * 重置所有流程并保存历史记录。
     */
    public void resetProcessesAndSaveHistory() {
        saveHistory(getLlmServiceForMainModel().popConversationHistory(getSessionId()));
        // 重置时从 ConfigService 重新加载流程
        processManager.updateProcesses(configService.getProcessList());
        this.silentCount = 0;
    }

    /**
     * 在会话结束时保存历史记录。
     */
    public void saveHistoryOnExit() {
        saveHistory(getLlmServiceForMainModel().getConversationHistory(getSessionId()));
    }

    /**
     * 保存会话历史到持久化存储（当前注释）。
     */
    private void saveHistory(List<LlmMessage> history) {
        if (history != null && !history.isEmpty()) {
            // historyService.saveConversationToFile("", history);
        }
    }

    /**
     * 解析流程依赖关系字符串，并构建 Map。
     * 格式: 流程A -> 流程B, 流程C
     */
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

    /**
     * 根据名称或 ID 查找流程的完整名称。
     */
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