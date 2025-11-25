package org.example.agent.dto;

/**
 * @author hull
 * @since 2025/9/25 10:27
 */
public class ChatRequest {
    private String message;
    private String sessionId; // 【新增】

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    // 【新增 Getter/Setter】
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
