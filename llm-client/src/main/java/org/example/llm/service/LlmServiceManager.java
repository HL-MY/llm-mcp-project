package org.example.llm.service;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class LlmServiceManager {

    private final List<LlmService> llmServices;

    public LlmServiceManager(List<LlmService> llmServices) {
        this.llmServices = llmServices;
    }

    public LlmService getService(String modelName) {
        return llmServices.stream()
                .filter(service -> service.supports(modelName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的大模型: " + modelName));
    }
}