package org.example.agent.dto;

import java.util.List;

public class ConfigurationRequest {
    private List<String> processes;
    private String personaTemplate;
    private String dependencies;
    private String openingMonologue;
    private String modelName;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Double repetitionPenalty;
    private Double presencePenalty;
    private Double frequencyPenalty;

    // Getters and Setters
    public List<String> getProcesses() { return processes; }
    public void setProcesses(List<String> processes) { this.processes = processes; }
    public String getPersonaTemplate() { return personaTemplate; }
    public void setPersonaTemplate(String personaTemplate) { this.personaTemplate = personaTemplate; }
    public String getDependencies() { return dependencies; }
    public void setDependencies(String dependencies) { this.dependencies = dependencies; }
    public String getOpeningMonologue() { return openingMonologue; }
    public void setOpeningMonologue(String openingMonologue) { this.openingMonologue = openingMonologue; }
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