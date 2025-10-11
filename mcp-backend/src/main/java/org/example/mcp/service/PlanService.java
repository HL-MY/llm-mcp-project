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

/**
 * 后端业务核心服务 (mcp-backend 模块)
 * 职责：专门负责套餐数据的读取和基础业务逻辑。
 * 这是一个纯粹的业务Bean，不关心AI或Web请求。
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);
    private Map<String, Plan> planData = Collections.emptyMap();

    /**
     * @PostConstruct 注解确保在服务启动并初始化后，
     * 这个 init 方法会被自动执行一次，从而将套餐数据加载到内存中。
     */
    @PostConstruct
    public void init() {
        // 使用 try-with-resources 确保 InputStream 会被自动关闭
        try (InputStream inputStream = getClass().getResourceAsStream("/mcp_data.json")) {
            if (inputStream == null) {
                log.error("❌ 关键错误：在类路径的根目录下找不到数据文件 'mcp_data.json'。");
                throw new IllegalStateException("Data file 'mcp_data.json' not found in classpath.");
            }
            ObjectMapper mapper = new ObjectMapper();
            // 使用 TypeReference 来帮助 Jackson 正确地反序列化泛型列表
            List<Plan> plans = mapper.readValue(inputStream, new TypeReference<>() {});
            // 将列表转换为一个以套餐名称为键(key)的Map，便于快速查找
            this.planData = plans.stream().collect(Collectors.toMap(Plan::getName, Function.identity()));
            log.info("✅ PlanService: 成功加载 {} 个套餐数据。", planData.size());
        } catch (Exception e) {
            log.error("❌ PlanService: 初始化套餐数据时发生严重错误。", e);
        }
    }

    /**
     * 获取所有套餐的名称列表。
     * @return 一个包含所有套餐名称的集合 (Set)
     */
    public Set<String> getAllPlanNames() {
        return planData.keySet();
    }

    /**
     * 根据提供的两个套餐名称，查找并返回它们的详细信息。
     * @param planName1 第一个套餐的名称
     * @param planName2 第二个套餐的名称
     * @return 一个Map，键是套餐名称，值是套餐的详细信息对象 (Plan DTO)。如果某个套餐找不到，则不会包含在返回的Map中。
     */
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