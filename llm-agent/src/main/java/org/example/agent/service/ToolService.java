package org.example.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mcp.service.FaqService;
import org.example.mcp.service.PlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ToolService {

    private static final Logger log = LoggerFactory.getLogger(ToolService.class);
    private final PlanService planService;
    private final FaqService faqService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final WebClient webClient;

    // 【关键修复】存储带 "Bearer " 前缀的 Key
    private final String dashscopeApiKeyWithBearer;

    public ToolService(PlanService planService, FaqService faqService,
                       WebClient.Builder webClientBuilder,
                       @Value("${alibaba.api.key}") String dashscopeApiKey) {
        this.planService = planService;
        this.faqService = faqService;
        this.webClient = webClientBuilder.build();
        // 【关键修复】还原回 V11/V12 的鉴权方式，Python 脚本证明了这是对的
        this.dashscopeApiKeyWithBearer = "Bearer " + dashscopeApiKey;
    }

    // ... (compareTwoPlans 和 queryMcpFaq 保持不变) ...
    public String compareTwoPlans(String planName1, String planName2) {
        log.info("ToolService: 正在直接调用 PlanService 比较套餐: {} vs {}", planName1, planName2);
        try {
            return mapper.writeValueAsString(planService.compareTwoPlans(planName1, planName2));
        } catch (Exception e) {
            log.error("调用 PlanService compareTwoPlans 失败", e);
            return "{\"error\": \"无法比较套餐\"}";
        }
    }
    public String queryMcpFaq(String intent) {
        log.info("ToolService: 正在调用 FaqService 查询FAQ, 意图: {}", intent);
        try {
            return faqService.getAnswerForIntent(intent);
        } catch (Exception e) {
            log.error("调用 FaqService queryMcpFaq 失败", e);
            return "{\"error\": \"无法查询常见问题答案\"}";
        }
    }


    /**
     * 【V11/V12 版 - 封装 Payload】
     */
    public String getWeather(String city, String date) {
        log.info("ToolService: 正在调用 WebClient (SSE) 查询天气 (amap-maps)");
        log.info("ToolService: 城市: {}, 日期: {}", city, date);

        String sseUrl = "https://dashscope.aliyuncs.com/api/v1/mcps/amap-maps/sse";

        // 1. 创建参数 map
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("city", city);
        parameters.put("date", date);

        // 2. 封装到 "parameters" key 下
        Map<String, Object> input = Map.of(
                "parameters", parameters
        );

        Map<String, Object> requestBody = Map.of(
                "model", "amap-maps",
                "input", input,
                "stream", true
        );

        try {
            String fullResponse = webClient.post()
                    .uri(sseUrl)
                    // 【关键修复】使用 Authorization: Bearer ...
                    .header("Authorization", this.dashscopeApiKeyWithBearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    // ... (SSE 处理逻辑不变) ...
                    .filter(sseLine -> sseLine.startsWith("data:"))
                    .map(sseLine -> sseLine.substring(5).trim())
                    .filter(data -> !data.isEmpty() && !data.equalsIgnoreCase("[DONE]"))
                    .collectList()
                    .map(dataBlocks -> {
                        StringBuilder textContent = new StringBuilder();
                        String error = null;

                        for (String data : dataBlocks) {
                            try {
                                JsonNode root = mapper.readTree(data);
                                if (root.has("code") || root.has("message")) {
                                    error = data;
                                    log.error("Dashscope SSE 流返回错误: {}", error);
                                    break;
                                }
                                if (root.has("output") && root.get("output").has("text")) {
                                    textContent.append(root.get("output").get("text").asText());
                                }
                            } catch (Exception e) {
                                log.warn("解析 SSE data 块失败: {}", data, e);
                            }
                        }

                        if (error != null) {
                            return "{\"error\": \"查询天气API返回错误\", \"details\": " + error + "}";
                        }

                        String result = textContent.toString();

                        if (result.isEmpty()) {
                            log.warn("ToolService: SSE 流处理完成，但未提取到任何 'output.text' 内容。API 可能未返回数据。");
                            return "{\"error\": \"工具未返回任何内容\", \"details\": \"API stream was empty.\"}";
                        }

                        return result;
                    })
                    .block();

            log.info("WebClient (SSE) 完整天气响应 (amap-maps): {}", fullResponse);
            return fullResponse;

        } catch (Exception e) {
            log.error("调用 WebClient getWeather (amap-maps) 失败", e);
            // 【关键】将 401 错误信息返回给 ChatService
            return "{\"error\": \"调用 WebClient getWeather 失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 【V11/V12 版 - 封装 Payload】
     */
    public String webSearch(String query, Integer count) {
        log.info("ToolService: 正在调用 WebClient (SSE) 联网搜索");
        log.info("ToolService: 搜索词: {}, 数量: {}", query, count);

        String sseUrl = "https://dashscope.aliyuncs.com/api/v1/mcps/webSearch/sse";

        // 1. 创建参数 map
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        parameters.put("count", count);

        // 2. 封装到 "parameters" key 下
        Map<String, Object> input = Map.of(
                "parameters", parameters
        );

        Map<String, Object> requestBody = Map.of(
                "model", "jisu-search.internet",
                "input", input,
                "stream", true
        );

        try {
            String fullResponse = webClient.post()
                    .uri(sseUrl)
                    // 【关键修复】使用 Authorization: Bearer ...
                    .header("Authorization", this.dashscopeApiKeyWithBearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    // ... (SSE 处理逻辑不变) ...
                    .filter(sseLine -> sseLine.startsWith("data:"))
                    .map(sseLine -> sseLine.substring(5).trim())
                    .filter(data -> !data.isEmpty() && !data.equalsIgnoreCase("[DONE]"))
                    .collectList()
                    .map(dataBlocks -> {
                        StringBuilder textContent = new StringBuilder();
                        String error = null;

                        for (String data : dataBlocks) {
                            try {
                                JsonNode root = mapper.readTree(data);
                                if (root.has("code") || root.has("message")) {
                                    error = data;
                                    log.error("Dashscope SSE (webSearch) 流返回错误: {}", error);
                                    break;
                                }
                                if (root.has("output") && root.get("output").has("text")) {
                                    textContent.append(root.get("output").get("text").asText());
                                }
                            } catch (Exception e) {
                                log.warn("解析 SSE (webSearch) data 块失败: {}", data, e);
                            }
                        }

                        if (error != null) {
                            return "{\"error\": \"联网搜索API返回错误\", \"details\": " + error + "}";
                        }

                        String result = textContent.toString();

                        if (result.isEmpty()) {
                            log.warn("ToolService (webSearch): SSE 流处理完成，但未提取到任何 'output.text' 内容。");
                            return "{\"error\": \"工具未返回任何内容\", \"details\": \"API stream was empty.\"}";
                        }

                        return result;
                    })
                    .block();

            log.info("WebClient (SSE) 完整搜索响应: {}", fullResponse);
            return fullResponse;

        } catch (Exception e) {
            log.error("调用 WebClient webSearch 失败", e);
            return "{\"error\": \"调用 WebClient webSearch 失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }
}