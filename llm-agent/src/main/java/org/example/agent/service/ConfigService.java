package org.example.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 【新增】
 * 这是一个核心的、单例的配置服务 (Singleton)。
 * 它替代了旧的 StrategyService, ModelConfigurationService, WorkflowStateService。
 * 它从数据库加载所有配置，并提供给 Session-scoped 的 ChatService。
 * 它也被 ConfigAdminController 调用，以实现配置的实时保存。
 */
@Service
@Transactional(readOnly = true) // 默认所有方法都是只读事务
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    // 数据库中 global_settings 表的主键 (Key)
    public static final String KEY_MAIN_MODEL = "main_model_params";
    public static final String KEY_PRE_MODEL = "pre_model_params";
    public static final String KEY_PERSONA_TEMPLATE = "persona_template";
    public static final String KEY_OPENING_MONOLOGUE = "opening_monologue";
    public static final String KEY_PROCESSES = "processes";
    public static final String KEY_DEPENDENCIES = "dependencies";
    public static final String KEY_FALLBACK = "fallback_response";
    public static final String KEY_SENSITIVE = "sensitive_response";
    public static final String KEY_PRE_PROMPT = "pre_processing_prompt";

    private final GlobalSettingMapper globalSettingMapper;
    private final StrategyMapper strategyMapper;
    private final ObjectMapper objectMapper;

    public ConfigService(GlobalSettingMapper globalSettingMapper, StrategyMapper strategyMapper, ObjectMapper objectMapper) {
        this.globalSettingMapper = globalSettingMapper;
        this.strategyMapper = strategyMapper;
        this.objectMapper = objectMapper;
    }

    // --- 策略 (Strategy) 相关 API ---

    public List<Strategy> getAllStrategies() {
        return strategyMapper.selectList(null);
    }

    /**
     * 获取所有被标记为 is_active = true 的策略
     * @param type "INTENT" 或 "EMOTION"
     * @return Map<Key, Value>
     */
    public Map<String, String> getActiveStrategies(String type) {
        QueryWrapper<Strategy> query = new QueryWrapper<>();
        query.eq("strategy_type", type).eq("is_active", true);

        return strategyMapper.selectList(query)
                .stream()
                .collect(Collectors.toMap(Strategy::getStrategyKey, Strategy::getStrategyValue));
    }

    /**
     * 保存 (插入或更新) 一个策略
     */
    @Transactional // 覆盖为读写事务
    public Strategy saveStrategy(Strategy strategy) {
        if (strategy.getId() == null) {
            strategyMapper.insert(strategy);
        } else {
            strategyMapper.updateById(strategy);
        }
        return strategy;
    }

    /**
     * 创建一个新策略 (确保ID为null)
     */
    @Transactional
    public Strategy createStrategy(Strategy strategy) {
        strategy.setId(null);
        strategyMapper.insert(strategy);
        return strategy;
    }

    /**
     * 删除一个策略
     */
    @Transactional
    public void deleteStrategy(Integer id) {
        strategyMapper.deleteById(id);
    }

    // --- 全局设置 (GlobalSetting) 相关 API ---

    public Map<String, String> getAllGlobalSettings() {
        return globalSettingMapper.selectList(null).stream()
                .collect(Collectors.toMap(GlobalSetting::getSettingKey, GlobalSetting::getSettingValue));
    }

    /**
     * 批量保存全局设置 (Insert or Update)
     */
    @Transactional
    public void saveGlobalSettings(Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            GlobalSetting setting = new GlobalSetting();
            setting.setSettingKey(entry.getKey());
            setting.setSettingValue(entry.getValue());
            // 先尝试更新，如果失败（影响行数为0），则插入
            int updated = globalSettingMapper.updateById(setting);
            if (updated == 0) {
                globalSettingMapper.insert(setting);
            }
        }
    }

    /**
     * 获取单个全局设置
     */
    public String getGlobalSetting(String key, String defaultValue) {
        GlobalSetting setting = globalSettingMapper.selectById(key);
        return (setting != null) ? setting.getSettingValue() : defaultValue;
    }

    // --- 辅助Getters：获取特定类型的配置 ---

    /**
     * 从 global_settings 表获取并解析模型参数JSON
     */
    public ModelParameters getModelParams(String key) {
        try {
            String json = getGlobalSetting(key, "{}");
            return objectMapper.readValue(json, ModelParameters.class);
        } catch (Exception e) {
            log.error("解析模型参数JSON失败, key: {}. 返回默认值。", key, e);
            // 返回一个安全的默认值
            return new ModelParameters(
                    "qwen-turbo-instruct", 0.7, 0.7, 1024, 1.0, 0.0, 0.0
            );
        }
    }

    public List<String> getProcessList() {
        String processesStr = getGlobalSetting(KEY_PROCESSES, "1. 默认流程*");
        return List.of(processesStr.split("\\r?\\n")); // 按行分割
    }

    public String getDependencies() {
        return getGlobalSetting(KEY_DEPENDENCIES, "");
    }

    public String getPersonaTemplate() {
        return getGlobalSetting(KEY_PERSONA_TEMPLATE, "你是一个AI助手。 {tasks} {workflow} {code}");
    }

    public String getOpeningMonologue() {
        return getGlobalSetting(KEY_OPENING_MONOLOGUE, "您好，有什么可以帮您？");
    }

    public String getPreProcessingPrompt() {
        return getGlobalSetting(KEY_PRE_PROMPT, "分析：{ \"intent\": \"意图不明\", \"emotion\": \"中性\", \"is_sensitive\": \"false\" }");
    }

    public String getFallbackResponse() {
        return getGlobalSetting(KEY_FALLBACK, "我没听懂。");
    }

    public String getSensitiveResponse() {
        return getGlobalSetting(KEY_SENSITIVE, "我们换个话题吧。");
    }
}