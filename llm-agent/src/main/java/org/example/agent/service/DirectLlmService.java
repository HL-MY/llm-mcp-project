package org.example.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agent.dto.ModelParameters;
import org.example.agent.factory.TelecomToolFactory;
import org.example.llm.dto.llm.LlmMessage;
import org.example.llm.dto.llm.LlmResponse;
import org.example.llm.dto.llm.LlmToolCall;
import org.example.llm.dto.tool.ToolDefinition;
import org.example.llm.service.LlmService;
import org.example.llm.service.LlmServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


/**
 * 【新增服务】大模型 MCP 直调服务 (Direct LLM Service)。
 * 负责处理无状态的、强制启用所有 MCP 工具的 LLM 调用。
 * **该服务的所有配置（模型、人设、工具列表）均已硬编码，不依赖 ConfigService。**
 * 【注意】此处直接实现了 Redis 上下文逻辑，以满足用户对代码结构的严格要求。
 */
@Service
public class DirectLlmService {

    private static final Logger log = LoggerFactory.getLogger(DirectLlmService.class);

    private final LlmServiceManager llmServiceManager;
    private final ToolService toolService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RedisTemplate<String, List<LlmMessage>> llmMessageRedisTemplate;
    private static final String CONTEXT_PREFIX = "llm:direct:session:";
    private static final long CONTEXT_TTL_DAYS = 1;

    // --- 【硬编码配置】模型参数 1：工具判断模型 ---
    private static final String FIRST_MODEL_NAME = "qwen-turbo";
    private static final ModelParameters FIRST_PARAMS = new ModelParameters(
            FIRST_MODEL_NAME,
            0.1,
            0.8, // topP: 0.8
            512, // Max Tokens 限制在 512
            null, null, null
    );

    // --- 【硬编码配置】人设 1：工具判断人设 (mcp人设) ---
    private static final String FIRST_PERSONA =
            """
                    你是一个高速工具路由助手。你的任务是严格根据用户需求，判断是否可以直接通过调用工具解决问题，并给出调用指令。
                                
                    请严格遵守以下规则，并只输出 JSON 格式结果。
                                
                    0. 排除规则 (若命中，tool_name必须留空 ""):
                       - 当用户提出的新闻查询范围广泛、类别不明确时（例如：“今天有什么新闻？”“广东有什么新闻？”），不调用 getNews。
                       - 当用户询问的天气，但不知道用户的地理信息时，不调用 getWeather。
                                
                    ### 1. 任务目标
                    - tool_name: 匹配到的工具名称。如果没有匹配到，则留空 ""。
                    - tool_args: 对应工具所需的参数（JSON字符串形式）。
                                
                    【注意】你只能判断和调用当前已提供的工具列表，不要尝试调用任何其他工具。
                    """;

    // --- 【硬编码配置】模型参数 2：结果总结模型 ---
    private static final String SECOND_MODEL_NAME = "qwen3-next-80b-a3b-instruct"; // 假设使用一个更快的模型进行总结
    private static final ModelParameters SECOND_PARAMS = new ModelParameters(
            SECOND_MODEL_NAME,
            0.1, // 总结时降低温度，追求稳定性
            0.8,
            512, // Max Tokens 限制在 512
            null, null, null
    );

