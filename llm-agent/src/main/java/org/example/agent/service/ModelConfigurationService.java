package org.example.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import java.util.HashMap;
import java.util.Map;

@Service
@SessionScope
public class ModelConfigurationService {

    private String modelName = "qwen3-next-80b-a3b-instruct";
    private Double temperature = 0.7;
    private Double topP = 0.8;
    private Integer maxTokens = null;
    private Double repetitionPenalty = null;
    private Double presencePenalty = null;
    private Double frequencyPenalty = null;

    // --- Getters ---
    public String getModelName() { return modelName; }
    public Double getTemperature() { return temperature; }
    public Double getTopP() { return topP; }
    public Integer getMaxTokens() { return maxTokens; }
    public Double getRepetitionPenalty() { return repetitionPenalty; }
    public Double getPresencePenalty() { return presencePenalty; }
    public Double getFrequencyPenalty() { return frequencyPenalty; }
    // [已移除] stop, seed, stream 的 Getters

    // --- getParametersAsMap 方法已更新 ---
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

    // --- Setters/Updaters ---
    public void updateModelName(String modelName) {
        if (modelName != null && !modelName.trim().isEmpty()) { this.modelName = modelName; }
    }

    public void updateTemperature(Double temperature) {
        if (temperature != null && temperature >= 0.0 && temperature <= 2.0) { this.temperature = temperature; }
    }

    public void updateTopP(Double topP) {
        if (topP != null && topP > 0.0 && topP <= 1.0) { this.topP = topP; }
    }

    public void updateMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public void updateRepetitionPenalty(Double repetitionPenalty) { this.repetitionPenalty = repetitionPenalty; }
    public void updatePresencePenalty(Double presencePenalty) { this.presencePenalty = presencePenalty; }
    public void updateFrequencyPenalty(Double frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; }
}