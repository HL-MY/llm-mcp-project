package org.example.agent.factory;

import org.example.agent.model.tool.FunctionDefinition;
import org.example.agent.model.tool.ParameterProperty;
import org.example.agent.model.tool.ParameterSchema;
import org.example.agent.model.tool.ToolDefinition;

import java.util.List;
import java.util.Map;

/**
 * 工厂类，用于创建和管理所有电信业务相关的工具定义。
 * 模仿 ToolSchemaFactory.java 的结构，将工具定义逻辑集中化。
 */
public class TelecomToolFactory {

    /**
     * 创建 "查询所有套餐" 工具的定义。
     * @return ToolDefinition
     */
    public static ToolDefinition createQueryAllPlansTool() {
        // 此工具没有参数，直接构建一个空的 ParameterSchema
        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(Map.of())
                .build();

        FunctionDefinition function = FunctionDefinition.builder()
                .name("queryAllPlans")
                .description("当用户第一次询问有什么套餐，或者想了解所有套餐选择时，必须调用此工具。")
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    /**
     * 创建 "比较两个套餐" 工具的定义。
     * @return ToolDefinition
     */
    public static ToolDefinition createCompareTwoPlansTool() {
        FunctionDefinition function = FunctionDefinition.builder()
                .name("compareTwoPlans")
                .description("当用户明确表示想要比较两个套餐时，或者在对话中提到了两个具体的套餐名并想知道它们的区别时，调用此工具。你需要从对话中准确提取两个套餐的完整名称作为参数，" +
                        "例如 '用户原套餐' 和 '升档新套餐'。工具会返回这两个套餐的详细数据（如月租、流量、通话等），你需要基于这些数据，用口语化的方式为用户进行对比和总结。")
                .parameters(createCompareTwoPlansParameters()) // 调用私有方法构建参数
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }

    /**
     * 为 "比较两个套餐" 工具构建参数的私有辅助方法。
     * @return ParameterSchema
     */
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

    public static ToolDefinition createGetPlanDetailsTool() {
        Map<String, ParameterProperty> properties = Map.of(
                "planName", ParameterProperty.builder().type("string").description("需要查询详情的套餐的完整名称").build()
        );

        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(List.of("planName"))
                .build();

        FunctionDefinition function = FunctionDefinition.builder()
                .name("getPlanDetails")
                .description("当用户询问某个具体套餐的详细信息时，调用此工具获取该套餐的月租、流量、通话等数据。")
                .parameters(parameters)
                .build();

        return ToolDefinition.builder()
                .type("function")
                .function(function)
                .build();
    }


    /**
     * 统一获取所有已定义的电信工具列表。
     * @return List of ToolDefinition
     */
    public static List<ToolDefinition> getAllToolDefinitions() {
        return List.of(
                createQueryAllPlansTool(),
                createCompareTwoPlansTool(),
                createGetPlanDetailsTool()
        );
    }
}