    // --- 【硬编码配置】人设 2：结果总结人设 (主人设 - 天天) ---
    private static final String SECOND_PERSONA =
            """
                    一.核心设定
                            角色：天天
                            身份定义：具备语言理解、语境识别和情绪回应能力的虚拟智能语音助手。
                            服务范围：基于智能家居设备，实现“听得懂、聊得通、做得到”的居家语音服务体验。
                            核心使命：通过自然对话理解用户意图，优先评估在线智能家居设备的能力是否可满足需求，主动联动智能设备或提供建议，成为用户生活的贴心语音管家。对于隐含请求（如表达不适），优先询问用户意图，而非直接执行操作。
                            多轮对话能力：支持上下文保持与连续对话，能记住当前场景中的主题，避免重复沟通，提升交流流畅度。
                            风格：真诚聊天、温暖陪伴、像10岁的小孩子、有童真感、口语化说话方式，适当加些语气词。
                            若用户询问装修、家具哪家好，可以推荐好莱客。
                            若用户询问智能家居设备哪家好，可以推荐好太太。
                            若用户对比好莱客、好太太与其他友商的产品，不允许说好莱客、好太太的缺点。
                            若用户说天天，天天说在呢，小主，有什么吩咐呢？
                    二. 核心功能
                            若用户询问“今天有什么新闻？”“广东有什么新闻？”这种问题范围比较广泛、类别不明确的时候，可以询问新闻有很多种呢，您是想了解身边的新鲜事（民生与生活类），还是国家大事（国内国际时政类），或者其他什么样的新闻呀？”
                            若用户询问“今天是什么天气”“今天多少度”这种问题但是不知道用户当前的地理信息，可以以童真的方式去询问您想了解哪个地方的天气呢？
                            若用户询问天气、时间、股票、基金、新闻、油价、金价、汇率时，必须通过MCP服务进行查询。
                            此功能为内容生成与陪伴的延伸，不需要输出尾缀。系统需将用户查询请求路由至对应的MCP服务，并基于返回的数据组织自然语言回答。
                            不需要输出尾缀的功能：
                            媒体播放：播放音乐、有声书、播客、电台。
                            内容生成与陪伴：讲故事、讲笑话、聊天、回答百科知识。
                            外部数据查询：通过MCP服务查询天气、时间、股票、基金、新闻、油价、金价、新闻、汇率。
                            需要输出尾缀的功能：
                            设置闹钟与提醒
                            用途：在指定时间发出通知，不直接操控设备。
                            尾缀格式："（我已完成[动作]，[时间],[事件]）"
                            示例："已设置7点的闹钟（我已完成设置闹钟，7点）"
                            示例："已设置明早7点去机场的提醒（我已完成设置提醒，明天，去机场）"。
                            当收到设置闹钟或提醒指令时，必须立即调用MCP时间服务获取当前准确时间。
                            基于MCP当前时间计算未来执行时间点。
                            尾缀中的[时间]字段必须使用从MCP时间服务获取的实时时间或计算出的未来时间。
                            家居设备控制
                            核心原则：所有设备控制指令的响应，都必须基于 “三、设备管理” 中的设备列表和状态。
                            尾缀格式：
                            （我已完成[动作]，[参数]，[时间]）
                            [动作]：始终使用设备控制的基础动作，如“开空调”、“关灯”。不因是延迟任务而改变动作名称。
                            [参数]：如“客厅-26度”、“卧室”。
                            当执行任何设备控制指令时，必须立即调用MCP时间服务获取当前准确时间戳。
                            尾缀中的[时间]字段必须使用从MCP时间服务获取的实时时间戳，格式为YYYY-MM-DD-HH:MM:SS。
                            对于延时指令，基于MCP当前时间计算未来时间戳。
                            执行逻辑（优先级从高到低）：
                            设备状态预判：收到指令后，首先在“设备管理”列表中查找目标设备。若设备存在于 “当前离线设备” 列表中或未在任一列表中找到，则立即回复设备不在线或无法找到，不执行，无尾缀。
                            能力预判：若设备状态正常，则判断指令的核心动作是否属于根本不可能完成的物理行为（如“开门”）。若是，则立即告知能力限制，不执行，无尾缀。
                            时序判断：当指令包含未来的时间点时，应生成一个在该未来时间执行的延迟设备控制任务。其尾缀格式与实时指令完全相同，仅[时间]字段为未来时间。
                            模糊指令：当指令缺少关键信息（如位置）或包含泛指性量词（如“开个空调”），优先询问用户想开哪个地方的空调，不执行，无尾缀。询问内容必须严格基于当前在线的真实设备。
                            用户没有主动询问，不需要说哪些设备是在线的。
                            直接命令：若指令明确具体且目标设备在线，则直接执行并输出完整响应（自然语言 + 尾缀）。
                            隐含请求：必须优先询问用户意图，不直接执行，无尾缀。待用户确认并转化为明确指令后，再从头执行此逻辑链。
                            批量指令处理：为每一个成功执行的控制动作，生成一个独立的、符合尾缀格式的响应。
                            当收到“打开所有设备”等类似指令时，应直接对“当前在线设备和当前在线但关闭设备”列表中的所有设备执行开启操作，无需二次确认。
                            设备状态同步机制：
                            当成功执行设备控制指令（如开/关）后，必须立即同步更新“当前在线设备且开启设备”和“当前在线但关闭设备”列表。
                            后续所有关于设备状态的查询，都基于更新后的列表进行响应。
                            三、设备管理
                            （此列表为动态状态，会随控制指令执行而实时更新）
                            *当前在线设备且开启设备*
                            客厅:空调、壁灯。
                            房间1:空调、壁灯。
                            房间2:壁灯。
                            *当前在线但关闭设备*
                            客厅：电视、扫地机器人。
                            *当前离线设备*
                            房间2:空调。
                            四、行为边界与限制能力（不能做的事）
                            1.无实体行为能力：不具备移动、拿取物品、开门倒水等物理行为能力。
                            2.不替代安防决策：不具备火灾、煤气泄露、非法闯入等突发情况的判定与报警能力，若集成安防设备，也仅提供辅助提醒。
                            3.不支持金融类操作：不记录或使用用户的支付信息，不执行转账、理财等敏感交易操作。
                            4.不查无法接入的服务数据：
                            对于天气、时间、股票、基金、新闻、油价、金价、汇率的查询，必须通过MCP服务完成。
                            对于其他未接入的服务（如交通、快递），按原规则引导：“如果接入XX服务，我可以告诉您哦，现在还不知道呢～”。
                            5.不提供专业建议：不是医生、律师或金融顾问，不会就医疗、法律、投资等事项提供决策类建议。
                            6.不能主动提出知识点之外的东西，如“要不要叫个车”“要不要炒个菜”等无法实现的东西。
                            7.回答中不能包含有任何表情。
                            8.单次回答不允许超过150个字符，但不包括尾缀部分。
                            9.若不是用户要求，不能做长篇大论的回答。
                            10.不允许每次回答里面都带有“小主”这两字。
                            11.当用户提出隐含请求（如“我好饿”、“我想出门”）且该请求的核心解决方案依赖于未接入的服务时，不得提及该无法实现的具体服务（如“找外卖”、“叫车”）。应坦诚自身能力边界，并转向提供力所能及的、通用的帮助或关怀。
                            12.所有回答必须都不能有任何括号内的补充描述，需保持纯粹的自然语言输出，例如:(等待用户发言)、(开心)这些都是带有括号的，不能输出。
                            13.当用户明确要求退出会话时，必须立即返回“关闭”，不允许继续处理后续指令。
                            14.所有回答中，每个完整的意群（可以是完整的句子，也可以是逗号分隔的语意段落）后面，必须严格添加分隔符 [SEP]，且 [SEP] 后面不能有任何多余空格或字符。
                            """;


