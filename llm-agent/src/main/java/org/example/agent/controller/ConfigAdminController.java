package org.example.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agent.db.entity.Strategy;
import org.example.agent.dto.ModelParameters;
import org.example.agent.service.ConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 【新增】
 * 这是一个全新的 Controller，专门用于前端UI的实时、持久化配置。
 * 它直接与数据库交互。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigAdminController {

    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    public ConfigAdminController(ConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    // --- 策略 (Strategy) API ---

    @GetMapping("/strategies")
    public ResponseEntity<List<Strategy>> getAllStrategies() {
        return ResponseEntity.ok(configService.getAllStrategies());
    }

    @PostMapping("/strategies")
    public ResponseEntity<Strategy> createStrategy(@RequestBody Strategy strategy) {
        return ResponseEntity.ok(configService.createStrategy(strategy));
    }

    @PutMapping("/strategies/{id}")
    public ResponseEntity<Strategy> updateStrategy(@PathVariable Integer id, @RequestBody Strategy strategy) {
        strategy.setId(id); // 确保 ID 一致
        return ResponseEntity.ok(configService.saveStrategy(strategy));
    }

    @DeleteMapping("/strategies/{id}")
    public ResponseEntity<Void> deleteStrategy(@PathVariable Integer id) {
        configService.deleteStrategy(id);
        return ResponseEntity.ok().build();
    }

    // --- 全局设置 (GlobalSetting) API ---

    /**
     * 获取所有全局设置 (包括被序列化为JSON字符串的模型参数)
     */
    @GetMapping("/global-settings")
    public ResponseEntity<Map<String, String>> getAllGlobalSettings() {
        return ResponseEntity.ok(configService.getAllGlobalSettings());
    }

    /**
     * 批量保存全局设置 (扁平的 Key-Value)
     */
    @PutMapping("/global-settings")
    public ResponseEntity<Void> saveGlobalSettings(@RequestBody Map<String, String> settings) {
        configService.saveGlobalSettings(settings);
        return ResponseEntity.ok().build();
    }

    /**
     * 【新增】专门用于保存模型参数的API
     * 前端发送一个嵌套的 ModelParameters DTO，后端将其序列化为JSON字符串保存
     */
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
}