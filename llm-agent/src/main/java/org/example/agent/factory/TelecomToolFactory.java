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
     * 创建 "比较两个套餐" 工具的定义。
     * @return ToolDefinition
     */
    public static ToolDefinition createCompareTwoPlansTool() {
        FunctionDefinition function = FunctionDefinition.builder()
                .name("compareTwoPlans")
                .description("当客户想要对比两个套餐的差异时调用此工具。" +
                        "你需要从对话中准确提取两个需要对比的套餐名称。" +
                        // 关键指令：明确告知如何处理指代不明的情况
                        "重要规则：当用户用 '我现在的套餐'、'我的套餐' 或 '原套餐' 等词语指代他们当前的套餐时，你必须使用 '用户原套餐' 这个标准名称作为参数。" +
                        "对于客服推荐的新方案，通常应使用 '升档新套餐'。" +
                        // 强化约束
                        "工具会返回这两个套餐的详细数据，你需要基于这些数据，用口语化的方式为用户进行对比和简洁回答。"
                )
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
     * 新增：创建 "查询套餐常见问题 (FAQ)" 工具的定义。
     * @return ToolDefinition
     */
    /**
     * 新增：创建 "查询套餐常见问题 (FAQ)" 工具的定义。
     * @return ToolDefinition
     */
    public static ToolDefinition createQueryMcpFaqTool() {
        Map<String, ParameterProperty> properties = Map.of(
                "intent", ParameterProperty.builder()
                        .type("string")
                        //.description("用户问题的核心意图，必须是 '优惠到期的月租', '合约期多久', '怎么取消套餐', '违约金多少', '恢复原套餐', '流量结转', '生效时间', '去店里办', '怎么扣费', '充值渠道', '其他套餐', '4G网还是5G', '流量是通用还是定向', '接电话费用', '打电话费用', '办副卡', '宽带业务', '改套餐', '10块钱话费是什么', '要换号码吗' 中的一个。")
                        .build()
        );

        ParameterSchema parameters = ParameterSchema.builder()
                .type("object")
                .properties(properties)
                .required(List.of("intent"))
                .build();

        FunctionDefinition function = FunctionDefinition.builder()
                .name("queryMcpFaq")
                .description("该工具是移动手机卡套餐知识库，当客户询问关于产品介绍、升级确认、升级提醒、升级成功通知、结東语的问题时，输出末尾必须附加\"(我已完成流程[tasks])" +
                        "例如：如果匹配的意图是 '产品介绍'，则在末尾附加 （我已完成流程[产品介绍]）；如果匹配的意图是 '升级确认'，则在末尾附加 （我已完成流程[升级确认]），以此类推。"+
                        "以下是节点的话术方向，输出内容末尾必须附加\"(我已完成流程[tasks])\"" +
                        "产品介绍的话术方向：有个活动特别划算、介绍具体资费、非常划算\n" +
                        "升级确认的话术方向：询问客户是否确认升级 \n" +
                        "升级提醒的话术方向：升级前进行业务提醒，没问题就帮客户升级了\n" +
                        "升级成功通知话术方向：告知客户已成功升级，直接输出固定话术。\n" +
                        "结束语话术方向：直接输出固定话术"
                )
//                .description("【该工具是移动手机卡套餐知识库，包含用户常问的套餐问题、费用、升档信息、优惠期（合约期）、违约金等。" +
//                        "当用户提到套餐、月租、流量、升级、优惠期等关键词，务必调用本工具，用户的问题与工具内意图不完全一致时，请尝试语义匹配，不能仅依赖关键词。"+
//                        "当客户询问关于产品介绍、升级确认、升级提醒、升级成功通知、结束语的问题时，输出末尾必须附加”（我已完成流程[tasks]）“" +
//                        "例如：如果匹配的意图是 '产品介绍'，则在末尾附加 （我已完成流程[产品介绍]）；如果匹配的意图是 '升级确认'，则在末尾附加 （我已完成[流程升级确认]），以此类推。"
//                )
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
                createCompareTwoPlansTool()
                , createQueryMcpFaqTool()
        );
    }
}