package example.agent.dto;
import java.util.List;
public class ConfigurationRequest {
    private List<String> processes;
    private String personaTemplate;
    private String dependencies;
    private String openingMonologue;
    private String modelName;
    private Double temperature;
    private Double topP;

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
}