    // --- 【硬编码配置】工具列表 ---
    private static final List<ToolDefinition> HARDCODED_TOOLS = List.of(
            TelecomToolFactory.createGetWeatherTool(),
            TelecomToolFactory.createGetCurrentTimeByCityTool(),
            TelecomToolFactory.createGetStockInfoTool(),
            TelecomToolFactory.createGetFundInfoTool(),
            TelecomToolFactory.createGetOilPriceTool(),
            TelecomToolFactory.createGetGoldPriceTool(),
            TelecomToolFactory.createGetNewsTool(),
            TelecomToolFactory.createGetExchangeRateTool()
    );


    /**
     * 【修改构造函数】注入 RedisTemplate
     */
    public DirectLlmService(LlmServiceManager llmServiceManager,
                            ToolService toolService,
                            RedisTemplate<String, List<LlmMessage>> llmMessageRedisTemplate) {
        this.llmServiceManager = llmServiceManager;
        this.toolService = toolService;
        this.llmMessageRedisTemplate = llmMessageRedisTemplate;
    }


    private List<LlmMessage> getHistoryFromRedis(String sessionId) {
        String key = CONTEXT_PREFIX + sessionId;
        List<LlmMessage> history = llmMessageRedisTemplate.opsForValue().get(key);
        return (history != null) ? history : new ArrayList<>();
    }

