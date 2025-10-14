package org.example.llm.dto.api.doubao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DoubaoApiReq {
    /**
     * 模型名称 (对于豆包，这里是 Endpoint ID)
     */
    private String model;

    /**
     * 对话消息列表
     */
    private List<DoubaoMessage> messages;

    /**
     * 是否流式返回
     */
    @Builder.Default
    private boolean stream = false;

    /**
     * 控制生成文本的多样性。值越高，输出越随机。 (0.0 to 2.0)
     */
    private Double temperature;

    /**
     * 控制核心词的概率。值越低，越倾向于高频词。 (0.0 to 1.0)
     */
    @JsonProperty("top_p")
    private Double topP;

    /**
     * 【新增】为每个输入消息生成多少个聊天完成选择。
     */
    private Integer n;

    /**
     * 【新增】API 将停止生成更多令牌的最多4个序列。
     */
    private List<String> stop;

    /**
     * 【新增】聊天完成时要生成的最大令牌数。
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /**
     * 【新增】存在惩罚：-2.0到2.0之间的数字。正值会根据新令牌是否出现在文本中来惩罚它们，从而增加模型谈论新主题的可能性。
     */
    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    /**
     * 【新增】频率惩罚：-2.0到2.0之间的数字。正值会根据新令牌在文本中的现有频率来惩罚它们，从而降低模型逐字重复同一行的可能性。
     */
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    /**
     * 【新增】一个唯一的标识符，代表您的最终用户，可以帮助 OpenAI 监控和检测滥用行为。
     */
    private String user;
}