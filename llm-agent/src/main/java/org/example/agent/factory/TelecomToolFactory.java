package org.example.agent.factory;

import org.example.llm.dto.tool.FunctionDefinition;
import org.example.llm.dto.tool.ParameterProperty;
import org.example.llm.dto.tool.ParameterSchema;
import org.example.llm.dto.tool.ToolDefinition;

import com.google.gson.Gson; // 导入 Gson
import java.util.List;
import java.util.Map;

public class TelecomToolFactory {

    private static final Gson gson = new Gson(); // 用于 enum

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

    /**
     * 【V14 最终版】
     * - 替换为 和风天气(QWeather) 的定义, 匹配 QWeatherTool.java 的逻辑
     * - LLM 调用的工具名仍为 'getWeather'
     */
    public static ToolDefinition createGetWeatherTool() {
        // 1. 定义参数
        // 1a. 城市 (必需)
        ParameterProperty cityParam = ParameterProperty.builder()
                .type("string")
                .description("需要查询天气的城市名称, 例如 '北京', '上海'")
                .build();

        // 1b. 预报类型 (必需)
        ParameterProperty forecastParam = ParameterProperty.builder()
                .type("string")
                .description("查询的类型。'now' 表示实时天气, '3d' 表示未来3天预报。如果用户没说，默认为 'now'。")
                // 1c. 限制 LLM 只能从这两个值中选择
                .enumValues(List.of("now", "3d"))
                .build();

        Map<String, ParameterProperty> properties = Map.of(
                "city", cityParam,
                "forecast_type", forecastParam
        );

        // 2. 构建 ParameterSchema
        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(List.of("city", "forecast_type")) // 强制 LLM 两者都返回
                .build();

        // 3. 构建 FunctionDefinition
        FunctionDefinition function = FunctionDefinition.builder()
                .name("getWeather") // 我们保持 'getWeather' 名称不变，ChatService 会路由
                .description("获取指定城市的实时天气或未来3天天气预报。") // 描述更新
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    // 【V14 移除】
    // public static ToolDefinition createWebSearchTool() { ... }

    public static Map<String, String> getAllToolDescriptions() {
        return Map.of(
                "compareTwoPlans", createCompareTwoPlansTool().getFunction().getDescription(),
                "queryMcpFaq", createQueryMcpFaqTool().getFunction().getDescription(),
                "getWeather", "获取指定城市的实时天气或未来3天天气预报。" // 描述更新
        );
    }

    public static List<ToolDefinition> getAllToolDefinitions() {
        return List.of(
                createCompareTwoPlansTool(),
                createQueryMcpFaqTool(),
                createGetWeatherTool() // (已更新)
        );
    }
}