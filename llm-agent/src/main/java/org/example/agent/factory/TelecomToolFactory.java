package org.example.agent.factory;

import org.example.llm.dto.tool.FunctionDefinition;
import org.example.llm.dto.tool.ParameterProperty;
import org.example.llm.dto.tool.ParameterSchema;
import org.example.llm.dto.tool.ToolDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TelecomToolFactory {

    // --- Helper methods to create base ToolDefinition objects ---

    private static ToolDefinition createBaseTool(String name, String description, ParameterSchema parameters) {
        FunctionDefinition function = FunctionDefinition.builder()
                .name(name)
                .description(description)
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    // --- Tool Definitions (Defaults) ---

    public static ToolDefinition createCompareTwoPlansTool() {
        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(Map.of(
                        "planName1", ParameterProperty.builder().type("string").description("第一个套餐的完整名称").build(),
                        "planName2", ParameterProperty.builder().type("string").description("第二个套餐的完整名称").build()
                ))
                .required(List.of("planName1", "planName2"))
                .build();

        String defaultDesc = "当客户想要对比两个套餐的差异时调用此工具。" +
                "你需要从对话中准确提取两个需要对比的套餐名称。" +
                "重要规则：当用户用 '我现在的套餐'、'我的套餐' 或 '原套餐' 等词语指代他们当前的套餐时，你必须使用 '用户原套餐' 这个标准名称作为参数。" +
                "对于客服推荐的新方案，通常应使用 '升档新套餐'。" +
                "工具会返回这两个套餐的详细数据，你需要基于这些数据，用口语化的方式为用户进行对比和简洁回答。";

        return createBaseTool("compareTwoPlans", defaultDesc, parameters);
    }

    public static ToolDefinition createQueryMcpFaqTool() {
        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(Map.of(
                        "intent", ParameterProperty.builder().type("string").build()
                ))
                .required(List.of("intent"))
                .build();

        String defaultDesc = "【该工具是移动手机卡套餐知识库】" +
                "包含用户常问的套餐问题、费用、升档信息、优惠期（合约期）、违约金等。" +
                "当用户提到套餐、月租、流量、升级、优惠期等关键词时，调用本工具查询标准答案。" +
                "用户的问题与工具内意图不完全一致时，请尝试语义匹配，不能仅依赖关键词。";

        return createBaseTool("queryMcpFaq", defaultDesc, parameters);
    }

    public static ToolDefinition createGetWeatherTool() {
        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(Map.of(
                        "city", ParameterProperty.builder().type("string").description("需要查询天气的城市名称，例如: 杭州").build()
//                        "date", ParameterProperty.builder().type("string").description("需要查询的日期，例如 'today'。如果用户未指定，请使用 'today'。").build()
                ))
//                .required(List.of("city", "date"))
                .required(List.of("city"))
                .build();

        return createBaseTool("getWeather", "查询指定城市和日期的实时天气预报。", parameters);
    }

    public static ToolDefinition createGetOilPriceTool() {
        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(Map.of(
                        "province", ParameterProperty.builder().type("string").description("需要查询油价的省名称，例如: 广东，新疆、内蒙古").build()
                ))
                .required(List.of("province"))
                .build();

        return createBaseTool("getOilPrice", "查询指定省份油价。", parameters);
    }

    public static ToolDefinition createGetGoldPriceTool() {
        return createBaseTool("getGoldPrice", "查询金价。", null);
    }

    public static ToolDefinition createGetNewsTool() {
        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(Map.of(
                        "areaName", ParameterProperty.builder().type("string").description("新闻的地区，例如: 广东，新疆、安徽").build(),
                        "title", ParameterProperty.builder().type("string").description("新闻的标题").build()
                ))
                .required(null)
                .build();

        return createBaseTool("getNews", "查询新闻。", parameters);
    }

    public static ToolDefinition createGetExchangeRateTool() {
        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(Map.of(
                        "currency", ParameterProperty.builder().type("string").description("货币代码，例如: CNY，USD、JPY，KRW 等").build()
                ))
                .required(null)
                .build();

        return createBaseTool("getExchangeRate", "查询汇率。", parameters);
    }

    public static ToolDefinition createGetFundInfoTool() {
        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(Map.of(
                        "fundCode", ParameterProperty.builder().type("string").description("基金代码，例如: 018124 ").build()
                ))
                .required(List.of("fundCode"))
                .build();

        return createBaseTool("getFundInfo", "查询基金信息。", parameters);
    }

    public static ToolDefinition createGetCurrentTimeByCityTool() {
        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(Map.of(
                        "city", ParameterProperty.builder().type("string").description("城市，如：北京，广州，莫斯科，平壤 等").build()
                ))
                .required(null)
                .build();

        return createBaseTool("getCurrentTimeByCity", "查询城市当前时间。", parameters);
    }

    public static ToolDefinition createGetStockInfoTool() {
        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(Map.of(
                        "symbol", ParameterProperty.builder().type("string").description("股票代码，如：sh000001,sz000002,bj430047 等").build()
                ))
                .required(List.of("symbol"))
                .build();

        return createBaseTool("getStockInfo", "查询股票信息。", parameters);
    }

    public static ToolDefinition createWebSearchTool() {
        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(Map.of(
                        "query", ParameterProperty.builder().type("string").description("需要搜索的关键词或问题").build(),
                        "count", ParameterProperty.builder().type("number").description("需要返回的搜索结果数量。如果用户未指定，请使用 5。").build()
                ))
                .required(List.of("query", "count"))
                .build();

        String defaultDesc = "【联网搜索工具】当用户询问实时信息、新闻、或你知识库中没有的外部信息时，调用此工具。";

        return createBaseTool("webSearch", defaultDesc, parameters);
    }

    // --- 【新增】为 DataInitializer 和 ConfigAdminController 提供硬编码默认描述 ---
    public static Map<String, String> getHardcodedToolDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("compareTwoPlans", createCompareTwoPlansTool().getFunction().getDescription());
        descriptions.put("queryMcpFaq", createQueryMcpFaqTool().getFunction().getDescription());
        descriptions.put("getWeather", createGetWeatherTool().getFunction().getDescription());
        descriptions.put("getOilPrice", createGetOilPriceTool().getFunction().getDescription());
        descriptions.put("getGoldPrice", createGetGoldPriceTool().getFunction().getDescription());
        descriptions.put("getNews", createGetNewsTool().getFunction().getDescription());
        descriptions.put("getExchangeRate", createGetExchangeRateTool().getFunction().getDescription());
        descriptions.put("getFundInfo", createGetFundInfoTool().getFunction().getDescription());
        descriptions.put("getCurrentTimeByCity", createGetCurrentTimeByCityTool().getFunction().getDescription());
        descriptions.put("getStockInfo", createGetStockInfoTool().getFunction().getDescription());
        descriptions.put("webSearch", createWebSearchTool().getFunction().getDescription());
        return descriptions;
    }

    // --- 【修改】为 ConfigAdminController 提供 UI 列表描述 ---
    public static Map<String, String> getAllToolDescriptions() {
        return getHardcodedToolDescriptions();
    }

    // --- 【修改】为 ChatService 提供最终 ToolDefinition ---
    // 这个方法会被 ChatService 调用，用于构建传给 LLM 的最终工具列表
    public static List<ToolDefinition> getAllToolDefinitions(Map<String, String> customDescriptions) {
        // 1. Get the base definitions with hardcoded default descriptions
        List<ToolDefinition> definitions = List.of(
                createCompareTwoPlansTool(),
                createQueryMcpFaqTool(),
                createGetWeatherTool(),
                createGetOilPriceTool(),
                createGetGoldPriceTool(),
                createGetNewsTool(),
                createGetExchangeRateTool(),
                createGetFundInfoTool(),
                createGetCurrentTimeByCityTool(),
                createGetStockInfoTool(),
                createWebSearchTool()
        );

        // 2. Apply custom descriptions
        return definitions.stream().map(def -> {
            String name = def.getFunction().getName();
            String customDesc = customDescriptions.get(name);
            if (customDesc != null) {
                // 使用 Builder 模式重新构建 FunctionDefinition 和 ToolDefinition
                FunctionDefinition newFunction = FunctionDefinition.builder()
                        .name(def.getFunction().getName())
                        .description(customDesc)
                        .parameters(def.getFunction().getParameters())
                        .build();

                return ToolDefinition.builder()
                        .type(def.getType())
                        .function(newFunction)
                        .build();
            }
            return def;
        }).collect(Collectors.toList());
    }
}