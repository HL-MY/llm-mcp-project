package org.example.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agent.db.entity.DecisionRule;
import org.example.agent.db.entity.Strategy;
import org.example.agent.db.mapper.DecisionRuleMapper;
import org.example.agent.dto.ModelParameters;
import org.example.agent.factory.TelecomToolFactory;
import org.example.agent.service.ConfigService;
import org.example.llm.dto.tool.ParameterProperty;
import org.example.llm.dto.tool.ParameterSchema;
import org.example.llm.dto.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/config")
public class ConfigAdminController {

    private static final Logger log = LoggerFactory.getLogger(ConfigAdminController.class);

    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    private final DecisionRuleMapper decisionRuleMapper;

    // 【修复编译错误】使用 Map.ofEntries 解决参数数量超过 10 对的限制
    private static final Map<String, String> TOOL_LOCALIZATION = Map.ofEntries(
            Map.entry("compareTwoPlans", "比较套餐"),
            Map.entry("queryMcpFaq", "查询套餐FAQ"),
            Map.entry("getWeather", "查询天气"),
            Map.entry("getOilPrice", "查询油价"),
            Map.entry("getGoldPrice", "查询金价"),
            Map.entry("getNews", "查询新闻"),
            Map.entry("getExchangeRate", "查询汇率"),
            Map.entry("getFundInfo", "查询基金信息"),
            Map.entry("getCurrentTimeByCity", "查询城市时间"),
            Map.entry("getStockInfo", "查询股票信息"),
            Map.entry("webSearch", "联网搜索")
    );

    // 【修改】新增 chineseName 和 parameters 字段
    public record ToolStatus(String name, String chineseName, String description, String parameters, boolean isActive) {}

    public ConfigAdminController(ConfigService configService, ObjectMapper objectMapper, DecisionRuleMapper decisionRuleMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.decisionRuleMapper = decisionRuleMapper;
    }

