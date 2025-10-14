package org.example.llm.dto.api.doubao;

/**
 * @author hull
 * @since 2025/10/14 15:46
 */

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 用于与豆包（及其他OpenAI兼容API）进行数据交换的 Message DTO。
 * 它的结构严格匹配API的JSON格式。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // 序列化时忽略null字段，以保持请求体干净
public class DoubaoMessage {

    private String role;
    private String content;
}
