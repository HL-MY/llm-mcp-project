package org.example.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 专用于无状态、硬编码 LLM 接口的简单返回 DTO。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DirectChatResponse {
    private final String reply;
    private String sessionId; // 【新增】

    public DirectChatResponse(String reply, String sessionId) {
        this.reply = reply;
        this.sessionId = sessionId;
    }

    public String getReply() {
        return reply;
    }
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}