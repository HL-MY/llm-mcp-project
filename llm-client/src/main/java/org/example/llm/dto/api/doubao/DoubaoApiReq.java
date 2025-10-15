package org.example.llm.dto.api.doubao;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import org.example.llm.dto.tool.ToolDefinition;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DoubaoApiReq {
    private String model;
    private List<DoubaoMessage> messages;
    private Double temperature;
    @JsonProperty("top_p")
    private Double topP;
    private Integer n;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    @JsonProperty("presence_penalty")
    private Double presencePenalty;
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;
    private String user;

    private List<ToolDefinition> tools;
}