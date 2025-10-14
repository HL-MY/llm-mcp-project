package org.example.agent.factory;

import org.example.llm.dto.tool.FunctionDefinition;
import org.example.llm.dto.tool.ParameterProperty;
import org.example.llm.dto.tool.ParameterSchema;
import org.example.llm.dto.tool.ToolDefinition;


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
                .description("查询所有可用的电信套餐列表。")
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
                .description("获取两个指定套餐的详细信息以进行比较。")
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


    /**
     * 统一获取所有已定义的电信工具列表。
     * @return List of ToolDefinition
     */
    public static List<ToolDefinition> getAllToolDefinitions() {
        return List.of(
                createQueryAllPlansTool(),
                createCompareTwoPlansTool()
        );
    }
}