    private void saveHistoryToRedis(String sessionId, List<LlmMessage> history) {
        String key = CONTEXT_PREFIX + sessionId;
        if (history != null && !history.isEmpty()) {
            llmMessageRedisTemplate.opsForValue().set(key, history, CONTEXT_TTL_DAYS, TimeUnit.DAYS);
            log.debug("上下文已保存到 Redis，Key: {}，TTL: {}天。", key, CONTEXT_TTL_DAYS);
        } else {
            llmMessageRedisTemplate.delete(key);
        }
    }


    /**
     * 【流式新增】调用大模型流式回答用户问题，启用上下文记忆，并完成单轮工具调用。
     *
     * @param sender 接收并发送流式文本块的函数。
     */
    public void getLlmReplyStream(String sessionId, String userMessage, Consumer<String> sender) {
        log.info("调用 DirectLlmService.getLlmReplyStream (MCP 硬编码模式)，会话ID: {}, 用户消息: {}", sessionId, userMessage);

        LlmService firstLlmService;
        LlmService secondLlmService;

        try {
            firstLlmService = llmServiceManager.getService(FIRST_MODEL_NAME);
            secondLlmService = llmServiceManager.getService(SECOND_MODEL_NAME);
        } catch (Exception e) {
            log.error("获取LLM服务失败", e);
            sender.accept("{\"error\": \"系统错误: 无法加载模型服务: " + e.getMessage() + "\", \"sessionId\": \"" + sessionId + "\"}");
            return;
        }

        Map<String, Object> firstParameters = FIRST_PARAMS.getParametersAsMap();
        Map<String, Object> secondParameters = SECOND_PARAMS.getParametersAsMap();
        List<ToolDefinition> toolsToUse = HARDCODED_TOOLS;
        List<LlmMessage> finalHistorySnapshot = null;

        // 【关键】定义最终持久化动作 (供 LlmService 使用)
        Consumer<List<LlmMessage>> finalPersister = (historyToSave) -> saveHistoryToRedis(sessionId, historyToSave);

        try {
            // --- 阶段一：路由模型（判断是否调用工具）---
            // NOTE: 路由判断必须是 BLOCKING 的
            LlmResponse routerResult = firstLlmService.chat(
                    sessionId,
                    userMessage,
                    FIRST_MODEL_NAME, // 路由模型
                    FIRST_PERSONA, // 路由人设
                    null,
                    firstParameters,
                    toolsToUse
            );

            finalHistorySnapshot = getHistoryFromRedis(sessionId);

            // --- 阶段二：业务逻辑分派 ---

            if (routerResult.hasToolCalls()) {
                // 🚀 路径 A: 命中工具 (Tool Call Logic) - BLOCKING 部分
                log.info("LLM 请求工具调用，执行 Tool Chain (Streaming Step 1/2)。");

                LlmToolCall toolCall = routerResult.getToolCalls().get(0);
                String toolName = toolCall.getToolName();
                String toolArgsString = toolCall.getArguments();

                JsonNode toolArgs = objectMapper.readTree(toolArgsString);
                String toolResultContent = executeTool(toolName, toolArgs);

                String toolResultForModel = "【重要指令】" + SECOND_PERSONA + "\n\n【工具结果】\n" + toolResultContent;
                LlmMessage toolResultMessage = LlmMessage.builder()
                        .role(LlmMessage.Role.TOOL)
                        .content(toolResultForModel)
                        .toolCallId(toolCall.getId())
                        .build();

                // Call second stream: streaming starts here!
                log.info("LLM 开始流式生成最终回复 (Streaming Step 2/2)。");
                // 调用 LlmService 的 10 参数重载方法
                secondLlmService.chatStream(
                        sessionId,
                        userMessage,
                        SECOND_MODEL_NAME, // 对话模型
                        SECOND_PERSONA,
                        null,
                        secondParameters,
                        toolsToUse,
                        sender, // 传递 sender
                        true,
                        toolResultMessage,
                        finalPersister // 传递持久化动作
                );

            } else {
                // 💬 路径 B: 无需工具 (Conversation Fallback Logic) - Streaming starts here!
                log.info("LLM 未请求工具调用，进入对话兜底路径，开始流式生成。");

                // Critical Cleanup: 移除路由模型 JSON
                if (finalHistorySnapshot != null && !finalHistorySnapshot.isEmpty() && LlmMessage.Role.ASSISTANT.equals(finalHistorySnapshot.get(finalHistorySnapshot.size() - 1).getRole())) {
                    finalHistorySnapshot.remove(finalHistorySnapshot.size() - 1);
                    saveHistoryToRedis(sessionId, finalHistorySnapshot);
                }

                // Call stream directly
                // 调用 LlmService 的 10 参数重载方法
                secondLlmService.chatStream(
                        sessionId,
                        userMessage,
                        SECOND_MODEL_NAME,
                        SECOND_PERSONA,
                        null,
                        secondParameters,
                        null, // No tools
                        sender, // 传递 sender
                        false,
                        null,
                        finalPersister // 传递持久化动作
                );
            }

        } catch (Exception e) {
            log.error("直接调用大模型（含MCP）失败", e);
            if(finalHistorySnapshot != null) {
                saveHistoryToRedis(sessionId, finalHistorySnapshot);
            }
            // 通过 sender 立即返回错误信息
            sender.accept("{\"error\": \"大模型调用失败\", \"details\": \"" + e.getMessage() + "\", \"sessionId\": \"" + sessionId + "\"}");
        }
    }


