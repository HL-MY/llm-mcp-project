package org.example.llm.client;


import org.example.dto.api.doubao.DoubaoApiReq;
import org.example.dto.api.doubao.DoubaoApiResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 这是一个 Feign 客户端接口，用于声明式地调用豆包（火山方舟）的 API。
 * Spring Cloud OpenFeign 会在运行时自动为这个接口创建一个实现类。
 */
@FeignClient(name = "doubaoClient", url = "https://ark.cn-beijing.volces.com/api/v3")
public interface DoubaoClient {

    @PostMapping(path = "/chat/completions")
    DoubaoApiResp chatCompletions(
            @RequestHeader("Authorization") String authorization,
            @RequestBody DoubaoApiReq req
    );
}