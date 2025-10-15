package org.example.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.mcp.dto.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);
    private Map<String, Plan> planData = Collections.emptyMap();

    @PostConstruct
    public void init() {
        try (InputStream inputStream = getClass().getResourceAsStream("/mcp_data.json")) {
            if (inputStream == null) {
                log.error("❌ 关键错误：在类路径的根目录下找不到数据文件 'mcp_data.json'。");
                throw new IllegalStateException("Data file 'mcp_data.json' not found in classpath.");
            }
            ObjectMapper mapper = new ObjectMapper();
            List<Plan> plans = mapper.readValue(inputStream, new TypeReference<>() {});
            this.planData = plans.stream().collect(Collectors.toMap(Plan::getName, Function.identity()));
            log.info("✅ PlanService: 成功加载 {} 个套餐数据。", planData.size());
        } catch (Exception e) {
            log.error("❌ PlanService: 初始化套餐数据时发生严重错误。", e);
        }
    }

    public Map<String, Plan> compareTwoPlans(String planName1, String planName2) {
        Map<String, Plan> result = new HashMap<>();
        Plan p1 = planData.get(planName1);
        if (p1 != null) {
            result.put(planName1, p1);
        } else {
            log.warn("比较套餐时未找到: {}", planName1);
        }
        Plan p2 = planData.get(planName2);
        if (p2 != null) {
            result.put(planName2, p2);
        } else {
            log.warn("比较套餐时未找到: {}", planName2);
        }
        return result;
    }

}