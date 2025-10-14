package org.example.llm.client;

import org.example.llm.dto.api.qwen.QwenApiReq;
import org.example.llm.dto.api.qwen.QwenApiResp;


@FeignClient(name = "qianwenClient", url = "https://dashscope.aliyuncs.com")
public interface QianwenClient {

    @PostMapping(path = "/api/v1/services/aigc/text-generation/generation")
    QwenApiResp chatCompletions(
            @RequestHeader("Authorization") String authorization,
            @RequestBody QwenApiReq req
    );
}