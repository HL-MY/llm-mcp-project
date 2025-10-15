package org.example.llm.client;

import org.example.llm.dto.api.qwen.QwenApiReq;
import org.example.llm.dto.api.qwen.QwenApiResp;
import org.springframework.cloud.openfeign.FeignClient; // 确保引入
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "qianwenClient", url = "https://dashscope.aliyuncs.com/api/v1")
public interface QianwenClient {

    @PostMapping(path = "/services/aigc/text-generation/generation")
    QwenApiResp chatCompletions(
            @RequestHeader("Authorization") String authorization,
            @RequestBody QwenApiReq req
    );
}