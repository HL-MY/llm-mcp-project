package org.example.agent.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import org.example.agent.db.entity.GlobalSetting;
import org.example.agent.db.mapper.GlobalSettingMapper;
import org.example.agent.factory.TelecomToolFactory;
import org.example.agent.service.ConfigService;
import org.example.llm.dto.tool.ParameterProperty;
import org.example.llm.dto.tool.ParameterSchema;
import org.example.llm.dto.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final GlobalSettingMapper globalSettingMapper;

    public DataInitializer(GlobalSettingMapper globalSettingMapper) {
        this.globalSettingMapper = globalSettingMapper;
    }

    @PostConstruct
    @Transactional
    public void init() {
        log.info("正在检查数据库配置完整性...");

        // 1. 初始化开场白
        initSetting(ConfigService.KEY_OPENING_MONOLOGUE, "您好，我是您的智能业务助理，请问有什么可以帮您？");

        // 2. 初始化主人设
        String defaultPersona = """
                你是一个专业的业务办理专员，语气热情、专业。
                你的任务是协助客户办理业务，解答疑问。
                请遵循以下原则：
                1. 始终保持礼貌，使用敬语。
                2. 回答简洁明了，不堆砌技术术语。
                3. 如果需要调用工具查询（如查天气、查套餐），请耐心等待工具结果。
                
                当前可用流程：{workflow}
                当前待办任务：{tasks}
                状态码说明：{code}
                """;
        initSetting(ConfigService.KEY_PERSONA_TEMPLATE, defaultPersona);

        // 3. 初始化安全红线
        String defaultRedlines = """
                1. 严禁辱骂或嘲讽客户。
                2. 严禁承诺具体的退款金额或赔偿金额。
                3. 涉及政治、暴力、色情话题直接拒绝。
                4. 遇到无法回答的问题，请引导客户转人工。
                """;
        initSetting(ConfigService.KEY_SAFETY_REDLINES, defaultRedlines);

        // 4. 初始化流程 SOP
        String defaultProcess = """
                1. 询问客户具体需求
                2. 确认客户身份信息
                3. 根据需求介绍对应业务
                4. 询问是否还需要其他帮助
                5. 礼貌结束对话
                """;
        initSetting(ConfigService.KEY_PROCESSES, defaultProcess);
        initSetting(ConfigService.KEY_DEPENDENCIES, ""); // 初始化流程依赖

        // 5. 初始化【策略/意图分析小模型】 Prompt (KEY_PRE_PROMPT)
        String defaultPrePrompt = """
                你是一个智能意图分析助手。你的任务是分析用户输入的核心意图、情绪和敏感词。
                请严格遵守以下规则，并只输出 JSON 格式结果。
                
                ### 1. 分析目标
                - intent: 用户的核心意图 (如: 闲聊, 投诉, 比较套餐, 查询FAQ, 联网搜索, 查询数据)。
                - emotion: 用户情绪 (高兴, 生气, 困惑, 中性)。
                - is_sensitive: 是否包含辱骂、色情等敏感词 (true/false)。
                
                【注意】你不需要做工具调用判断，那是另一个模型的工作。
                
                请分析下面的输入：
                """;
        initSetting(ConfigService.KEY_PRE_PROMPT, defaultPrePrompt);

        // 处理 工具描述、参数等
        StringBuilder stringBuilder = handleAllToolsDesc();

        // 6. 初始化【工具路由小模型】 Prompt (KEY_ROUTER_PROMPT)
        String defaultRouterPrompt = """
                你是一个高速工具路由助手。你的任务是判断用户是否可以直接通过调用工具解决问题，并给出调用指令。
                请严格遵守以下规则，并只输出 JSON 格式结果。
                
                ### 1. 任务目标
                - tool_name: 匹配到的工具名称。如果没有匹配到，则留空 ""。
                - tool_args: 对应工具所需的参数（JSON字符串形式）。
                
                ### 2. 高速工具通道 (Fast Track)
                仅当用户意图匹配以下【简单数据查询】工具时，才在 "tool_name" 和 "tool_args" 生成指令。
                 【支持的高速工具】:"""+

                stringBuilder
//                【支持的高速工具】: getWeather, getOilPrice, getGoldPrice, getExchangeRate, getCurrentTimeByCity, getStockInfo。
                +"""
                【绝对禁止】(必须留空 tool_name，交给主模型):
                - ❌ compareTwoPlans, queryMcpFaq, webSearch -> 留空！
                
                请分析下面的输入：
                """;
        initSetting(ConfigService.KEY_ROUTER_PROMPT, defaultRouterPrompt);


        // 7. 初始化模型参数
        initSetting(ConfigService.KEY_MAIN_MODEL, "{\"modelName\":\"qwen3-next-80b-a3b-instruct\",\"temperature\":0.7,\"topP\":0.8,\"maxTokens\":2048}");
        initSetting(ConfigService.KEY_PRE_MODEL, "{\"modelName\":\"qwen-turbo\",\"temperature\":0.1,\"topP\":0.7,\"maxTokens\":512}");
        initSetting(ConfigService.KEY_ROUTER_MODEL, "{\"modelName\":\"qwen-turbo\",\"temperature\":0.1,\"topP\":0.7,\"maxTokens\":512}");

        // 8. 初始化工具开关和描述 (新增描述初始化逻辑)
        Map<String, String> defaultDescriptions = TelecomToolFactory.getHardcodedToolDescriptions();
        for (Map.Entry<String, String> entry : defaultDescriptions.entrySet()) {
            String toolName = entry.getKey();
            // 初始化开关
            initSetting("enable_tool_" + toolName, "true");
            // 初始化描述
            initSetting(ConfigService.getToolDescriptionKey(toolName), entry.getValue());
        }

        log.info("数据库配置检查完成。");
    }

    private static StringBuilder handleAllToolsDesc() {
        // 获取所有工具定义（传入空Map获取默认描述）
        List<ToolDefinition> allTools = TelecomToolFactory.getAllToolDefinitions(new HashMap<>());
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (ToolDefinition tool : allTools) {
            String name = tool.getFunction().getName();

            // 拼接工具名和描述
            // 例如: 1. getWeather: 查询指定城市和日期的实时天气预报。
            sb.append(String.format("%d. %s: %s\n", index++, name, tool.getFunction().getDescription()));

            // 拼接参数详情
            ParameterSchema params = tool.getFunction().getParameters();
            if (params != null && params.getProperties() != null && !params.getProperties().isEmpty()) {
                List<String> required = params.getRequired() != null ? params.getRequired() : Collections.emptyList();

                // 对参数名排序，保证 Prompt 稳定性
                params.getProperties().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            String paramName = entry.getKey();
                            ParameterProperty prop = entry.getValue();
                            boolean isReq = required.contains(paramName);
                            String desc = prop.getDescription() != null ? prop.getDescription() : "";

                            // 例如:    - city (必填): 需要查询天气的城市名称
                            sb.append(String.format("   - %s (%s): %s\n",
                                    paramName,
                                    isReq ? "必填" : "选填",
                                    desc
                            ));
                        });
            } else {
                sb.append("   - (无参数)\n");
            }
            sb.append("\n");
        }
        return sb;
    }

    private void initSetting(String key, String defaultValue) {
        QueryWrapper<GlobalSetting> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("setting_key", key);
        GlobalSetting setting = globalSettingMapper.selectOne(queryWrapper);

        if (setting == null) {
            setting = new GlobalSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(defaultValue);
            globalSettingMapper.insert(setting);
            log.info("初始化配置项: {}", key);
        }
    }
}