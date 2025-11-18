package org.example.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.mcp.db.entity.Plan; // <-- 【修改】
import org.example.mcp.db.entity.PlanAlias; // <-- 【修改】
import org.example.mcp.db.mapper.PlanAliasMapper; // <-- 【修改】
import org.example.mcp.db.mapper.PlanMapper; // <-- 【修改】
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
 * 【重构】
 * 此服务现在从数据库 (plan, plan_alias) 读取数据，
 * 不再依赖于 mcp_data.json 文件。
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);
    private final PlanMapper planMapper; // <-- 【修改】
    private final PlanAliasMapper planAliasMapper; // <-- 【修改】
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanService(PlanMapper planMapper, PlanAliasMapper planAliasMapper) { // <-- 【修改】
        this.planMapper = planMapper;
        this.planAliasMapper = planAliasMapper;
    }

    /**
     * 【删除】
     * init() 方法不再需要，数据已在数据库中。
     */
    // @PostConstruct
    // public void init() { ... }

    /**
     * 【重构】从数据库比较两个套餐
     * @return 返回 Map<String, Plan>，其中 Plan 是包含 detailsJson 的 POJO/实体
     */
    public Map<String, Plan> compareTwoPlans(String planName1, String planName2) {
        Map<String, Plan> result = new HashMap<>();

        Plan p1 = findPlanByNameOrAlias(planName1);
        if (p1 != null) {
            result.put(p1.getPlanName(), p1);
        } else {
            log.warn("比较套餐时未找到: {}", planName1);
        }

        Plan p2 = findPlanByNameOrAlias(planName2);
        if (p2 != null) {
            result.put(p2.getPlanName(), p2);
        } else {
            log.warn("比较套餐时未找到: {}", planName2);
        }

        // 返回值 (Map<String, Plan>) 保持不变，ToolService 可以正常序列化它
        // POJO中的 'detailsJson' 字段会被 Jackson 自动序列化
        return result;
    }

    /**
     * 【重构】辅助方法：通过名称或别名查找套餐
     */
    private Plan findPlanByNameOrAlias(String name) {
        // 1. 尝试按别名查找
        // SELECT * FROM plan_alias WHERE alias_name = ? LIMIT 1
        QueryWrapper<PlanAlias> aliasQuery = new QueryWrapper<>();
        aliasQuery.eq("alias_name", name);
        PlanAlias alias = planAliasMapper.selectOne(aliasQuery);

        if (alias != null) {
            // 2. 如果找到别名，通过 plan_id 查找主套餐
            log.debug("通过别名 '{}' 找到 planId: {}", name, alias.getPlanId());
            // SELECT * FROM plan WHERE id = ?
            return planMapper.selectById(alias.getPlanId());
        }

        // 3. 如果别名未找到，尝试按主名称查找
        // SELECT * FROM plan WHERE plan_name = ? LIMIT 1
        QueryWrapper<Plan> planQuery = new QueryWrapper<>();
        planQuery.eq("plan_name", name);
        Plan plan = planMapper.selectOne(planQuery);

        if (plan != null) {
            log.debug("通过主名称 '{}' 找到 planId: {}", name, plan.getId());
        }

        return plan;
    }
}