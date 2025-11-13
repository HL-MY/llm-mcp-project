package org.example.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.mcp.db.entity.FaqKb;
import org.example.mcp.db.mapper.FaqMapper; // <-- 【修改】
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * 【重构】
 * 此服务现在从数据库 (faq_kb) 读取数据，
 * 不再依赖于类路径下的 faq.json 文件。
 */
@Service
public class FaqService {

    private static final Logger log = LoggerFactory.getLogger(FaqService.class);
    private final FaqMapper faqMapper; // <-- 【修改】注入 Mapper
    // ObjectMapper 仍然保留，因为我们返回的是一个 JSON 字符串
    private final ObjectMapper mapper = new ObjectMapper();

    public FaqService(FaqMapper faqMapper) { // <-- 【修改】
        this.faqMapper = faqMapper;
    }

    /**
     * 【删除】
     * init() 方法不再需要，数据已在数据库中。
     */
    // @PostConstruct
    // public void init() { ... }

    /**
     * 【重构】根据意图（问题）从数据库获取标准答案
     * @param intent 用户的核心问题，与faq_kb表中的key对应
     * @return 对应的答案，如果找不到则返回提示信息
     */
    public String getAnswerForIntent(String intent) {
        log.info("FaqService: 正在从数据库查询FAQ, 意图: '{}'", intent);

        if (intent == null || intent.trim().isEmpty()) {
            log.warn("FaqService: 查询意图为空或空白字符串。");
            return "{\"error\": \"查询意图不能为空\"}";
        }

        // 【修改】使用 MyBatis-Plus QueryWrapper 按 intent_key 查询
        // SELECT * FROM faq_kb WHERE intent_key = ?
        QueryWrapper<FaqKb> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("intent_key", intent);
        FaqKb faq = faqMapper.selectOne(queryWrapper);

        if (faq == null) {
            log.warn("FaqService: 在FAQ数据中未找到意图 '{}' 对应的答案。", intent);
            return String.format("{\"error\": \"没有找到关于 '%s' 的标准回答。\"}", intent);
        }

        String answer = faq.getAnswerText();
        log.info("FaqService: 为意图 '{}' 成功找到答案。", intent);

        // 保持返回的 JSON 格式与以前一致，ToolService 无需改动
        return String.format("{\"intent\": \"%s\", \"answer\": \"%s\"}", intent, answer);
    }
}