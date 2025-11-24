package org.example.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 专用于无状态、硬编码 LLM 接口的简单返回 DTO。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DirectChatResponse {
    private final String reply;

    public DirectChatResponse(String reply) {
        this.reply = reply;
    }

    public String getReply() {
        return reply;
    }
}