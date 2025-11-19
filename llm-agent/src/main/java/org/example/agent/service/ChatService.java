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

    // 流程完成检测正则
    private static final Pattern PROCESS_COMPLETE_PATTERN =
            Pattern.compile("我已完成流程\\[(?:.*[—→>]\\s*)?([^\\]]+)\\]");

    private final LlmServiceManager llmServiceManager;
    private final ProcessManager processManager;
    private final ConfigService configService;
    private final HistoryService historyService;
    private final HttpSession httpSession;
    private final ToolService toolService;
    private final RuleEngineService ruleEngineService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 【修改】移除了 private List<ToolDefinition> allTools; 字段，改为运行时动态加载
    private int silentCount = 0;
    private DecisionProcessInfo lastDecisionProcess;

    // 聊天结果 DTO
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
     * 初始化：加载流程 (不再初始化硬编码工具列表)
     */
    @PostConstruct
    public void init() {
        this.processManager.updateProcesses(configService.getProcessList());
        // 【修改】不再初始化 this.allTools，改为运行时加载
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
     * 处理用户消息的核心方法 (V3.5 - 逻辑解耦 + 动态工具描述)
     */
    public ChatCompletion processUserMessage(String userMessage) throws IOException {
        long startTime = System.currentTimeMillis();

        // 0. 获取功能开关状态
        boolean enableWorkflow = configService.getEnableWorkflow();
        boolean enableStrategy = configService.getEnableStrategy(); // 控制规则/情绪/敏感词
        boolean enableMcp = configService.getEnableMcp();           // 控制工具/高速通道

        // 1. --- 手动回复检查 (打断、空格) ---
        boolean isInterrupted = userMessage != null && userMessage.contains("打断");
        boolean isSpaceMessage = " ".equals(userMessage);

        String personaForUiUpdate;

        if (isInterrupted) {
            log.info("检测到用户输入'打断'，执行手动回复。");
            personaForUiUpdate = buildDynamicPersona("2", null, null, enableWorkflow);
            return new ChatCompletion("您请说，", null, null, personaForUiUpdate);
        }

        if (isSpaceMessage) {
            log.info("检测到用户输入'空格'，执行沉默回复逻辑。");
            personaForUiUpdate = buildDynamicPersona("3", null, null, enableWorkflow);
            silentCount++;
            String manualReply;
            if (silentCount >= 4) {
                if (enableWorkflow) forceCompleteAllProcesses();
                silentCount = 0;
                manualReply = "好的，先不打扰您了，礼貌起见请您先挂机，祝您生活愉快，再见！";
            } else {
                List<String> cannedResponses = Arrays.asList("喂，您好，能听到说话么？", "我这边是中国移动流量卡渠道商的，能听到说话么？", "喂？您好，这边听不到您的声音，是信号不好吗？");
                manualReply = cannedResponses.get(Math.min(silentCount - 1, cannedResponses.size() - 1));
            }
            // 手动添加历史记录
            try {
                getLlmServiceForMainModel().addMessagesToHistory(getSessionId(),
                        LlmMessage.builder().role(LlmMessage.Role.USER).content(userMessage).build(),
                        LlmMessage.builder().role(LlmMessage.Role.ASSISTANT).content(manualReply).build());
            } catch (Exception e) {
                log.error("手动添加会话历史失败", e);
            }
            return new ChatCompletion(manualReply, null, null, personaForUiUpdate);
        }
        silentCount = 0;

        // 【流程完成检查修正】只有当流程启用，且总流程列表不为空，且未完成列表为空时，才报告完成。
        if (enableWorkflow) {
            if (!processManager.getAllProcesses().isEmpty() && processManager.getUnfinishedProcesses().isEmpty()) {
                String defaultPersona = buildDynamicPersona("1", null, null, true);
                return new ChatCompletion("🎉 恭喜！所有流程均已完成！", null, null, defaultPersona);
            }
        }

        String persona;
        List<ToolDefinition> toolsToUse = Collections.emptyList();
        this.lastDecisionProcess = new DecisionProcessInfo();
        PreProcessingResult strategyResult = new PreProcessingResult();
        PreProcessingResult routerResult = new PreProcessingResult();


        // 2. --- 智能大脑 (预处理) ---

        // 2.1 [策略预处理] (只要策略开启，就分析意图/情绪/敏感词)
        if (enableStrategy) {
            long preStartTime = System.currentTimeMillis();
            strategyResult = runStrategyPreProcess(userMessage, this.lastDecisionProcess);
            this.lastDecisionProcess.setPreProcessingTimeMs(System.currentTimeMillis() - preStartTime);
            log.info("策略预处理结果: Intent={}, Emotion={}, Sensitive={}",
                    strategyResult.getIntent(), strategyResult.getEmotion(), strategyResult.isSensitive());
        } else {
            // 记录策略未启用
            this.lastDecisionProcess.setDetectedIntent("策略未启用");
        }


        // 3. --- 分支逻辑处理 ---

        // 3.1 [敏感词拦截] (仅当策略开启且敏感词触发时生效)
        if (enableStrategy && strategyResult.isSensitive()) {
            this.lastDecisionProcess.setSelectedStrategy("敏感词兜底");
            long totalTime = System.currentTimeMillis() - startTime;
            long strategyTime = (this.lastDecisionProcess.getPreProcessingTimeMs() != null) ? this.lastDecisionProcess.getPreProcessingTimeMs() : 0;
            String sensitiveReply = configService.getSensitiveResponse() +
                    buildTimeBadges(strategyTime, 0, totalTime);

            return new ChatCompletion(sensitiveReply, null, this.lastDecisionProcess,
                    buildDynamicPersona("1", null, null, enableWorkflow));
        }

        // 3.2 [工具路由预处理] (仅当MCP开启时，运行 Router Model 尝试高速通道)
        if (enableMcp) {
            long routerStartTime = System.currentTimeMillis();
            routerResult = runMcpRouterProcess(userMessage, this.lastDecisionProcess);
            long routerTime = System.currentTimeMillis() - routerStartTime;

            // 累加预处理时间 (如果策略启用了，就加上策略时间)
            Long currentPreTime = this.lastDecisionProcess.getPreProcessingTimeMs();
            this.lastDecisionProcess.setPreProcessingTimeMs(
                    currentPreTime != null ? currentPreTime + routerTime : routerTime
            );

            log.info("路由预处理结果: ToolName={}, ToolArgs={}",
                    routerResult.getToolName(), routerResult.getToolArgs());

            // 检查是否触发高速通道
            if (routerResult.hasDirectToolCall()) {
                log.info("🚀 触发高速通道: 路由模型直接指派工具 [{}]", routerResult.getToolName());

                long toolStart = System.currentTimeMillis();
                JsonNode argsNode;
                try {
                    // 使用 routerResult 的 toolArgs
                    argsNode = objectMapper.readTree(routerResult.getToolArgs());
                } catch (Exception e) {
                    log.error("高速通道参数解析失败", e);
                    argsNode = objectMapper.createObjectNode();
                }

                String toolResultJson = executeTool(routerResult.getToolName(), argsNode);
                long toolExecTime = System.currentTimeMillis() - toolStart;

                // 欺骗主模型直接总结 (减少Token，提升速度)
                String summaryPrompt = "用户意图需要调用工具 '" + routerResult.getToolName() + "'。\n" +
                        "工具执行结果如下：\n" + toolResultJson + "\n\n" +
                        "请根据上述数据，用亲切、专业的口吻回答用户的问题。";

                // 使用 strategyResult 的 intent (如果策略开启)
                String intentForPersona = enableStrategy ? strategyResult.getIntent() : null;
                String fastTrackPersona = buildDynamicPersona("1", null, intentForPersona, enableWorkflow) +
                        "\n\n【关键数据】\n" + summaryPrompt;

                long llmStart = System.currentTimeMillis();

                // 调用主模型 (只生成文本，不挂载工具)
                ModelParameters mainParams = configService.getModelParams(ConfigService.KEY_MAIN_MODEL);
                LlmResponse finalRes = getLlmService(mainParams.getModelName()).chat(
                        getSessionId(),
                        userMessage,
                        mainParams.getModelName(),
                        fastTrackPersona,
                        null,
                        mainParams.getParametersAsMap(),
                        null  // 不传 tools
                );
                long llmTime = System.currentTimeMillis() - llmStart;

                // 构造返回结果
                long totalTime = System.currentTimeMillis() - startTime;
                ToolCallInfo fastToolInfo = new ToolCallInfo(routerResult.getToolName(), routerResult.getToolArgs(), toolResultJson, toolExecTime, 0L, llmTime);

                long strategyTime = (this.lastDecisionProcess.getPreProcessingTimeMs() != null) ? this.lastDecisionProcess.getPreProcessingTimeMs() : 0;

                // 高速通道特殊标记 + 时间标签
                String finalReply = finalRes.getContent() +
                        buildTimeBadges(strategyTime, toolExecTime, totalTime) +
                        " <span style='font-size:10px; color:#ff9800;'>(极速模式)</span>";

                return new ChatCompletion(finalReply, fastToolInfo, this.lastDecisionProcess, fastTrackPersona);
            }
        }


        // 3.3 [策略规则匹配] (意图/情绪分析结果用于策略匹配，更新人设)
        String strategyPrompt = "";
        String finalIntent = enableStrategy ? strategyResult.getIntent() : "N/A";

        if (enableStrategy) {
            // 规则引擎匹配 (意图 + 情绪)
            strategyPrompt = ruleEngineService.selectBestStrategy(finalIntent, strategyResult.getEmotion());
            this.lastDecisionProcess.setSelectedStrategy(strategyPrompt.isEmpty() ? "无匹配规则" : strategyPrompt);
        } else {
            // 如果策略没开
            this.lastDecisionProcess.setSelectedStrategy("策略未启用");
        }

        // 构建最终人设 (包含流程、策略指令)
        persona = buildDynamicPersona("1", strategyPrompt, finalIntent, enableWorkflow);


        // 4. --- 常规路径准备 ---

        // 【修改】动态加载工具描述和可用工具列表
        Map<String, String> customDescriptions = configService.getAllToolDescriptions();
        List<ToolDefinition> customizedAllTools = TelecomToolFactory.getAllToolDefinitions(customDescriptions);

        // 筛选工具 (仅当MCP开启时)
        if (enableMcp) {
            toolsToUse = customizedAllTools.stream()
                    .filter(tool -> {
                        String configKey = "enable_tool_" + tool.getFunction().getName();
                        // 【注意】这里必须用 configService.getGlobalSetting() 来获取开关状态
                        return "true".equalsIgnoreCase(configService.getGlobalSetting(configKey, "false"));
                    })
                    .collect(Collectors.toList());
        } else {
            log.info("MCP模块已禁用 (enable_mcp=false)，不挂载工具。");
            toolsToUse = Collections.emptyList();
        }

        // 5. --- 主模型调用 (常规路径) ---
        ModelParameters mainParams = configService.getModelParams(ConfigService.KEY_MAIN_MODEL);
        String modelName = mainParams.getModelName();
        var parameters = mainParams.getParametersAsMap();
        String openingMonologue = configService.getOpeningMonologue();

        long llm1StartTime = System.currentTimeMillis();
        LlmResponse result = getLlmService(modelName).chat(getSessionId(), userMessage, modelName, persona, openingMonologue, parameters, toolsToUse);
        long llmFirstCallTime = System.currentTimeMillis() - llm1StartTime;
        log.info("【LLM主调用耗时】: {} ms", llmFirstCallTime);

        // 6. --- 处理常规工具调用 (常规慢速路径) ---
        if (result.hasToolCalls()) {
            return handleToolCalls(result, modelName, parameters, toolsToUse, llmFirstCallTime, this.lastDecisionProcess, persona, startTime, enableWorkflow);
        }

        // 7. --- 结束 (普通对话) ---
        if (enableWorkflow) {
            processResponseKeywords(result.getContent());
        }

        // 计算总耗时
        long totalTime = System.currentTimeMillis() - startTime;
        long strategyTime = (this.lastDecisionProcess.getPreProcessingTimeMs() != null) ? this.lastDecisionProcess.getPreProcessingTimeMs() : 0;
        String finalReply = result.getContent() + buildTimeBadges(strategyTime, 0, totalTime);

        return new ChatCompletion(finalReply, null, this.lastDecisionProcess, persona);
    }

    /**
     * 预处理：调用【策略模型】分析意图、情绪、敏感词
     */
    private PreProcessingResult runStrategyPreProcess(String userMessage, DecisionProcessInfo decisionProcess) {
        String tempSessionId = getSessionId() + "_strategy";

        String prompt = configService.getPreProcessingPrompt(); // KEY_PRE_PROMPT
        if (prompt == null || prompt.isEmpty()) {
            prompt = "分析用户意图(intent, emotion, is_sensitive)。请只输出JSON格式。";
        }
        prompt += "\n输入: \"" + userMessage + "\"";

        ModelParameters preParams = configService.getModelParams(ConfigService.KEY_PRE_MODEL);
        String modelName = preParams.getModelName();

        LlmService llmService;
        try {
            llmService = getLlmService(modelName);
        } catch (Exception e) {
            log.error("获取策略模型 '{}' 失败，回退到主模型。", modelName);
            ModelParameters mainParams = configService.getModelParams(ConfigService.KEY_MAIN_MODEL);
            modelName = mainParams.getModelName();
            llmService = getLlmService(modelName);
        }
        decisionProcess.setPreProcessingModel("Strategy:" + modelName);

        try {
            LlmResponse preResponse = llmService.chat(tempSessionId, userMessage, modelName, prompt, null, preParams.getParametersAsMap(), null);
            llmService.popConversationHistory(tempSessionId);

            String jsonResponse = cleanLlmResponse(preResponse.getContent());
            PreProcessingResult result = objectMapper.readValue(jsonResponse, PreProcessingResult.class);

            if (result.getEmotion() == null) result.setEmotion("中性");
            if (result.getIntent() == null) result.setIntent("闲聊");
            if (result.getIsSensitive() == null) result.setIsSensitive("false");

            decisionProcess.setDetectedEmotion(result.getEmotion());
            decisionProcess.setDetectedIntent(result.getIntent());
            decisionProcess.setIsSensitive(result.isSensitive());

            return result;
        } catch (Exception e) {
            log.error("策略预处理失败", e);
            PreProcessingResult fallback = new PreProcessingResult();
            fallback.setIntent("意图不明");
            fallback.setIsSensitive("false");
            fallback.setEmotion("中性");
            decisionProcess.setDetectedIntent("意图不明 (解析失败)");
            return fallback;
        }
    }

    /**
     * 新增方法：调用【路由模型】分析工具调用
     */
    private PreProcessingResult runMcpRouterProcess(String userMessage, DecisionProcessInfo decisionProcess) {
        String tempSessionId = getSessionId() + "_router";

        String prompt = configService.getRouterProcessingPrompt(); // KEY_ROUTER_PROMPT
        if (prompt == null || prompt.isEmpty()) {
            prompt = "分析用户是否需要调用工具(tool_name, tool_args)。请只输出JSON格式。";
        }
        prompt += "\n输入: \"" + userMessage + "\"";

        ModelParameters routerParams = configService.getModelParams(ConfigService.KEY_ROUTER_MODEL);
        String modelName = routerParams.getModelName();

        LlmService llmService;
        try {
            llmService = getLlmService(modelName);
        } catch (Exception e) {
            log.error("获取路由模型 '{}' 失败，回退到主模型。", modelName);
            ModelParameters mainParams = configService.getModelParams(ConfigService.KEY_MAIN_MODEL);
            modelName = mainParams.getModelName();
            llmService = getLlmService(modelName);
        }

        // 更新决策模型信息，追加路由模型信息
        String currentPreModel = decisionProcess.getPreProcessingModel();
        if (currentPreModel == null || currentPreModel.contains("策略未启用")) {
            decisionProcess.setPreProcessingModel("Router:" + modelName);
        } else {
            // 如果策略模型运行了，则显示两个模型
            decisionProcess.setPreProcessingModel(currentPreModel + " & Router:" + modelName);
        }

        try {
            LlmResponse routerResponse = llmService.chat(tempSessionId, userMessage, modelName, prompt, null, routerParams.getParametersAsMap(), null);
            llmService.popConversationHistory(tempSessionId);

            String jsonResponse = cleanLlmResponse(routerResponse.getContent());

            // ObjectMapper.readValue 依赖 PreProcessingResult 包含 tool_name/tool_args 字段
            PreProcessingResult result = objectMapper.readValue(jsonResponse, PreProcessingResult.class);

            // 补充处理如果模型返回 null/空字符串/无 tool_name 的情况
            if (result.getToolName() == null || result.getToolName().isEmpty() || "null".equalsIgnoreCase(result.getToolName())) {
                result.setToolName(null);
            }
            if (result.getToolArgs() == null || result.getToolArgs().isEmpty() || "null".equalsIgnoreCase(result.getToolArgs())) {
                result.setToolArgs("{}");
            }

            return result;
        } catch (Exception e) {
            log.error("路由预处理失败", e);
            PreProcessingResult fallback = new PreProcessingResult();
            fallback.setToolName(null);
            fallback.setToolArgs("{}");
            return fallback;
        }
    }

    /**
     * 辅助方法：清理LLM返回的JSON字符串
     */
    private String cleanLlmResponse(String rawResponse) {
        if (rawResponse == null) return "{}";
        String jsonResponse = rawResponse;
        if (jsonResponse.contains("```json")) {
            jsonResponse = jsonResponse.substring(jsonResponse.indexOf('{'), jsonResponse.lastIndexOf('}') + 1);
        } else if (jsonResponse.contains("{")) {
            int s = jsonResponse.indexOf("{");
            int e = jsonResponse.lastIndexOf("}");
            if(s >= 0 && e > s) jsonResponse = jsonResponse.substring(s, e + 1);
        }
        return jsonResponse;
    }

    /**
     * 处理工具调用 (常规路径)
     */
    private ChatCompletion handleToolCalls(LlmResponse result, String modelName, Map<String, Object> parameters, List<ToolDefinition> tools,
                                           long llmFirstCallTime, DecisionProcessInfo decisionProcessInfo, String personaUsedInFirstCall,
                                           long startTime, boolean enableWorkflow) {
        LlmToolCall toolCall = result.getToolCalls().get(0);
        String toolName = toolCall.getToolName();
        String toolArgsString = toolCall.getArguments();
        log.info("LLM调用工具: {}, 参数: {}", toolName, toolArgsString);

        JsonNode toolArgs;
        try {
            toolArgs = objectMapper.readTree(toolArgsString);
        } catch (JsonProcessingException e) {
            return new ChatCompletion("工具参数错误", null, decisionProcessInfo, personaUsedInFirstCall);
        }

        long toolStart = System.currentTimeMillis();
        String toolResultContent = executeTool(toolName, toolArgs);
        long toolExecutionTime = System.currentTimeMillis() - toolStart;

        LlmMessage toolResultMessage = LlmMessage.builder()
                .role(LlmMessage.Role.TOOL)
                .content(toolResultContent)
                .toolCallId(toolCall.getId())
                .build();

        long llm2Start = System.currentTimeMillis();
        LlmResponse finalResult = getLlmService(modelName).chatWithToolResult(getSessionId(), modelName, parameters, tools, toolResultMessage);
        long llmSecondCallTime = System.currentTimeMillis() - llm2Start;

        ToolCallInfo toolCallInfo = new ToolCallInfo(toolName, toolArgsString, toolResultContent, toolExecutionTime, llmFirstCallTime, llmSecondCallTime);

        if (enableWorkflow) {
            processResponseKeywords(finalResult.getContent());
        }

        long totalTime = System.currentTimeMillis() - startTime;
        long strategyTime = (decisionProcessInfo != null && decisionProcessInfo.getPreProcessingTimeMs() != null)
                ? decisionProcessInfo.getPreProcessingTimeMs() : 0;

        String finalReply = finalResult.getContent() + buildTimeBadges(strategyTime, toolExecutionTime, totalTime);

        return new ChatCompletion(finalReply, toolCallInfo, decisionProcessInfo, personaUsedInFirstCall);
    }

    /**
     * 生成纯文字时间标签 (无图标)
     */
    private String buildTimeBadges(long strategyTime, long toolTime, long totalTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='message-meta-container'>");

        if (strategyTime > 0) {
            sb.append(String.format("<span class='time-badge badge-strategy'>预处理: %dms</span>", strategyTime)); // 统一显示为预处理
        }

        if (toolTime > 0) {
            sb.append(String.format("<span class='time-badge badge-mcp'>MCP调用: %dms</span>", toolTime));
        }

        sb.append(String.format("<span class='time-badge badge-total'>总响应: %dms</span>", totalTime));

        sb.append("</div>");
        return sb.toString();
    }

    /**
     * 执行工具逻辑
     */
    private String executeTool(String toolName, JsonNode args) {
        try {
            switch (toolName) {
                case "compareTwoPlans":
                    return toolService.compareTwoPlans(args.path("planName1").asText(), args.path("planName2").asText());
                case "queryMcpFaq":
                    return toolService.queryMcpFaq(args.path("intent").asText());
                case "getWeather":
                    // 假设 getWeather(city, date) 优先
                    if(args.has("date") && !args.path("date").asText().isEmpty()) {
                        return toolService.getWeather(args.path("city").asText(), args.path("date").asText());
                    }
                    return toolService.getWeather(args.path("city").asText());
                case "getOilPrice":
                    return toolService.getOilPrice(args.path("province").asText());
                case "getGoldPrice":
                    return toolService.getGoldPrice();
                case "getNews":
                    return toolService.getNews(args.path("areaName").asText(), args.path("title").asText());
                case "getExchangeRate":
                    return toolService.getExchangeRate(args.path("currency").asText());
                case "getFundInfo":
                    return toolService.getFundInfo(args.path("fundCode").asText());
                case "getCurrentTimeByCity":
                    return toolService.getCurrentTimeByCity(args.path("city").asText());
                case "getStockInfo":
                    return toolService.getStockInfo(args.path("symbol").asText());
                case "webSearch":
                    int count = args.has("count") && args.get("count").isInt() ? args.get("count").asInt() : 5;
                    return toolService.webSearch(args.path("query").asText(), count);
                default:
                    return "{\"error\": \"未知工具: " + toolName + "\"}";
            }
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolName, e);
            return "{\"error\": \"工具执行失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }

    private String buildDynamicPersona(String codeValue, String strategyPrompt, String finalIntent, boolean enableWorkflow) {
        String personaTemplate = configService.getPersonaTemplate();
        String persona = personaTemplate.replace("{code}", codeValue);

        if (enableWorkflow) {
            String availableTasksStr = sanitizeProcessNames(getAvailableProcesses()).stream().collect(Collectors.joining("→"));
            String workflowStr = sanitizeProcessNames(configService.getProcessList()).stream().collect(Collectors.joining(" → "));
            persona = persona.replace("{tasks}", availableTasksStr.isEmpty() ? "无" : availableTasksStr)
                    .replace("{workflow}", workflowStr);
        } else {
            persona = persona.replace("{tasks}", "自由对话").replace("{workflow}", "自由对话");
        }

        if (strategyPrompt != null && !strategyPrompt.isEmpty()) {
            persona += "\n\n--- 必须执行的指令 ---\n" + strategyPrompt;
        }

        if (finalIntent != null && Set.of("比较套餐", "查询FAQ", "查询天气", "联网搜索").contains(finalIntent)) {
            persona += "\n\n--- 提示 ---\n检测到意图 '" + finalIntent + "'，请优先使用对应工具回答。";
        }

        String redlines = configService.getSafetyRedlines();
        if (redlines != null && !redlines.isEmpty()) {
            persona += "\n\n--- 沟通禁忌 ---\n" + redlines;
        }

        return persona;
    }

    private void processResponseKeywords(String llmResponse) {
        if (llmResponse == null || llmResponse.isEmpty()) return;
        Matcher matcher = PROCESS_COMPLETE_PATTERN.matcher(llmResponse);
        if (matcher.find()) {
            String targetProcessName = matcher.group(1).trim();
            getAvailableProcesses().stream()
                    .filter(process -> sanitizeProcessName(process).equals(targetProcessName))
                    .findFirst()
                    .ifPresent(processManager::completeProcess);
        }
    }

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

    private void forceCompleteAllProcesses() {
        configService.getProcessList().forEach(processManager::completeProcess);
    }

    public UiState getCurrentUiState(String actualPersonaUsed) {
        Map<String, String> statuses = processManager.getAllProcesses().stream()
                .collect(Collectors.toMap(p -> p, p -> processManager.getUnfinishedProcesses().contains(p) ? "PENDING" : "COMPLETED", (v1, v2) -> v1, LinkedHashMap::new));
        return new UiState(statuses, actualPersonaUsed, null);
    }

    public UiState getInitialUiState() {
        String openingMonologue = configService.getOpeningMonologue();
        boolean enableWorkflow = configService.getEnableWorkflow();
        String persona = buildDynamicPersona("1", null, null, enableWorkflow);

        Map<String, String> statuses = processManager.getAllProcesses().stream()
                .collect(Collectors.toMap(p -> p, p -> processManager.getUnfinishedProcesses().contains(p) ? "PENDING" : "COMPLETED", (v1, v2) -> v1, LinkedHashMap::new));

        return new UiState(statuses, persona, openingMonologue);
    }

    public void resetProcessesAndSaveHistory() {
        saveHistory(getLlmServiceForMainModel().popConversationHistory(getSessionId()));
        processManager.updateProcesses(configService.getProcessList());
        this.silentCount = 0;
    }

    public void saveHistoryOnExit() {
        saveHistory(getLlmServiceForMainModel().getConversationHistory(getSessionId()));
    }

    private void saveHistory(List<LlmMessage> history) {
        // historyService.saveConversationToFile("", history);
    }

    private Map<String, List<String>> parseAndSetDependencies(String dependencies) {
        Map<String, List<String>> rules = new HashMap<>();
        if (dependencies == null || dependencies.trim().isEmpty()) return rules;

        for (String line : dependencies.split("\\r?\\n")) {
            if (!line.contains("->")) continue;
            String[] parts = line.split("->");
            if (parts.length < 2) continue;
            String process = findProcessByName(parts[0].trim());
            if (process == null) continue;
            List<String> prerequisites = Arrays.stream(parts[1].split(","))
                    .map(String::trim).map(this::findProcessByName)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            if (!prerequisites.isEmpty()) rules.put(process, prerequisites);
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

    private String sanitizeProcessName(String processName) {
        String name = processName.trim();
        return name.endsWith("*") ? name.substring(0, name.length() - 1).replaceAll("^\\d+\\.?\\s*", "").trim() : name.replaceAll("^\\d+\\.?\\s*", "").trim();
    }

    private List<String> sanitizeProcessNames(List<String> processNames) {
        return processNames.stream().map(this::sanitizeProcessName).collect(Collectors.toList());
    }
}