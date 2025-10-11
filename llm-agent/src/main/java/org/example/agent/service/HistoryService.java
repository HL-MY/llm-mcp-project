package org.example.agent.service;

import com.alibaba.dashscope.common.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);
    private static final String HISTORY_SAVE_PATH = "log";

    public void saveConversationToFile(String path, List<Message> conversation) {
        if (conversation == null || conversation.isEmpty()) {
            return;
        }
        try {
            Path directoryPath = Paths.get(HISTORY_SAVE_PATH);
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = timestamp + ".md";
            Path filePath = directoryPath.resolve(fileName);

            StringBuilder content = new StringBuilder();
            content.append("# Conversation Log - ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
            for (Message message : conversation) {
                String role = message.getRole();
                content.append("## 👤 ").append(role.toUpperCase()).append("\n");
                content.append(message.getContent()).append("\n\n");
            }

            Files.write(filePath, content.toString().getBytes(), StandardOpenOption.CREATE_NEW);
            log.info("✅ 对话历史已成功保存至: {}", filePath.toAbsolutePath());

        } catch (IOException e) {
            log.error("❌ 保存对话历史失败。", e);
        }
    }
}