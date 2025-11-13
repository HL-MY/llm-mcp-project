package org.example.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agent.db.entity.DecisionRule; // 【新增】
import org.example.agent.db.entity.Strategy;
import org.example.agent.db.mapper.DecisionRuleMapper; // 【新增】
import org.example.agent.dto.ModelParameters;
import org.example.agent.factory.TelecomToolFactory;
import org.example.agent.service.ConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 这是一个全新的 Controller，专门用于前端UI的实时、持久化配置。
 * 它直接与数据库交互。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigAdminController {

    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    private final DecisionRuleMapper decisionRuleMapper; // 【新增】

    // 【新增】内部 DTO 用于工具状态传输
    public record ToolStatus(String name, String description, boolean isActive) {}

    public ConfigAdminController(ConfigService configService, ObjectMapper objectMapper, DecisionRuleMapper decisionRuleMapper) { // 【修改】
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.decisionRuleMapper = decisionRuleMapper; // 【新增】
    }

    // --- 策略 (Strategy) API ---
    // (保持不变)
    @GetMapping("/strategies")
    public ResponseEntity<List<Strategy>> getAllStrategies() {
        return ResponseEntity.ok(configService.getAllStrategies());
    }

    @PostMapping("/strategies")
    public ResponseEntity<Strategy> createStrategy(@RequestBody Strategy strategy) {
        return ResponseEntity.ok(configService.createStrategy(strategy));
    }

    @PutMapping("/strategies/{id}")
    public ResponseEntity<Strategy> updateStrategy(@PathVariable("id") Integer id, @RequestBody Strategy strategy) {
        strategy.setId(id); // 确保 ID 一致
        return ResponseEntity.ok(configService.saveStrategy(strategy));
    }

    @DeleteMapping("/strategies/{id}")
    public ResponseEntity<Void> deleteStrategy(@PathVariable("id") Integer id) {
        configService.deleteStrategy(id);
        return ResponseEntity.ok().build();
    }

    // --- 【新增】决策规则库 (DecisionRule) API ---

    @GetMapping("/rules")
    public ResponseEntity<List<DecisionRule>> getAllRules() {
        return ResponseEntity.ok(decisionRuleMapper.selectList(null));
    }

    @PostMapping("/rules")
    public ResponseEntity<DecisionRule> createRule(@RequestBody DecisionRule rule) {
        rule.setId(null); // 确保是插入
        decisionRuleMapper.insert(rule);
        return ResponseEntity.ok(rule);
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<DecisionRule> updateRule(@PathVariable("id") Integer id, @RequestBody DecisionRule rule) {
        rule.setId(id); // 确保 ID 一致
        decisionRuleMapper.updateById(rule);
        return ResponseEntity.ok(rule);
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable("id") Integer id) {
        decisionRuleMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }


    // --- 全局设置 (GlobalSetting) API ---
    // (保持不变)
    @GetMapping("/global-settings")
    public ResponseEntity<Map<String, String>> getAllGlobalSettings() {
        return ResponseEntity.ok(configService.getAllGlobalSettings());
    }

    @PutMapping("/global-settings")
    public ResponseEntity<Void> saveGlobalSettings(@RequestBody Map<String, String> settings) {
        configService.saveGlobalSettings(settings);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/global-settings/model/{key}")
    public ResponseEntity<Void> saveModelParameters(
            @PathVariable String key,
            @RequestBody ModelParameters params) throws Exception {

        // 验证 key
        if (!ConfigService.KEY_MAIN_MODEL.equals(key) && !ConfigService.KEY_PRE_MODEL.equals(key)) {
            return ResponseEntity.badRequest().build();
        }

        String jsonValue = objectMapper.writeValueAsString(params);
        configService.saveGlobalSettings(Map.of(key, jsonValue));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/tools")
    public ResponseEntity<List<ToolStatus>> getAllToolsStatus() {
        Map<String, String> descriptions = TelecomToolFactory.getAllToolDescriptions();

        List<ToolStatus> statuses = descriptions.entrySet().stream()
                .map(entry -> {
                    String toolName = entry.getKey();
                    // 动态构造配置键，例如: enable_tool_compareTwoPlans
                    String configKey = "enable_tool_" + toolName;
                    String isActiveStr = configService.getGlobalSetting(configKey, "false");
                    boolean isActive = "true".equalsIgnoreCase(isActiveStr);
                    return new ToolStatus(toolName, entry.getValue(), isActive);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(statuses);
    }
}