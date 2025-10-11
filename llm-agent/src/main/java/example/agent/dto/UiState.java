package example.agent.dto;
import java.util.Map;
public class UiState {
    private final Map<String, String> processStatus;
    private final String persona;
    private final String rawPersonaTemplate;
    private final String openingMonologue;
    private final String modelName;
    private final double temperature;
    private final double topP;
    public UiState(Map<String, String> processStatus, String persona, String rawPersonaTemplate, String openingMonologue,
                   String modelName, double temperature, double topP) {
        this.processStatus = processStatus;
        this.persona = persona;
        this.rawPersonaTemplate = rawPersonaTemplate;
        this.openingMonologue = openingMonologue;
        this.modelName = modelName;
        this.temperature = temperature;
        this.topP = topP;
    }
    // Getters
    public Map<String, String> getProcessStatus() { return processStatus; }
    public String getPersona() { return persona; }
    public String getRawPersonaTemplate() { return rawPersonaTemplate; }
    public String getOpeningMonologue() { return openingMonologue; }
    public String getModelName() { return modelName; }
    public double getTemperature() { return temperature; }
    public double getTopP() { return topP; }
}