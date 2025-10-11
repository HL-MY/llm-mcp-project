package example.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mcp.service.PlanService; // 【核心】直接导入并使用后端模块的Service
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ToolService {

    private static final Logger log = LoggerFactory.getLogger(ToolService.class);
    private final PlanService planService; // 【核心】直接注入PlanService
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolService(PlanService planService) { // Spring会自动从mcp-backend模块找到Bean并注入
        this.planService = planService;
    }

    public String queryAllPlans() {
        log.info("ToolService: 正在直接调用 PlanService 获取所有套餐...");
        try {
            return mapper.writeValueAsString(planService.getAllPlanNames());
        } catch (Exception e) {
            log.error("调用 PlanService queryAllPlans 失败", e);
            return "{\"error\": \"无法获取套餐列表\"}";
        }
    }

    public String compareTwoPlans(String planName1, String planName2) {
        log.info("ToolService: 正在直接调用 PlanService 比较套餐...");
        try {
            return mapper.writeValueAsString(planService.compareTwoPlans(planName1, planName2));
        } catch (Exception e) {
            log.error("调用 PlanService compareTwoPlans 失败", e);
            return "{\"error\": \"无法比较套餐\"}";
        }
    }
}