package example.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import java.util.HashMap;
import java.util.Map;

@Service
@SessionScope
public class ModelConfigurationService {

    private String modelName = "qwen3-30b-a3b"; // 默认使用一个标准的、不会出错的模型
    private double temperature = 0.7;
    private double topP = 0.8;

    // --- Getters ---
    public String getModelName() { return modelName; }
    public double getTemperature() { return temperature; }
    public double getTopP() { return topP; }

    /**
     * 【核心修正】
     * 这个方法现在只返回通用的模型参数，不再包含任何特殊参数。
     */
    public Map<String, Object> getParametersAsMap() {
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", this.temperature);
        params.put("top_p", this.topP);
        return params;
    }

    // --- Setters ---
    public void updateModelName(String modelName) {
        if (modelName != null && !modelName.trim().isEmpty()) { this.modelName = modelName; }
    }

    public void updateTemperature(Double temperature) {
        if (temperature != null && temperature >= 0.0 && temperature <= 2.0) { this.temperature = temperature; }
    }

    public void updateTopP(Double topP) {
        if (topP != null && topP > 0.0 && topP <= 1.0) { this.topP = topP; }
    }
}