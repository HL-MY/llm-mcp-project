package org.example.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @author hull
 * @since 2025/9/25 10:27
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // This annotation prevents sending null fields in the JSON response
public class ChatResponse {
    private final String reply;
    private final UiState uiState;
    private final ToolCallInfo toolCall; // New field for tool call info

    public ChatResponse(String reply, UiState uiState, ToolCallInfo toolCall) {
        this.reply = reply;
        this.uiState = uiState;
        this.toolCall = toolCall;
    }

    // Overloaded constructor for backwards compatibility and for messages without tool calls
    public ChatResponse(String reply, UiState uiState) {
        this(reply, uiState, null);
    }

    public String getReply() {
        return reply;
    }

    public UiState getUiState() {
        return uiState;
    }

    public ToolCallInfo getToolCall() {
        return toolCall;
    }
}