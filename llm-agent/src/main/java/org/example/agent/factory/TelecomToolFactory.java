package org.example.agent.factory;

import org.example.llm.dto.tool.FunctionDefinition;
import org.example.llm.dto.tool.ParameterProperty;
import org.example.llm.dto.tool.ParameterSchema;
import org.example.llm.dto.tool.ToolDefinition;

import java.util.List;
import java.util.Map;

public class TelecomToolFactory {

    public static ToolDefinition createCompareTwoPlansTool() {
        FunctionDefinition function = FunctionDefinition.builder()
                .name("compareTwoPlans")
                .description("当客户想要对比两个套餐的差异时调用此工具。" +
                        "你需要从对话中准确提取两个需要对比的套餐名称。" +
                        "重要规则：当用户用 '我现在的套餐'、'我的套餐' 或 '原套餐' 等词语指代他们当前的套餐时，你必须使用 '用户原套餐' 这个标准名称作为参数。" +
                        "对于客服推荐的新方案，通常应使用 '升档新套餐'。" +
                        "工具会返回这两个套餐的详细数据，你需要基于这些数据，用口语化的方式为用户进行对比和简洁回答。"
                )
                .parameters(createCompareTwoPlansParameters())
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    private static ParameterSchema createCompareTwoPlansParameters() {
        Map<String, ParameterProperty> properties = Map.of(
                "planName1", ParameterProperty.builder().type("string").description("第一个套餐的完整名称").build(),
                "planName2", ParameterProperty.builder().type("string").description("第二个套餐的完整名称").build()
        );

        return ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(List.of("planName1", "planName2"))
                .build();
    }

    public static ToolDefinition createQueryMcpFaqTool() {
        Map<String, ParameterProperty> properties = Map.of(
                "intent", ParameterProperty.builder()
                        .type("string")
                        .build()
        );

        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(List.of("intent"))
                .build();

        FunctionDefinition function = FunctionDefinition.builder()
                .name("queryMcpFaq")
                .description("【该工具是移动手机卡套餐知识库】" +
                        "包含用户常问的套餐问题、费用、升档信息、优惠期（合约期）、违约金等。" +
                        "当用户提到套餐、月租、流量、升级、优惠期等关键词时，调用本工具查询标准答案。" +
                        "用户的问题与工具内意图不完全一致时，请尝试语义匹配，不能仅依赖关键词。")
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    public static ToolDefinition createGetWeatherTool() {
        Map<String, ParameterProperty> properties = Map.of(
                "city", ParameterProperty.builder()
                        .type("string")
                        .description("需要查询天气的城市名称，例如: 杭州")
                        .build(),
                "date", ParameterProperty.builder()
                        .type("string")
                        .description("需要查询的日期，例如 'today'。如果用户未指定，请使用 'today'。")
                        .build()
        );

        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(List.of("city", "date"))
                .build();

        FunctionDefinition function = FunctionDefinition.builder()
                .name("getWeather")
                .description("查询指定城市和日期的实时天气预报。")
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    public static ToolDefinition createGetOilPriceTool() {
        Map<String, ParameterProperty> properties = Map.of(
                "province", ParameterProperty.builder()
                        .type("string")
                        .description("需要查询油价的省名称，例如: 广东，新疆、内蒙古")
                        .build()
        );

        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(List.of("province"))
                .build();

        FunctionDefinition function = FunctionDefinition.builder()
                .name("getOilPrice")
                .description("查询指定省份油价。")
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    public static ToolDefinition createGetGoldPriceTool() {
        FunctionDefinition function = FunctionDefinition.builder()
                .name("getGoldPrice")
                .description("查询金价")
                .parameters(null)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    public static ToolDefinition createGetNewsTool() {
        Map<String, ParameterProperty> properties = Map.of(
                "areaName", ParameterProperty.builder()
                        .type("string")
                        .description("新闻的地区，例如: 广东，新疆、安徽")
                        .build(),
                "title", ParameterProperty.builder()
                        .type("string")
                        .description("新闻的标题")
                        .build()
        );

        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(null)
                .build();
        FunctionDefinition function = FunctionDefinition.builder()
                .name("getNews")
                .description("查询新闻")
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }
    public static ToolDefinition createGetExchangeRateTool() {
        Map<String, ParameterProperty> properties = Map.of(
                "currency", ParameterProperty.builder()
                        .type("string")
                        .description("货币代码，例如: CNY，USD、JPY，KRW 等")
                        .build()
        );

        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(null)
                .build();
        FunctionDefinition function = FunctionDefinition.builder()
                .name("getExchangeRate")
                .description("查询汇率")
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    public static ToolDefinition createGetFundInfoTool() {
        Map<String, ParameterProperty> properties = Map.of(
                "fundCode", ParameterProperty.builder()
                        .type("string")
                        .description("基金代码，例如: 018124 ")
                        .build()
        );

        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(List.of("fundCode"))
                .build();

        FunctionDefinition function = FunctionDefinition.builder()
                .name("getFundInfo")
                .description("查询基金信息")
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    public static ToolDefinition createGetCurrentTimeByCityTool() {
        Map<String, ParameterProperty> properties = Map.of(
                "city", ParameterProperty.builder()
                        .type("string")
                        .description("城市，如：北京，广州，莫斯科，平壤 等")
                        .build()
        );

        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(null)
                .build();

        FunctionDefinition function = FunctionDefinition.builder()
                .name("getCurrentTimeByCity")
                .description("查询城市当前时间")
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    public static ToolDefinition createGetStockInfoTool() {
        Map<String, ParameterProperty> properties = Map.of(
                "symbol", ParameterProperty.builder()
                        .type("string")
                        .description("股票代码，如：sh000001,sz000002,bj430047 等")
                        .build()
        );

        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(List.of("symbol"))
                .build();

        FunctionDefinition function = FunctionDefinition.builder()
                .name("getStockInfo")
                .description("查询股票信息")
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    public static ToolDefinition createWebSearchTool() {
        Map<String, ParameterProperty> properties = Map.of(
                "query", ParameterProperty.builder()
                        .type("string")
                        .description("需要搜索的关键词或问题")
                        .build(),
                "count", ParameterProperty.builder()
                        .type("number")
                        .description("需要返回的搜索结果数量。如果用户未指定，请使用 5。")
                        .build()
        );

        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(List.of("query", "count"))
                .build();

        FunctionDefinition function = FunctionDefinition.builder()
                .name("webSearch")
                .description("【联网搜索工具】当用户询问实时信息、新闻、或你知识库中没有的外部信息时，调用此工具。")
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    public static Map<String, String> getAllToolDescriptions() {
//        return Map.of(
//                "compareTwoPlans", createCompareTwoPlansTool().getFunction().getDescription(),
//                "queryMcpFaq", createQueryMcpFaqTool().getFunction().getDescription(),
//                "getWeather", "查询指定城市和日期的实时天气预报。",
//                "getOilPrice", "查询指定省份油价。",
//                "getGoldPrice", "查询金价。",
//                "getNews", "查询新闻。",
//                "getExchangeRate", "查询汇率。",
//                "getFundInfo", "查询基金信息。",
//                "getCurrentTimeByCity", "查询城市当前时间。",
//                "webSearch", "【联网搜索工具】当用户询问实时信息、新闻、或你知识库中没有的外部信息时，调用此工具。"
//        );
        return Map.ofEntries(
                Map.entry("compareTwoPlans", createCompareTwoPlansTool().getFunction().getDescription()),
                Map.entry("queryMcpFaq", createQueryMcpFaqTool().getFunction().getDescription()),
                Map.entry("getWeather", "查询指定城市和日期的实时天气预报。"),
                Map.entry("getOilPrice", "查询指定省份油价。"),
                Map.entry("getGoldPrice", "查询金价。"),
                Map.entry("getNews", "查询新闻。"),
                Map.entry("getExchangeRate", "查询汇率。"),
                Map.entry("getFundInfo", "查询基金信息。"),
                Map.entry("getCurrentTimeByCity", "查询城市当前时间。"),
                Map.entry("getStockInfo", "查询股票信息。"),
                Map.entry("webSearch", "【联网搜索工具】当用户询问实时信息、新闻、或你知识库中没有的外部信息时，调用此工具。")
        );
    }

    public static List<ToolDefinition> getAllToolDefinitions() {
        return List.of(
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
    }
}
