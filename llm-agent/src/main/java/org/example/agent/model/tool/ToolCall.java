package org.example.agent.model.tool;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【手写模型】替代官方的 ToolCall。
 * 模型返回的工具调用请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ToolCall {

    /**
     * 工具调用的唯一ID。
     */
    private String id;

    /**
     * 工具的类型，固定为 "function"。
     */
    private String type;

    /**
     * 调用的具体函数信息。
     */
    private ToolCallFunction function;
}