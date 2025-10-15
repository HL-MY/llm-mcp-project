package org.example.agent.dto;

import java.util.Map;

public class UiState {
    private final Map<String, String> processStatus;
    private final String persona;
    private final String rawPersonaTemplate;
    private final String openingMonologue;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;

    private final Double repetitionPenalty;
    private final Double presencePenalty;
    private final Double frequencyPenalty;


    // --- 构造函数已更新 ---
    public UiState(Map<String, String> processStatus, String persona, String rawPersonaTemplate, String openingMonologue,
                   String modelName, Double temperature, Double topP, Integer maxTokens,
                   Double repetitionPenalty, Double presencePenalty, Double frequencyPenalty) {
        this.processStatus = processStatus;
        this.persona = persona;
        this.rawPersonaTemplate = rawPersonaTemplate;
        this.openingMonologue = openingMonologue;
        this.modelName = modelName;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.repetitionPenalty = repetitionPenalty;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
    }

    // --- Getters ---
    public Map<String, String> getProcessStatus() { return processStatus; }
    public String getPersona() { return persona; }
    public String getRawPersonaTemplate() { return rawPersonaTemplate; }
    public String getOpeningMonologue() { return openingMonologue; }
    public String getModelName() { return modelName; }
    public Double getTemperature() { return temperature; }
    public Double getTopP() { return topP; }
    public Integer getMaxTokens() { return maxTokens; }
    public Double getRepetitionPenalty() { return repetitionPenalty; }
    public Double getPresencePenalty() { return presencePenalty; }
    public Double getFrequencyPenalty() { return frequencyPenalty; }

}