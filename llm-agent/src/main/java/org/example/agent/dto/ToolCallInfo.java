package org.example.agent.dto;

/**
 * DTO for sending tool call information to the frontend.
 */
public class ToolCallInfo {
    private final String toolName;
    private final String toolArgs;
    private final String toolResult;
    private final Long toolExecutionTime;
    private final Long llmFirstCallTime; // 新增：第一次LLM调用耗时
    private final Long llmSecondCallTime; // 新增：第二次LLM调用耗时

    public ToolCallInfo(String toolName, String toolArgs, String toolResult, Long toolExecutionTime, Long llmFirstCallTime, Long llmSecondCallTime) {
        this.toolName = toolName;
        this.toolArgs = toolArgs;
        this.toolResult = toolResult;
        this.toolExecutionTime = toolExecutionTime;
        this.llmFirstCallTime = llmFirstCallTime;
        this.llmSecondCallTime = llmSecondCallTime;
    }

    // Getters
    public String getToolName() { return toolName; }
    public String getToolArgs() { return toolArgs; }
    public String getToolResult() { return toolResult; }
    public Long getToolExecutionTime() { return toolExecutionTime; }
    public Long getLlmFirstCallTime() { return llmFirstCallTime; } // 新增 Getter
    public Long getLlmSecondCallTime() { return llmSecondCallTime; } // 新增 Getter
}