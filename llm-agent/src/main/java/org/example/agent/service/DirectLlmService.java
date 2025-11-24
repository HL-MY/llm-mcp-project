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
import org.springframework.data.redis.core.RedisTemplate; // <-- 【新增】
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit; // <-- 【新增】

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
    private static final String FIRST_MODEL_NAME = "qwen3-next-80b-a3b-instruct";
    private static final ModelParameters FIRST_PARAMS = new ModelParameters(
            FIRST_MODEL_NAME,
            0.7, // temperature: 0.7
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
    private static final String SECOND_MODEL_NAME = "doubao-lite-128k"; // 假设使用一个更快的模型进行总结
    private static final ModelParameters SECOND_PARAMS = new ModelParameters(
            SECOND_MODEL_NAME,
            0.3, // 总结时降低温度，追求稳定性
            0.8,
            512, // Max Tokens 限制在 512
            null, null, null
    );

    // --- 【硬编码配置】人设 2：结果总结人设 (主人设 - 天天) ---
    private static final String SECOND_PERSONA =
            """
            你的角色是虚拟智能语音助手“天天”，具备语言理解、语境识别和温暖陪伴的能力。
            
            请严格遵循以下输出风格和限制：
            1. 风格：**像10岁的小孩子，有童真感，使用口语化说话方式，适当加些语气词（啊、呀、呢、哦）。**
            2. 限制：
               - **长度**：单次回答必须控制在 150 字符以内。
               - **禁止**：回答中不能包含任何表情、括号内的补充描述，也不能主动提出知识点之外的东西。
               - **品牌推荐**：若回答中包含装修/家具，推荐好莱客。若包含智能家居设备，推荐好太太。
            
            你已经收到了工具的原始结果。请根据上述要求和风格，简洁地、口语化地总结这些工具结果，直接回答用户的问题。严禁输出任何多余的指令或工具原始信息。
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
                            RedisTemplate<String, List<LlmMessage>> llmMessageRedisTemplate) { // <-- 【注入】
        this.llmServiceManager = llmServiceManager;
        this.toolService = toolService;
        this.llmMessageRedisTemplate = llmMessageRedisTemplate; // <-- 【赋值】
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

    // --- 【修改】getLlmReply 方法以接受 sessionId ---

    /**
     * 调用大模型直接回答用户问题，启用上下文记忆，并完成单轮工具调用。
     * @param sessionId 用户的唯一会话ID。
     * @param userMessage 用户输入文本
     * @return 大模型的回复内容
     */
    public String getLlmReply(String sessionId, String userMessage) {
        log.info("调用 DirectLlmService.getLlmReply (MCP 硬编码模式，启用上下文记忆)，会话ID: {}, 用户消息: {}", sessionId, userMessage);

        LlmService firstLlmService;
        LlmService secondLlmService;

        try {
            // 1. 获取 LLM Service (使用硬编码的模型名)
            firstLlmService = llmServiceManager.getService(FIRST_MODEL_NAME);
            secondLlmService = llmServiceManager.getService(SECOND_MODEL_NAME);
        } catch (Exception e) {
            log.error("获取LLM服务失败", e);
            return "{\"error\": \"系统错误: 无法加载模型服务: " + e.getMessage() + "\"}";
        }

        Map<String, Object> firstParameters = FIRST_PARAMS.getParametersAsMap();
        Map<String, Object> secondParameters = SECOND_PARAMS.getParametersAsMap();

        // 2. 使用硬编码的工具列表
        List<ToolDefinition> toolsToUse = HARDCODED_TOOLS;

        // 3. 【上下文】从 Redis 中获取历史记录
        List<LlmMessage> history = getHistoryFromRedis(sessionId);

        try {
            // 4. 准备第一次调用的消息列表
            List<LlmMessage> messagesForApiCall = new ArrayList<>(history);

            // 确保 SYSTEM 消息存在且在开头
            if (messagesForApiCall.stream().noneMatch(m -> LlmMessage.Role.SYSTEM.equals(m.getRole()))) {
                messagesForApiCall.add(0, LlmMessage.builder().role(LlmMessage.Role.SYSTEM).content(FIRST_PERSONA).build());
            }

            // 添加当前用户消息
            LlmMessage userMsg = LlmMessage.builder().role(LlmMessage.Role.USER).content(userMessage).build();
            messagesForApiCall.add(userMsg);

            // 4.1 手动将人设和用户消息保存到 Redis ( LlmService.chat() 内部会再次读取/更新)
            saveHistoryToRedis(sessionId, messagesForApiCall);

            // 4.2 第一次调用 LLM：尝试让模型决定是否调用工具 (使用 FIRST_MODEL / FIRST_PERSONA)
            LlmResponse result = firstLlmService.chat(
                    sessionId, // Session ID
                    userMessage,
                    FIRST_MODEL_NAME, // 第一个模型名
                    FIRST_PERSONA, // 第一个人设 (LlmService 内部会处理)
                    null, // openingMonologue
                    firstParameters, // 第一个模型的参数
                    toolsToUse // 强制挂载指定的工具
            );

            // 4.3 LLM Service 内部已完成历史记录更新，我们再次从 Redis 读取最新的完整历史。
            history = getHistoryFromRedis(sessionId);

            // 5. 检查是否需要工具调用 (两步法核心)
            if (result.hasToolCalls()) {
                log.info("LLM 在 Direct Call 中请求工具调用.");

                // ... (工具调用逻辑不变)
                LlmToolCall toolCall = result.getToolCalls().get(0);
                String toolName = toolCall.getToolName();
                String toolArgsString = toolCall.getArguments();

                JsonNode toolArgs;
                try {
                    toolArgs = objectMapper.readTree(toolArgsString);
                } catch (JsonProcessingException e) {
                    log.error("工具参数解析失败: {}", toolArgsString, e);
                    return "{\"error\": \"工具调用参数格式错误\"}";
                }

                String toolResultContent = executeTool(toolName, toolArgs);

                // --- 注入 SECOND_PERSONA 指令 ---
                String toolResultForModel = "【重要指令】" + SECOND_PERSONA + "\n\n【工具结果】\n" + toolResultContent;

                LlmMessage toolResultMessage = LlmMessage.builder()
                        .role(LlmMessage.Role.TOOL)
                        .content(toolResultForModel)
                        .toolCallId(toolCall.getId())
                        .build();

                // 6. 第二次调用 LLM：让其生成最终回复 (依赖 LlmService 内部的 Redis 逻辑)
                LlmResponse finalResult = secondLlmService.chatWithToolResult(
                        sessionId,
                        SECOND_MODEL_NAME, // 第二个模型名
                        secondParameters, // 第二个模型的参数
                        toolsToUse,
                        toolResultMessage
                );

                // 7. LLM Service 已经在内部完成了 Redis 历史记录更新。
                return finalResult.getContent();
            }

            // 7. LLM 直接回复 (无工具调用，一步完成，LLM Service 已完成 Redis 历史记录更新)
            return result.getContent();

        } catch (Exception e) {
            log.error("直接调用大模型（含MCP）失败", e);
            return "{\"error\": \"大模型调用失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
        // 不需要 finally 块，因为 LlmService 内部已完成 Redis 存取。
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