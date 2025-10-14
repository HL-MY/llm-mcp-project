package org.example.llm.dto.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * 参数的 schema 定义。
 * 描述了参数的整体结构，通常是一个包含多个属性的 "object"。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ParameterSchema {

    /**
     * 参数的类型，对于函数调用，这里固定为 "object"。
     */
    @Builder.Default
    private String type = "object";

    /**
     * 一个Map，其中 key 是参数名，value 是该参数的具体属性定义。
     */
    private Map<String, ParameterProperty> properties;

    /**
     * 一个字符串列表，声明哪些参数是必须提供的。
     */
    private List<String> required;
}