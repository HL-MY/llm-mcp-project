package org.example.agent.service;

import com.alibaba.dashscope.utils.JsonUtils;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.example.agent.utils.HttpUtils;
import org.example.mcp.service.FaqService;
import org.example.mcp.service.PlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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
    private final String dashscopeApiKeyWithBearer;

    private final String alApiCode;

    public ToolService(PlanService planService, FaqService faqService,
                       WebClient.Builder webClientBuilder,
                       @Value("${alibaba.api.key}") String dashscopeApiKey,
                       @Value("${al.api.appcode}") String alApiCode
    ) {
        this.planService = planService;
        this.faqService = faqService;
        this.webClient = webClientBuilder.build();
        // 使用 Bearer 鉴权方式
        this.dashscopeApiKeyWithBearer = "Bearer " + dashscopeApiKey;
        this.alApiCode = alApiCode;
    }

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
     * 调用天气查询服务
     */
    public String getWeather(String city, String date) {
        log.info("ToolService: 正在调用 WebClient (SSE) 查询天气 (amap-maps)");
        log.info("ToolService: 城市: {}, 日期: {}", city, date);

        String sseUrl = "https://dashscope.aliyuncs.com/api/v1/mcps/amap-maps/sse";

        // 构建请求参数
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("city", city);
        parameters.put("date", date);

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
                    // 使用 Authorization: Bearer ...
                    .header("Authorization", this.dashscopeApiKeyWithBearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
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
            return "{\"error\": \"调用 WebClient getWeather 失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }
    @Cacheable(value = "weatherCache", key = "#city", unless = "#result.contains('\"error\"')")
    public String getWeather(String city) {
        log.info("ToolService: 正在调用 getWeather 查询天气");
        log.info("ToolService: 城市: {}", city);
        // 阿里云
        String host = "https://ali-weather.showapi.com";
        String path = "/day15";
        String method = "GET";
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "APPCODE " + alApiCode);
        Map<String, String> query = new HashMap<>();
        query.put("area", city);
//        query.put("areaCode", "530700");
        try {
            HttpResponse response = HttpUtils.doGet(host, path, method, headers, query);
            String entity = EntityUtils.toString(response.getEntity());
            log.info("getWeather 获取到的结果： {}", entity);
            return JsonUtils.parse(entity).get("showapi_res_body").toString();
        } catch (Exception e) {
            log.info("ToolService:  getWeather  error", e);
            return "{\"error\": \"调用 getWeather 失败\", \"details\": \"" + e.getMessage() + "\"}";
        }

        // 高德
//        try {
//            // 获取城市编码
//            String getRegionCodeUrl = "https://restapi.amap.com/v3/geocode/geo"+"?key="+gdApiKey+"&output=JSON&address="+city;
//            String reginCodeString = WebClient.builder().baseUrl(getRegionCodeUrl).defaultHeader("Accept", "application/json").build()
//                    .get().retrieve().bodyToMono(String.class).block();
//            String regionCode = mapper.readTree(reginCodeString).path("geocodes").get(0).path("adcode").asText();
//
//            // extensions: base-返回实况天气，all-返回预报天气
//            String getWeatherUrl = "https://restapi.amap.com/v3/weather/weatherInfo"+"?extensions=all&key="+gdApiKey+"&city="+regionCode;
//            String weatherString = WebClient.builder().baseUrl(getWeatherUrl).defaultHeader("Accept", "application/json").build()
//                    .get().retrieve().bodyToMono(String.class).block();
//            System.out.println(weatherString);
//            return weatherString;
//        }catch (Exception e) {
//            log.error("getWeather出错，参数city{}", city, e);
//            return "{\"error\": \"调用 WebClient getWeather 失败\", \"details\": \"" + e.getMessage() + "\"}";
//        }
    }
    @Cacheable(value = "oilPriceCache", key = "#province", unless = "#result.contains('\"error\"')")
    public String getOilPrice(String province){
        String host = "https://smjryjcx.market.alicloudapi.com";
        String path = "/oil/price";
        String method = "GET";

        Map<String, String> headers = new HashMap<String, String>();
        //最后在header中的格式(中间是英文空格)为Authorization:APPCODE 83359fd73fe94948385f570e3c139105
        headers.put("Authorization", "APPCODE " + alApiCode);
        Map<String, String> querys = new HashMap<String, String>();
        querys.put("prov", province);

        try {
            HttpResponse response = HttpUtils.doGet(host, path, method, headers, querys);
            // 返回结果格式为Json字符串
            String responseBody = EntityUtils.toString(response.getEntity());
            log.info(responseBody);
            return JsonUtils.parse(responseBody).getAsJsonObject("data").getAsJsonArray("list").get(0).toString();
        } catch (Exception e) {
            log.info("getOilPrice api服务调用失败", e);
            return "{\"error\": \"调用 WebClient getOilPrice 失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }
    @Cacheable(value = "goldPriceCache", key = "'latest'", unless = "#result.contains('\"error\"')")
    public String getGoldPrice(){
        String host = "https://tsgold2.market.alicloudapi.com";
        String path = "/shgold";
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "APPCODE " + alApiCode);
        Map<String, String> query = new HashMap<>();

        try {
            HttpResponse response = HttpUtils.doGet(host, path, "GET", headers, query);
            // 返回结果格式为Json字符串
            String responseBody = EntityUtils.toString(response.getEntity());
            log.info("getGoldPrice 获取到的数据为：{}",responseBody);
            return JsonUtils.parse(responseBody).getAsJsonObject("data").get("list").toString();
        } catch (Exception e) {
            log.info("getGoldPrice api服务调用失败", e);
            return "{\"error\": \"调用 WebClient getGoldPrice 失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }

    public String getNews(String areaName,String title){
        String host = "https://areanews1.market.alicloudapi.com";
        String path = "/localnews/query";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "APPCODE " + alApiCode);
        Map<String, String> query = new HashMap<String, String>();
//        querys.put("areaId", "areaId");
        if (StringUtils.isNotBlank(areaName)) {
            query.put("areaName", areaName);
        }
        if (StringUtils.isNotBlank(title)) {
            query.put("title", title);
        }
        query.put("page", "1");

        try {
            HttpResponse response = HttpUtils.doGet(host, path, "GET", headers, query);
            // 返回结果格式为Json字符串
            String responseBody = EntityUtils.toString(response.getEntity());
            log.info("getNews 获取到的数据为：{}",responseBody);
            return JsonUtils.parse(responseBody).getAsJsonObject("showapi_res_body").toString();
        } catch (Exception e) {
            log.info("getNews api服务调用失败", e);
            return "{\"error\": \"调用 WebClient getNews 失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }
    @Cacheable(value = "exchangeRateCache", key = "#currency", unless = "#result.contains('\"error\"')")
    public String getExchangeRate(String currency){
        String host = "https://tsexchange.market.alicloudapi.com";
        String path = "/single";
        Map<String, String> headers = new HashMap<String, String>();
        //最后在header中的格式(中间是英文空格)为Authorization:APPCODE 83359fd73fe94948385f570e3c139105
        headers.put("Authorization", "APPCODE " + alApiCode);
        Map<String, String> query = new HashMap<String, String>();
        query.put("from", "CNY");
        if (StringUtils.isNotBlank(currency)) {
            query.put("from", currency);
        }

        try {
            HttpResponse response = HttpUtils.doGet(host, path, "GET", headers, query);
            // 返回结果格式为Json字符串
            String responseBody = EntityUtils.toString(response.getEntity());
            log.info("getExchangeRate 获取到的数据为：{}",responseBody);
            return JsonUtils.parse(responseBody).getAsJsonObject("data").toString();
        } catch (Exception e) {
            log.info("getExchangeRate api服务调用失败", e);
            return "{\"error\": \"调用 WebClient getExchangeRate 失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }
    @Cacheable(value = "fundInfoCache", key = "#fundCode", unless = "#result.contains('\"error\"')")
    public String getFundInfo(String fundCode){
        String host = "https://jmjjhqcx.market.alicloudapi.com";
        String path = "/fund/detail";
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "APPCODE " + alApiCode);
        //根据API的要求，定义相对应的Content-Type
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        Map<String, String> query = new HashMap<>();
        Map<String, String> body = new HashMap<>();
        body.put("fundCode", fundCode);

        try {
            HttpResponse response = HttpUtils.doPost(host, path, "POST", headers, query,body);
            // 返回结果格式为Json字符串
            String responseBody = EntityUtils.toString(response.getEntity(),"UTF-8");
            log.info("getFundInfo 获取到的数据为：{}",responseBody);
            return JsonUtils.parse(responseBody).getAsJsonObject("data").toString();
        } catch (Exception e) {
            log.info("getFundInfo api服务调用失败", e);
            return "{\"error\": \"调用 WebClient getFundInfo 失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }
    public String getCurrentTimeByCity(String city){
        String host = "https://timezone.market.alicloudapi.com";
        String path = "/timezone";
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "APPCODE " + alApiCode);
        Map<String, String> query = new HashMap<>();
        query.put("city", "北京");
        if (StringUtils.isNotBlank(city)) {
            query.put("city", city);
        }

        try {
            HttpResponse response = HttpUtils.doGet(host, path, "GET", headers, query);
            // 返回结果格式为Json字符串
            String responseBody = EntityUtils.toString(response.getEntity(),"UTF-8");
            log.info("getCurrentTimeByCity 获取到的数据为：{}",responseBody);
            return responseBody;
        } catch (Exception e) {
            log.info("getCurrentTimeByCity api服务调用失败", e);
            return "{\"error\": \"调用 WebClient getCurrentTimeByCity 失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }

    public String getStockInfo(String symbol){
        String host = "https://jmgphqcxhs.market.alicloudapi.com";
        String path = "/stock/a/price";
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "APPCODE " + alApiCode);
        //根据API的要求，定义相对应的Content-Type
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        Map<String, String> query = new HashMap<>();
        Map<String, String> body = new HashMap<>();
        body.put("symbol", symbol);


        try {
            HttpResponse response = HttpUtils.doPost(host, path, "GET", headers, query,body);
            // 返回结果格式为Json字符串
            String responseBody = EntityUtils.toString(response.getEntity(),"UTF-8");
            log.info("getStockInfo 获取到的数据为：{}",responseBody);
            return JsonUtils.parse(responseBody).getAsJsonObject("data").toString();
        } catch (Exception e) {
            log.info("getCurrentTimeByCity api服务调用失败", e);
            return "{\"error\": \"调用 WebClient getCurrentTimeByCity 失败\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 调用网络搜索服务
     */
    public String webSearch(String query, Integer count) {
        log.info("ToolService: 正在调用 WebClient (SSE) 联网搜索");
        log.info("ToolService: 搜索词: {}, 数量: {}", query, count);

        String sseUrl = "https://dashscope.aliyuncs.com/api/v1/mcps/webSearch/sse";

        // 构建请求参数
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        parameters.put("count", count);

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
                    // 使用 Authorization: Bearer ...
                    .header("Authorization", this.dashscopeApiKeyWithBearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
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
