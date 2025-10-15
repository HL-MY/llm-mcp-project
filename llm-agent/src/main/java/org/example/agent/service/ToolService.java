package org.example.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mcp.service.FaqService;
import org.example.mcp.service.PlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ToolService {

    private static final Logger log = LoggerFactory.getLogger(ToolService.class);
    private final PlanService planService;

    private final FaqService faqService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolService(PlanService planService, FaqService faqService) {
        this.planService = planService;
        this.faqService = faqService;
    }

    public String compareTwoPlans(String planName1, String planName2) {
        log.info("ToolService: 正在直接调用 PlanService 比较套餐: {} vs {}", planName1, planName2);
        try {
            return mapper.writeValueAsString(planService.compareTwoPlans(planName1, planName2));
        } catch (Exception e) {
            log.error("调用 PlanService compareTwoPlans 失败", e);
            return "{\"error\": \"无法比较套餐\"}";
        }
    }

    /**
     * 新增：执行FAQ查询的工具方法
     *
     * @param intent 从模型传入的用户意图
     * @return 从FaqService获取的标准答案
     */
    public String queryMcpFaq(String intent) {
        log.info("ToolService: 正在调用 FaqService 查询FAQ, 意图: {}", intent);
        try {
            // 直接调用 faqService 并返回其结果
            return faqService.getAnswerForIntent(intent);
        } catch (Exception e) {
            log.error("调用 FaqService queryMcpFaq 失败", e);
            return "{\"error\": \"无法查询常见问题答案\"}";
        }
    }
}