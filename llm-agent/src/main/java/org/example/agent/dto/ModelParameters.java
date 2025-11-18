package org.example.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;

/**
 * 【新增 DTO】
 * 封装一个模型所需的所有可配置参数。
 * 主模型和预处理模型将各自拥有一个此对象的实例。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelParameters {

    private String modelName;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Double repetitionPenalty;
    private Double presencePenalty;
    private Double frequencyPenalty;

    // 默认构造函数 (用于JSON反序列化)
    public ModelParameters() {
    }

    // 带默认值的构造函数 (用于Service初始化)
    public ModelParameters(String modelName, Double temperature, Double topP, Integer maxTokens, Double repetitionPenalty, Double presencePenalty, Double frequencyPenalty) {
        this.modelName = modelName;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.repetitionPenalty = repetitionPenalty;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
    }

    // --- 核心方法：将自身转换为 LlmService 需要的 Map ---
    public Map<String, Object> getParametersAsMap() {
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", this.temperature);
        params.put("top_p", this.topP);

        if (this.maxTokens != null) params.put("max_tokens", this.maxTokens);
        if (this.repetitionPenalty != null) params.put("repetition_penalty", this.repetitionPenalty);
        if (this.presencePenalty != null) params.put("presence_penalty", this.presencePenalty);
        if (this.frequencyPenalty != null) params.put("frequency_penalty", this.frequencyPenalty);
        return params;
    }

    // --- Getters and Setters ---
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Double getRepetitionPenalty() { return repetitionPenalty; }
    public void setRepetitionPenalty(Double repetitionPenalty) { this.repetitionPenalty = repetitionPenalty; }
    public Double getPresencePenalty() { return presencePenalty; }
    public void setPresencePenalty(Double presencePenalty) { this.presencePenalty = presencePenalty; }
    public Double getFrequencyPenalty() { return frequencyPenalty; }
    public void setFrequencyPenalty(Double frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; }
}