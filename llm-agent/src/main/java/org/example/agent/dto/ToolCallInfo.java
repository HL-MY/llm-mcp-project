package org.example.agent.dto;

/**
 * DTO for sending tool call information to the frontend.
 */
public class ToolCallInfo {
    private final String toolName;
    private final String toolArgs;
    private final String toolResult;

    public ToolCallInfo(String toolName, String toolArgs, String toolResult) {
        this.toolName = toolName;
        this.toolArgs = toolArgs;
        this.toolResult = toolResult;
    }

    // Getters
    public String getToolName() { return toolName; }
    public String getToolArgs() { return toolArgs; }
    public String getToolResult() { return toolResult; }
}