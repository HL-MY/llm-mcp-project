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
    // 将 planData 的值类型改为 Plan 对象，以便存储和检索
    private Map<String, Plan> planAliasMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try (InputStream inputStream = getClass().getResourceAsStream("/mcp_data.json")) {
            if (inputStream == null) {
                log.error("❌ 关键错误：在类路径的根目录下找不到数据文件 'mcp_data.json'。");
                throw new IllegalStateException("Data file 'mcp_data.json' not found in classpath.");
            }
            ObjectMapper mapper = new ObjectMapper();
            List<Plan> plans = mapper.readValue(inputStream, new TypeReference<>() {});

            // 构建一个包含所有别名的查找映射
            this.planAliasMap = new HashMap<>();
            for (Plan plan : plans) {
                // 1. 添加正式名称作为键
                planAliasMap.put(plan.getName(), plan);
                // 2. 如果存在别名，添加所有别名作为键
                if (plan.getAliases() != null) {
                    for (String alias : plan.getAliases()) {
                        // 使用 computeIfAbsent 避免覆盖，并可以记录别名冲突
                        planAliasMap.computeIfAbsent(alias, k -> {
                            log.info("为套餐 '{}' 映射别名: '{}'", plan.getName(), k);
                            return plan;
                        });
                    }
                }
            }

            log.info("✅ PlanService: 成功加载 {} 个套餐数据，并构建了别名映射。", plans.size());
        } catch (Exception e) {
            log.error("❌ PlanService: 初始化套餐数据时发生严重错误。", e);
        }
    }

    public Map<String, Plan> compareTwoPlans(String planName1, String planName2) {
        Map<String, Plan> result = new HashMap<>();
        // 使用新的别名映射进行查找
        Plan p1 = planAliasMap.get(planName1);
        if (p1 != null) {
            // 始终使用 plan 的正式名称作为结果的 key，保持一致性
            result.put(p1.getName(), p1);
        } else {
            log.warn("比较套餐时未找到: {}", planName1);
        }

        Plan p2 = planAliasMap.get(planName2);
        if (p2 != null) {
            result.put(p2.getName(), p2);
        } else {
            log.warn("比较套餐时未找到: {}", planName2);
        }
        return result;
    }

}