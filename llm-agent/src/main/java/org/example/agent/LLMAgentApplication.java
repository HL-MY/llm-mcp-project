package org.example.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @author hull
 * @since 2025/9/17 11:33
 */
@EnableFeignClients(basePackages = "org.example.llm.client")
@SpringBootApplication(scanBasePackages = "org.example")
public class LLMAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(LLMAgentApplication.class, args);
    }
}