    @GetMapping("/global-settings")
    public ResponseEntity<Map<String, String>> getAllGlobalSettings() {
        log.info("收到前端请求: 获取所有全局配置 (GET /api/config/global-settings)");
        Map<String, String> settings = configService.getAllGlobalSettings();
        log.info("数据库查询完毕，共返回 {} 条配置项", settings.size());
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/global-settings")
    @Transactional
    public ResponseEntity<Void> saveGlobalSettings(@RequestBody Map<String, String> settings) {
        log.info("收到保存配置请求: {}", settings.keySet());
        configService.saveGlobalSettings(settings);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/global-settings/model/{key}")
    @Transactional
    public ResponseEntity<Void> saveModelParameters(
            @PathVariable String key,
            @RequestBody ModelParameters params) throws Exception {
        log.info("收到保存模型参数请求: key={}", key);
        if (!ConfigService.KEY_MAIN_MODEL.equals(key)
                && !ConfigService.KEY_PRE_MODEL.equals(key)
                && !ConfigService.KEY_ROUTER_MODEL.equals(key)) {
            return ResponseEntity.badRequest().build();
        }
        String jsonValue = objectMapper.writeValueAsString(params);
        configService.saveGlobalSettings(Map.of(key, jsonValue));
        return ResponseEntity.ok().build();
    }

    // 【新增】获取中文工具名称
    private String getChineseToolName(String toolName) {
        return TOOL_LOCALIZATION.getOrDefault(toolName, toolName);
    }

    // 【新增】获取参数概括（包含描述）
    private String getParameterSummary(ToolDefinition tool) {
        ParameterSchema schema = tool.getFunction().getParameters();
        if (schema == null || schema.getProperties() == null || schema.getProperties().isEmpty()) {
            return "无参数";
        }

        StringBuilder sb = new StringBuilder();
        List<String> requiredList = schema.getRequired() != null ? schema.getRequired() : Collections.emptyList();

        // 按字母顺序排列参数名，保证输出稳定
        List<String> sortedParamNames = schema.getProperties().keySet().stream().sorted().collect(Collectors.toList());

        for (String paramName : sortedParamNames) {
            ParameterProperty prop = schema.getProperties().get(paramName);
            String description = prop.getDescription();
            boolean isRequired = requiredList.contains(paramName);

            sb.append(paramName);
            sb.append(" (");

            // 提取 description 的关键信息作为提示
            if (description != null && !description.isEmpty()) {
                // 仅使用描述的第一句话或第一部分作为上下文
                sb.append(description.split("，")[0].split("。")[0].split("。")[0]);
                if (isRequired) {
                    sb.append(", ");
                }
            }

            if (isRequired) {
                sb.append("必填");
            } else {
                // 如果非必填且没描述，避免空括号
                if (description == null || description.isEmpty()) sb.append("非必填");
            }

            sb.append("), ");
        }
        // 移除末尾的 ", "
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }

    // --- ToolStatus 获取逻辑修改，以包含中文名称和参数描述 ---
    @GetMapping("/tools")
    public ResponseEntity<List<ToolStatus>> getAllToolsStatus() {
        log.info("收到获取工具列表请求: GET /api/config/tools");

        // 1. 获取所有工具定义和自定义描述
        Map<String, String> customDescriptions = configService.getAllToolDescriptions();
        List<ToolDefinition> definitions = TelecomToolFactory.getAllToolDefinitions(customDescriptions);

        // 2. 遍历并构建包含中文名称和参数的列表
        List<ToolStatus> statuses = definitions.stream()
                .map(def -> {
                    String toolName = def.getFunction().getName();
                    String configKey = "enable_tool_" + toolName;
                    String isActiveStr = configService.getGlobalSetting(configKey, "false");
                    boolean isActive = "true".equalsIgnoreCase(isActiveStr);

                    String chineseName = getChineseToolName(toolName);
                    String currentDescription = def.getFunction().getDescription();
                    String parameterSummary = getParameterSummary(def);

                    // 返回原始 toolName 在 'name' 字段，确保前端 JS 绑定 key 正常
                    return new ToolStatus(toolName, chineseName, currentDescription, parameterSummary, isActive);
                })
                .collect(Collectors.toList());

        log.info("返回工具数量: {}", statuses.size());
        return ResponseEntity.ok(statuses);
    }

    // ... (DecisionRule 和 Strategy API 保持不变) ...

    @GetMapping("/rules")
    public ResponseEntity<List<DecisionRule>> getAllRules() { return ResponseEntity.ok(decisionRuleMapper.selectList(null)); }

    @PostMapping("/rules")
    @Transactional
    public ResponseEntity<DecisionRule> createRule(@RequestBody DecisionRule rule) {
        rule.setId(null);
        decisionRuleMapper.insert(rule);
        return ResponseEntity.ok(rule);
    }

    @PutMapping("/rules/{id}")
    @Transactional
    public ResponseEntity<DecisionRule> updateRule(@PathVariable("id") Integer id, @RequestBody DecisionRule rule) {
        rule.setId(id);
        decisionRuleMapper.updateById(rule);
        return ResponseEntity.ok(rule);
    }

    @DeleteMapping("/rules/{id}")
    @Transactional
    public ResponseEntity<Void> deleteRule(@PathVariable("id") Integer id) {
        decisionRuleMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/strategies")
    public ResponseEntity<List<Strategy>> getAllStrategies() { return ResponseEntity.ok(configService.getAllStrategies()); }

    @PostMapping("/strategies")
    public ResponseEntity<Strategy> createStrategy(@RequestBody Strategy strategy) { return ResponseEntity.ok(configService.createStrategy(strategy)); }

    @PutMapping("/strategies/{id}")
    public ResponseEntity<Strategy> updateStrategy(@PathVariable("id") Integer id, @RequestBody Strategy strategy) {
        strategy.setId(id);
        return ResponseEntity.ok(configService.saveStrategy(strategy));
    }

    @DeleteMapping("/strategies/{id}")
    public ResponseEntity<Void> deleteStrategy(@PathVariable("id") Integer id) {
        configService.deleteStrategy(id);
        return ResponseEntity.ok().build();
    }
}