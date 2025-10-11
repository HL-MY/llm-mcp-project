package org.example.agent.model.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【手写模型】替代官方的 ToolCall 中的 Function 部分。
 * 描述了模型希望调用的函数名称和参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ToolCallFunction {

    /**
     * 【修正】添加缺失的 name 字段。
     * 希望调用的函数名称。
     */
    private String name;

    /**
     * 【修正】添加缺失的 arguments 字段。
     * 函数的参数，以JSON字符串的形式提供。
     */
    private String arguments;
}