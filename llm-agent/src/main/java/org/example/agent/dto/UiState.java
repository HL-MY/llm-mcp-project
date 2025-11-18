package org.example.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 【重大重构】
 * 这个 DTO 现在只负责承载 "会话" 相关的UI状态 (左侧栏和开场白)。
 * 所有的 "配置" 状态现在由 ConfigAdminController 独立管理。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiState {

    // 左侧栏：流程状态
    private final Map<String, String> processStatus;
    // 左侧栏：人设预览
    private final String persona;
    // 聊天框：开场白 (仅在 / 和 /reset 时非 null)
    private final String openingMonologue;

    // --- 构造函数已更新 ---
    public UiState(Map<String, String> processStatus, String persona, String openingMonologue) {
        this.processStatus = processStatus;
        this.persona = persona;
        this.openingMonologue = openingMonologue;
    }

    // --- Getters ---
    public Map<String, String> getProcessStatus() { return processStatus; }
    public String getPersona() { return persona; }
    public String getOpeningMonologue() { return openingMonologue; }
}