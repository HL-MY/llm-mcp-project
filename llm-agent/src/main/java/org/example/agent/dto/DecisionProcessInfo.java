package org.example.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 【新增 DTO】
 * 承载预处理（“小模型”）的决策过程和“思考链”，用于在前端展示。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecisionProcessInfo {

    private String preProcessingModel;
    private String detectedEmotion;
    private String detectedIntent;
    private Boolean isSensitive;
    private String selectedStrategy; // 最终合并后注入到Prompt的策略
    private Long preProcessingTimeMs;

    // Getters and Setters
    public String getPreProcessingModel() { return preProcessingModel; }
    public void setPreProcessingModel(String preProcessingModel) { this.preProcessingModel = preProcessingModel; }
    public String getDetectedEmotion() { return detectedEmotion; }
    public void setDetectedEmotion(String detectedEmotion) { this.detectedEmotion = detectedEmotion; }
    public String getDetectedIntent() { return detectedIntent; }
    public void setDetectedIntent(String detectedIntent) { this.detectedIntent = detectedIntent; }
    public Boolean getIsSensitive() { return isSensitive; }
    public void setIsSensitive(Boolean isSensitive) { this.isSensitive = isSensitive; }
    public String getSelectedStrategy() { return selectedStrategy; }
    public void setSelectedStrategy(String selectedStrategy) { this.selectedStrategy = selectedStrategy; }
    public Long getPreProcessingTimeMs() { return preProcessingTimeMs; }
    public void setPreProcessingTimeMs(Long preProcessingTimeMs) { this.preProcessingTimeMs = preProcessingTimeMs; }
}