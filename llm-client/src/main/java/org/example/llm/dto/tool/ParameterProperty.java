package org.example.llm.dto.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个参数的属性定义。
 * 描述了每个参数的类型（如 "string"）和功能说明。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ParameterProperty {

    /**
     * 参数的类型，例如 "string", "integer", "number", "boolean"。
     */
    private String type;

    /**
     * 对这个参数的描述，告诉大模型这个参数是用来做什么的。
     */
    private String description;
}