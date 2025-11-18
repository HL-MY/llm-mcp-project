package org.example.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.example.mcp.service.FaqService;
import org.example.mcp.service.PlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ToolService {

    private static final Logger log = LoggerFactory.getLogger(ToolService.class);
    private final PlanService planService;
    private final FaqService faqService;
    private final ObjectMapper mapper;
    private final WebClient webClient;
    private final Gson gson = new Gson();

    // --- (V18) 和风天气 (QWeather) 的配置 ---
    private final String qweatherApiKey;
    // 【V18 关键修复】: 必须使用两个不同的 Host
    private final String qweatherGeoHost;
    private final String qweatherDevHost;
    private final Map<String, String> cityIdCache;
    // --- 结束 ---

    public ToolService(PlanService planService, FaqService faqService,
                       WebClient.Builder webClientBuilder,
                       ObjectMapper objectMapper, // 注入 ObjectMapper
                       // 【V18 关键修复】: 注入三个配置
                       @Value("${qweather.api-key}") String qweatherApiKey,
                       @Value("${qweather.geo.host}") String qweatherGeoHost,
                       @Value("${qweather.dev.host}") String qweatherDevHost
    ) {
        this.planService = planService;
        this.faqService = faqService;
        this.webClient = webClientBuilder.build();
        this.mapper = objectMapper; // 存储 ObjectMapper

        // 存储和风天气配置
        this.qweatherApiKey = qweatherApiKey;
        this.qweatherGeoHost = qweatherGeoHost; // 存储 Geo Host
        this.qweatherDevHost = qweatherDevHost; // 存储 Dev Host

        // 初始化城市ID缓存
        this.cityIdCache = new ConcurrentHashMap<>();

        if (this.qweatherApiKey == null || this.qweatherApiKey.isBlank()) {
            log.error("!!!!!!!!!! qweather.api-key 未在 application.properties 中正确配置 !!!!!!!!!!");
        }
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
     * (V14/V18)
     * - 调用和风天气(QWeather)的直接 API
     */
    public String getWeather(String city, String forecastType) {
        log.info("ToolService: 正在调用 [和风天气] API");
        log.info("ToolService: 城市: {}, 类型: {}", city, forecastType);

        try {
            // 1. 获取 City ID (带缓存)
            String cityId = getCityId(city);
            if (cityId == null) {
                log.warn("Tool [WeatherTool] 无法找到城市ID: {}", city);
                return "{\"error\": \"找不到城市: " + city + "\"}";
            }
            log.debug("Tool [WeatherTool] 找到 City ID: {}", cityId);

            // 2. 根据类型调用不同的 API
            if ("3d".equals(forecastType)) {
                log.debug("Tool [WeatherTool] 执行3天预报查询...");
                return getWeatherForecast(cityId, "3d");
            } else {
                // 默认为 "now"
                log.debug("Tool [WeatherTool] 执行实时天气查询...");
                return getWeatherData(cityId);
            }

        } catch (Exception e) {
            log.error("Tool [WeatherTool] 调用异常: {}", e.getMessage(), e);
            return "{\"error\": \"调用天气API时发生内部异常: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 【V18 修复】: 内部方法：调用和风天气 "城市搜索" API
     * (使用 qweatherGeoHost)
     */
    private String getCityId(String city) throws IOException {
        // 1. 检查缓存
        String cachedId = this.cityIdCache.get(city);
        if (cachedId != null) {
            log.debug("Tool [WeatherTool] 缓存命中, City ID: {}", cachedId);
            return cachedId;
        }

        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        // 【V18 关键修复】: 使用 qweather.geo.host
        String url = String.format("%s/v2/city/lookup?location=%s&key=%s",
                this.qweatherGeoHost, encodedCity, this.qweatherApiKey);

        log.debug("GeoAPI 请求 URL: {}", url);

        try {
            // 使用 WebClient (GET)
            String responseBody = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null) {
                throw new IOException("GeoAPI 请求失败, 响应体为空");
            }

            JsonNode root = mapper.readTree(responseBody);
            if ("200".equals(root.path("code").asText())) {
                JsonNode locations = root.path("location");
                if (locations.isArray() && !locations.isEmpty()) {
                    String id = locations.get(0).path("id").asText(null);
                    if (id != null) {
                        // 2. 存入缓存
                        log.debug("Tool [WeatherTool] API 查询成功, 存入缓存: {} -> {}", city, id);
                        this.cityIdCache.put(city, id);
                        return id;
                    }
                }
            }
            log.warn("GeoAPI 业务错误: code={}, city={}", root.path("code").asText(), city);
            return null;

        } catch (Exception e) {
            log.error("GeoAPI WebClient 请求失败: {}", e.getMessage());
            throw new IOException("GeoAPI WebClient 请求失败", e);
        }
    }

    /**
     * 【V18 修复】: 内部方法：调用和风天气 "实时天气" API
     * (使用 qweatherDevHost)
     */
    private String getWeatherData(String cityId) throws IOException {
        // 【V18 关键修复】: 使用 qweather.dev.host
        String url = String.format("%s/v7/weather/now?location=%s&key=%s",
                this.qweatherDevHost, cityId, this.qweatherApiKey);

        log.debug("WeatherAPI (now) 请求 URL: {}", url);

        try {
            // 使用 WebClient (GET)
            String responseBody = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null) {
                throw new IOException("WeatherAPI(now) 请求失败, 响应体为空");
            }

            JsonNode root = mapper.readTree(responseBody);

            if ("200".equals(root.path("code").asText())) {
                // 只返回关键信息给LLM
                JsonNode now = root.path("now");
                JsonObject result = new JsonObject();
                result.addProperty("temp", now.path("temp").asText());
                result.addProperty("text", now.path("text").asText());
                result.addProperty("windDir", now.path("windDir").asText());
                result.addProperty("windScale", now.path("windScale").asText());
                result.addProperty("humidity", now.path("humidity").asText());
                return gson.toJson(result);
            } else {
                log.warn("WeatherAPI(now) 业务错误: code={}", root.path("code").asText());
                return "{\"error\": \"获取实时天气失败\", \"qweather_code\": \"" + root.path("code").asText() + "\"}";
            }
        } catch (Exception e) {
            log.error("WeatherAPI(now) WebClient 请求失败: {}", e.getMessage());
            throw new IOException("WeatherAPI(now) WebClient 请求失败", e);
        }
    }

    /**
     * 【V18 修复】: 内部方法：调用和风天气 "N天预报" API
     * (使用 qweatherDevHost)
     */
    private String getWeatherForecast(String cityId, String days) throws IOException {
        // 【V18 关键修复】: 使用 qweather.dev.host
        String url = String.format("%s/v7/weather/%s?location=%s&key=%s",
                this.qweatherDevHost, days, cityId, this.qweatherApiKey);

        log.debug("WeatherAPI ({}) 请求 URL: {}", days, url);

        try {
            // 使用 WebClient (GET)
            String responseBody = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null) {
                throw new IOException("WeatherAPI(" + days + ") 请求失败, 响应体为空");
            }

            JsonNode root = mapper.readTree(responseBody);

            if ("200".equals(root.path("code").asText())) {
                // 预报API返回的是 "daily" 数组, 我们只提取关键信息
                JsonNode daily = root.path("daily");
                if (daily.isArray()) {
                    JsonObject[] results = new JsonObject[daily.size()];
                    for (int i = 0; i < daily.size(); i++) {
                        JsonNode day = daily.get(i);
                        JsonObject result = new JsonObject();
                        result.addProperty("fxDate", day.path("fxDate").asText());
                        result.addProperty("tempMax", day.path("tempMax").asText());
                        result.addProperty("tempMin", day.path("tempMin").asText());
                        result.addProperty("textDay", day.path("textDay").asText());
                        result.addProperty("textNight", day.path("textNight").asText());
                        result.addProperty("windDirDay", day.path("windDirDay").asText());
                        result.addProperty("windScaleDay", day.path("windScaleDay").asText());
                        results[i] = result;
                    }
                    return gson.toJson(results);
                }
                return "[]";
            } else {
                log.warn("WeatherAPI({}) 业务错误: code={}", days, root.path("code").asText());
                return "{\"error\": \"获取天气预报失败\", \"qweather_code\": \"" + root.path("code").asText() + "\"}";
            }
        } catch (Exception e) {
            log.error("WeatherAPI({}) WebClient 请求失败: {}", days, e.getMessage());
            throw new IOException("WeatherAPI(" + days + ") WebClient 请求失败", e);
        }
    }
}