package org.example.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * @author hull
 * @since 2025/10/15 10:47
 */
@Service
public class FaqService {

    private static final Logger log = LoggerFactory.getLogger(FaqService.class);
    private Map<String, String> faqData = Collections.emptyMap();

    @PostConstruct
    public void init() {
        try (InputStream inputStream = getClass().getResourceAsStream("/faq.json")) {
            if (inputStream == null) {
                log.error("❌ 关键错误：在类路径的根目录下找不到数据文件 'faq.json'。");
                throw new IllegalStateException("Data file 'faq.json' not found in classpath.");
            }
            ObjectMapper mapper = new ObjectMapper();
            this.faqData = mapper.readValue(inputStream, new TypeReference<>() {});
            log.info("✅ FaqService: 成功加载 {} 条FAQ数据。", faqData.size());
        } catch (Exception e) {
            log.error("❌ FaqService: 初始化FAQ数据时发生严重错误。", e);
        }
    }

    /**
     * 根据意图（问题）获取标准答案
     * @param intent 用户的核心问题，与faq.json中的key对应
     * @return 对应的答案，如果找不到则返回提示信息
     */
    public String getAnswerForIntent(String intent) {
        log.info("FaqService: 正在查询FAQ, 意图: '{}'", intent); // <-- 新增日志

        if (intent == null || intent.trim().isEmpty()) {
            log.warn("FaqService: 查询意图为空或空白字符串。"); // <-- 新增日志
            return "{\"error\": \"查询意图不能为空\"}";
        }

        String answer = faqData.get(intent);
        if (answer == null) {
            // 这条日志您已经有了，它对于排查模型提取意图是否正确非常关键
            log.warn("FaqService: 在FAQ数据中未找到意图 '{}' 对应的答案。", intent);
            return String.format("{\"error\": \"没有找到关于 '%s' 的标准回答。\"}", intent);
        }

        log.info("FaqService: 为意图 '{}' 成功找到答案。", intent); // <-- 新增日志
        return String.format("{\"intent\": \"%s\", \"answer\": \"%s\"}", intent, answer);
    }
}