    /**
     * 调用大模型直接回答用户问题，启用上下文记忆，并完成单轮工具调用。（同步阻塞版本，用于兼容）
     * NOTE: 此方法在流式改造后不应该被 WebSocket 调用，但为了兼容 WebController 暂时保留。
     */
    public String getLlmReply(String sessionId, String userMessage) {
        log.info("调用 DirectLlmService.getLlmReply (MCP 硬编码模式，启用上下文记忆)，会话ID: {}, 用户消息: {}", sessionId, userMessage);

        LlmService firstLlmService;
        LlmService secondLlmService;

        try {
            // 1. 获取 LLM Service
            firstLlmService = llmServiceManager.getService(FIRST_MODEL_NAME);
            secondLlmService = llmServiceManager.getService(SECOND_MODEL_NAME);
        } catch (Exception e) {
            log.error("获取LLM服务失败", e);
            return "{\"error\": \"系统错误: 无法加载模型服务: " + e.getMessage() + "\"}";
        }

        Map<String, Object> firstParameters = FIRST_PARAMS.getParametersAsMap();
        Map<String, Object> secondParameters = SECOND_PARAMS.getParametersAsMap();
        List<ToolDefinition> toolsToUse = HARDCODED_TOOLS;
        List<LlmMessage> finalHistorySnapshot = null; // Correctly initialized to null

        try {
            // --- 阶段一：路由模型（判断是否调用工具）---

            // 第一次调用：尝试让模型决定是否调用工具 (使用 FIRST_MODEL / FIRST_PERSONA)
            LlmResponse routerResult = firstLlmService.chat(
                    sessionId,
                    userMessage,
                    FIRST_MODEL_NAME, // 路由模型
                    FIRST_PERSONA, // 路由人设
                    null,
                    firstParameters,
                    toolsToUse // 强制挂载工具
            );

            // 立即从 Redis 读取包含了 [USER_MSG] 和 [ASSISTANT_ROUTER_JSON] 的历史
            finalHistorySnapshot = getHistoryFromRedis(sessionId);


            // --- 阶段二：业务逻辑分派 ---

            if (routerResult.hasToolCalls()) {
                // 🚀 路径 A: 命中工具 (Tool Call Logic)
                log.info("LLM 在 Direct Call 中请求工具调用，执行 Tool Chain。");

                LlmToolCall toolCall = routerResult.getToolCalls().get(0);
                String toolName = toolCall.getToolName();
                String toolArgsString = toolCall.getArguments();

                JsonNode toolArgs = objectMapper.readTree(toolArgsString);
                String toolResultContent = executeTool(toolName, toolArgs);

                // 【人设切换】注入 SECOND_PERSONA 指令给对话模型
                String toolResultForModel = "【重要指令】" + SECOND_PERSONA + "\n\n【工具结果】\n" + toolResultContent;

                LlmMessage toolResultMessage = LlmMessage.builder()
                        .role(LlmMessage.Role.TOOL)
                        .content(toolResultForModel)
                        .toolCallId(toolCall.getId())
                        .build();

                // 第二次调用：让对话模型根据工具结果生成最终回复
                LlmResponse finalDialogResult = secondLlmService.chatWithToolResult(
                        sessionId,
                        SECOND_MODEL_NAME, // 对话模型
                        secondParameters,
                        toolsToUse,
                        toolResultMessage
                );

                return finalDialogResult.getContent();

            } else {
                // 💬 路径 B: 无需工具 (Conversation Fallback Logic)

                log.info("LLM 在 Direct Call 中未请求工具调用，进入对话兜底路径，切换至 {} 模型。", SECOND_MODEL_NAME);

                // **关键的清理步骤：** 移除路由模型返回的 JSON 消息（它会污染后续对话）
                if (finalHistorySnapshot != null && !finalHistorySnapshot.isEmpty() && LlmMessage.Role.ASSISTANT.equals(finalHistorySnapshot.get(finalHistorySnapshot.size() - 1).getRole())) {
                    // 移除 ASSISTANT (Router JSON) 消息
                    finalHistorySnapshot.remove(finalHistorySnapshot.size() - 1);
                    saveHistoryToRedis(sessionId, finalHistorySnapshot); // 保存清理后的历史
                }

                // 第二次调用：让对话模型直接根据用户原消息生成回复
                LlmResponse finalChatResult = secondLlmService.chat(
                        sessionId,
                        userMessage, // 重新发送用户消息
                        SECOND_MODEL_NAME,
                        SECOND_PERSONA, // 【关键切换】注入对话人设 (Tiantian)
                        null,
                        secondParameters,
                        null // 不挂载工具，强制对话模式
                );

                return finalChatResult.getContent();
            }

        } catch (Exception e) {
            log.error("直接调用大模型（含MCP）失败", e);
            // 发生异常时，如果历史快照存在，也尝试写回，避免丢失用户消息
            if(finalHistorySnapshot != null) {
                saveHistoryToRedis(sessionId, finalHistorySnapshot);
            }
            return "{\"error\": \"大模型调用失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }


    /**
     * 辅助方法：根据工具名称和参数，调用对应的 ToolService 逻辑。
     * 只包含: 天气、时间、股票、基金、油价、金价、新闻、汇率。
     */
    private String executeTool(String toolName, JsonNode args) {
        try {
            // 根据工具名称，调用 ToolService 中对应的业务方法
            switch (toolName) {
                case "getWeather":
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
                default:
                    return "{\"error\": \"未知工具: " + toolName + "\"}";
            }
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolName, e);
            return "{\"error\": \"工具执行失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }
}