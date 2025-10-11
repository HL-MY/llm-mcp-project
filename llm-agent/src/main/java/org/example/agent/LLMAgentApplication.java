package org.example.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author hull
 * @since 2025/9/17 11:33
 */
@SpringBootApplication(scanBasePackages = "org.example")
public class LLMAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(LLMAgentApplication.class, args);
    }
}
