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
    private String sessionId;
    private String streamStatus; // 【新增】用于标记流状态

    public DirectChatResponse(String reply, String sessionId) {
        this(reply, sessionId, null); // 兼容旧构造函数
    }

    // 【修改后的主构造函数】
    public DirectChatResponse(String reply, String sessionId, String streamStatus) {
        this.reply = reply;
        this.sessionId = sessionId;
        this.streamStatus = streamStatus;
    }

    public String getReply() { return reply; }
    public String getSessionId() { return sessionId; }
    public String getStreamStatus() { return streamStatus; } // 【新增 Getter】

    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setStreamStatus(String streamStatus) { this.streamStatus = streamStatus; } // 【新增 Setter】
}