package org.example.llm.dto.llm;

import lombok.Builder;
import lombok.Data;

/**
 * 内部统一的、与厂商无关的消息 DTO
 */
@Data
@Builder
public class LlmMessage {
    private String role;
    private String content;

    public static class Role {
        public static final String SYSTEM = "system";
        public static final String USER = "user";
        public static final String ASSISTANT = "assistant";
        public static final String TOOL = "tool"; // 【新增】
    }
}