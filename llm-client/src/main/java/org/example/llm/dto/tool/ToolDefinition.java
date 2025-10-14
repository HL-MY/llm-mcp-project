package org.example.llm.dto.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具定义的最外层模型。
 * 它描述了一个可供大模型使用的工具。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ToolDefinition {

    /**
     * 工具的类型，对于函数调用，这个值固定为 "function"。
     */
    @Builder.Default
    private String type = "function";

    /**
     * 函数工具的具体定义。
     */
    private FunctionDefinition function;
}