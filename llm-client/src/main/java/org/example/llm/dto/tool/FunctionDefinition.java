package org.example.llm.dto.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 函数定义的核心模型。
 * 描述了函数的名称、功能说明以及参数结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class FunctionDefinition {

    /**
     * 函数的名称，必须是字母、数字和下划线的组合。
     */
    private String name;

    /**
     * 对函数功能的详细描述，这部分内容会直接影响大模型对工具的理解和使用。
     */
    private String description;

    /**
     * 函数的参数结构定义。
     */
    private ParameterSchema parameters;
}