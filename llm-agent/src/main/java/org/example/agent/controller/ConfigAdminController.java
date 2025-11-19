package org.example.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agent.db.entity.DecisionRule;
import org.example.agent.db.entity.Strategy;
import org.example.agent.db.mapper.DecisionRuleMapper;
import org.example.agent.dto.ModelParameters;
import org.example.agent.factory.TelecomToolFactory;
import org.example.agent.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional; // <-- 确保引入
import org.springframework.web.bind.annotation.*;

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

    public record ToolStatus(String name, String description, boolean isActive) {}

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
    @Transactional // 【必需】确保 GlobalSetting 的写入能够被提交
    public ResponseEntity<Void> saveGlobalSettings(@RequestBody Map<String, String> settings) {
        log.info("收到保存配置请求: {}", settings.keySet());
        configService.saveGlobalSettings(settings);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/global-settings/model/{key}")
    @Transactional // 【必需】确保模型参数的写入能够被提交
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

    // --- Decision Rule 规则保存修复 (已添加 @Transactional) ---

    @GetMapping("/rules")
    public ResponseEntity<List<DecisionRule>> getAllRules() { return ResponseEntity.ok(decisionRuleMapper.selectList(null)); }

    @PostMapping("/rules")
    @Transactional // 【修复】新增规则，需要事务来提交
    public ResponseEntity<DecisionRule> createRule(@RequestBody DecisionRule rule) {
        rule.setId(null); // 确保 ID 被置空，让数据库自增
        decisionRuleMapper.insert(rule);
        return ResponseEntity.ok(rule);
    }

    @PutMapping("/rules/{id}")
    @Transactional // 【修复】更新规则，需要事务来提交
    public ResponseEntity<DecisionRule> updateRule(@PathVariable("id") Integer id, @RequestBody DecisionRule rule) {
        rule.setId(id);
        decisionRuleMapper.updateById(rule);
        return ResponseEntity.ok(rule);
    }

    @DeleteMapping("/rules/{id}")
    @Transactional // 【修复】删除规则，需要事务来提交
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

    // --- Tool Methods (保持不变) ---

    @GetMapping("/tools")
    public ResponseEntity<List<ToolStatus>> getAllToolsStatus() {
        log.info("收到获取工具列表请求: GET /api/config/tools");
        Map<String, String> descriptions = TelecomToolFactory.getAllToolDescriptions();
        List<ToolStatus> statuses = descriptions.entrySet().stream()
                .map(entry -> {
                    String toolName = entry.getKey();
                    String configKey = "enable_tool_" + toolName;
                    String isActiveStr = configService.getGlobalSetting(configKey, "false");
                    boolean isActive = "true".equalsIgnoreCase(isActiveStr);
                    return new ToolStatus(toolName, entry.getValue(), isActive);
                })
                .collect(Collectors.toList());
        log.info("返回工具数量: {}", statuses.size());
        return ResponseEntity.ok(statuses);
    }
}