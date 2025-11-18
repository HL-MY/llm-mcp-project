package org.example.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper; // 必须引入这个
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agent.db.entity.GlobalSetting;
import org.example.agent.db.entity.Strategy;
import org.example.agent.db.mapper.GlobalSettingMapper;
import org.example.agent.db.mapper.StrategyMapper;
import org.example.agent.dto.ModelParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    // Key 定义
    public static final String KEY_MAIN_MODEL = "main_model_params";
    public static final String KEY_PRE_MODEL = "pre_model_params";
    public static final String KEY_PERSONA_TEMPLATE = "persona_template";
    public static final String KEY_OPENING_MONOLOGUE = "opening_monologue";
    public static final String KEY_PROCESSES = "processes";
    public static final String KEY_DEPENDENCIES = "dependencies";
    public static final String KEY_SENSITIVE = "sensitive_response";
    public static final String KEY_PRE_PROMPT = "pre_processing_prompt";
    public static final String KEY_SAFETY_REDLINES = "safety_redlines";

    public static final String KEY_ENABLE_STRATEGY = "enable_strategy";
    public static final String KEY_ENABLE_EMOTION = "enable_emotion_recognition";
    public static final String KEY_ENABLE_WORKFLOW = "enable_workflow";
    public static final String KEY_ENABLE_MCP = "enable_mcp";

    private final GlobalSettingMapper globalSettingMapper;
    private final StrategyMapper strategyMapper;
    private final ObjectMapper objectMapper;

    public ConfigService(GlobalSettingMapper globalSettingMapper, StrategyMapper strategyMapper, ObjectMapper objectMapper) {
        this.globalSettingMapper = globalSettingMapper;
        this.strategyMapper = strategyMapper;
        this.objectMapper = objectMapper;
    }

    // --- 核心：动态获取配置 ---

    public Map<String, String> getAllGlobalSettings() {
        List<GlobalSetting> list = globalSettingMapper.selectList(null);
        return list.stream().collect(Collectors.toMap(GlobalSetting::getSettingKey, GlobalSetting::getSettingValue));
    }

    public String getGlobalSetting(String key, String defaultValue) {
        GlobalSetting setting = globalSettingMapper.selectById(key);
        return (setting != null) ? setting.getSettingValue() : defaultValue;
    }

    /**
     * 【核心修复】使用 UpdateWrapper 强制更新，解决主键策略导致的保存失效问题
     */
    @Transactional
    public void saveGlobalSettings(Map<String, String> settings) {
        log.info(">>> 正在批量保存 {} 项配置...", settings.size());

        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();

            // 1. 尝试强制更新 (不依赖 Entity 状态)
            // SQL: UPDATE global_settings SET setting_value = ? WHERE setting_key = ?
            UpdateWrapper<GlobalSetting> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("setting_key", key);
            updateWrapper.set("setting_value", val);

            int rows = globalSettingMapper.update(null, updateWrapper);

            if (rows > 0) {
                log.info("✅ 更新配置成功: {}", key);
            } else {
                // 2. 如果更新影响行数为 0，说明不存在，执行插入
                GlobalSetting newSetting = new GlobalSetting();
                newSetting.setSettingKey(key);
                newSetting.setSettingValue(val);
                globalSettingMapper.insert(newSetting);
                log.info("✨ 新增配置成功: {}", key);
            }
        }
    }

    public ModelParameters getModelParams(String key) {
        String json = getGlobalSetting(key, null);
        if (json == null) return new ModelParameters();
        try {
            return objectMapper.readValue(json, ModelParameters.class);
        } catch (Exception e) {
            log.error("解析模型参数失败: {}", key, e);
            return new ModelParameters();
        }
    }

    // --- 简单 Getters ---
    public List<String> getProcessList() {
        String val = getGlobalSetting(KEY_PROCESSES, "");
        if (val.isEmpty()) return List.of();
        return List.of(val.split("\\r?\\n"));
    }

    public String getDependencies() { return getGlobalSetting(KEY_DEPENDENCIES, ""); }

    public Boolean getEnableStrategy() { return "true".equalsIgnoreCase(getGlobalSetting(KEY_ENABLE_STRATEGY, "false")); }
    public Boolean getEnableEmotionRecognition() { return "true".equalsIgnoreCase(getGlobalSetting(KEY_ENABLE_EMOTION, "false")); }
    public Boolean getEnableWorkflow() { return "true".equalsIgnoreCase(getGlobalSetting(KEY_ENABLE_WORKFLOW, "false")); }
    public Boolean getEnableMcp() { return "true".equalsIgnoreCase(getGlobalSetting(KEY_ENABLE_MCP, "false")); }

    public String getSafetyRedlines() { return getGlobalSetting(KEY_SAFETY_REDLINES, ""); }
    public String getPersonaTemplate() { return getGlobalSetting(KEY_PERSONA_TEMPLATE, ""); }
    public String getOpeningMonologue() { return getGlobalSetting(KEY_OPENING_MONOLOGUE, ""); }
    public String getPreProcessingPrompt() { return getGlobalSetting(KEY_PRE_PROMPT, ""); }
    public String getSensitiveResponse() { return getGlobalSetting(KEY_SENSITIVE, "我们换个话题吧。"); }

    // --- Strategy 相关 ---
    public List<Strategy> getAllStrategies() { return strategyMapper.selectList(null); }
    public Map<String, String> getActiveStrategies(String type) {
        QueryWrapper<Strategy> query = new QueryWrapper<>();
        query.eq("strategy_type", type).eq("is_active", true);
        return strategyMapper.selectList(query).stream().collect(Collectors.toMap(Strategy::getStrategyKey, Strategy::getStrategyValue));
    }
    @Transactional public Strategy saveStrategy(Strategy strategy) {
        if (strategy.getId() == null) strategyMapper.insert(strategy); else strategyMapper.updateById(strategy);
        return strategy;
    }
    @Transactional public Strategy createStrategy(Strategy strategy) { strategy.setId(null); strategyMapper.insert(strategy); return strategy; }
    @Transactional public void deleteStrategy(Integer id) { strategyMapper.deleteById(id); }
}