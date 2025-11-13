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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 这是一个核心的、单例的配置服务 (Singleton)。
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
    public static final String KEY_SENSITIVE = "sensitive_response";
    public static final String KEY_PRE_PROMPT = "pre_processing_prompt";
    public static final String KEY_ENABLE_STRATEGY = "enable_strategy";

    // 【新增】情绪识别开关
    public static final String KEY_ENABLE_EMOTION = "enable_emotion_recognition";
    // 【新增】安全红线
    public static final String KEY_SAFETY_REDLINES = "safety_redlines";


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
        // 定义一个安全默认值，用于数据库为空或解析失败时
        ModelParameters safeDefault = new ModelParameters(
                // 使用新的推荐模型名
                KEY_MAIN_MODEL.equals(key) ? "qwen3-next-80b-a3b-instruct" : "qwen-turbo",
                // 默认参数
                KEY_MAIN_MODEL.equals(key) ? 0.7 : 0.1, // 温度：主模型 0.7，预处理模型 0.1
                KEY_MAIN_MODEL.equals(key) ? 0.8 : 0.7, // Top P
                KEY_MAIN_MODEL.equals(key) ? 2048 : 512, // Max Tokens
                1.0, 0.0, 0.0
        );

        try {
            String json = getGlobalSetting(key, "{}"); // 数据库不存在时返回 "{}"
            ModelParameters loadedParams = objectMapper.readValue(json, ModelParameters.class);

            // 核心修正：如果数据库返回空JSON ({})，modelName 会是 null。
            if (loadedParams.getModelName() == null || loadedParams.getModelName().isEmpty()) {
                loadedParams.setModelName(safeDefault.getModelName());
            }

            // 确保其他关键参数非空 (以防只保存了一部分配置)
            if (loadedParams.getTemperature() == null) loadedParams.setTemperature(safeDefault.getTemperature());
            if (loadedParams.getTopP() == null) loadedParams.setTopP(safeDefault.getTopP());
            if (loadedParams.getMaxTokens() == null) loadedParams.setMaxTokens(safeDefault.getMaxTokens());


            return loadedParams;
        } catch (Exception e) {
            log.error("解析模型参数JSON失败, key: {}. 返回安全默认值。", key, e);
            // 返回一个安全的默认值
            return safeDefault;
        }
    }

    public List<String> getProcessList() {
        return List.of(getGlobalSetting(KEY_PROCESSES, "1. 默认流程*").split("\\r?\\n")); // 按行分割
    }

    public String getDependencies() {
        return getGlobalSetting(KEY_DEPENDENCIES, "");
    }

    // 【新增】获取策略总开关状态
    public Boolean getEnableStrategy() {
        // 默认为 true
        String value = getGlobalSetting(KEY_ENABLE_STRATEGY, "true");
        return "true".equalsIgnoreCase(value);
    }

    // 【新增】获取情绪识别开关状态
    public Boolean getEnableEmotionRecognition() {
        // 默认为 true
        String value = getGlobalSetting(KEY_ENABLE_EMOTION, "true");
        return "true".equalsIgnoreCase(value);
    }

    // 【新增】获取安全红线
    public String getSafetyRedlines() {
        return getGlobalSetting(KEY_SAFETY_REDLINES, "");
    }

    public String getPersonaTemplate() {
        return getGlobalSetting(KEY_PERSONA_TEMPLATE, "你是一个AI助手。 {tasks} {workflow} {code}");
    }

    public String getOpeningMonologue() {
        return getGlobalSetting(KEY_OPENING_MONOLOGUE, "您好，有什么可以帮您？");
    }

    public String getPreProcessingPrompt() {
        String defaultPrompt = """
                你是一个专门用于分析用户输入的小模型。
                请严格按照 JSON 格式输出分析结果，不需要任何解释或额外文字。
                
                分析结果必须包含三个字段：
                1. "intent": 识别用户的意图。可选值：比较套餐, 查询FAQ, 有升级意向, 用户抱怨, 闲聊, 意图不明。
                2. "emotion": 识别用户的情绪。可选值：高兴, 生气, 困惑, 中性。
                3. "is_sensitive": 判断用户输入是否包含敏感词。可选值："true" 或 "false"。
                
                示例输出:
                { "intent": "意图不明", "emotion": "中性", "is_sensitive": "false" }
                
                请对下面的用户输入进行分析：
                """;
        // 使用 getGlobalSetting 来获取数据库或默认值
        String savedPrompt = getGlobalSetting(KEY_PRE_PROMPT, defaultPrompt);

        return savedPrompt;
    }

    public String getSensitiveResponse() {
        return getGlobalSetting(KEY_SENSITIVE, "我们换个话题吧。");
    }
}