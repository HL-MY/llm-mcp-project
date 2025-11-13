package org.example.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.agent.db.entity.DecisionRule;
import org.example.agent.db.entity.Strategy;
import org.example.agent.db.mapper.DecisionRuleMapper;
import org.example.agent.db.mapper.StrategyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RuleEngineService {

    private static final Logger log = LoggerFactory.getLogger(RuleEngineService.class);

    private final DecisionRuleMapper ruleMapper;
    private final StrategyMapper strategyMapper;

    public RuleEngineService(DecisionRuleMapper ruleMapper, StrategyMapper strategyMapper) {
        this.ruleMapper = ruleMapper;
        this.strategyMapper = strategyMapper;
    }

    /**
     * 根据意图和情绪，查询规则库，返回最高优先级的策略文本
     * @param intent 意图
     * @param emotion 情绪
     * @return 匹配到的策略文本 (话术)
     */
    public String selectBestStrategy(String intent, String emotion) {
        // 1. 优先尝试匹配 (意图 + 情绪)
        QueryWrapper<DecisionRule> query = new QueryWrapper<>();
        query.eq("trigger_intent", intent)
                .eq("trigger_emotion", emotion)
                .orderByDesc("priority")
                .last("LIMIT 1");

        DecisionRule rule = ruleMapper.selectOne(query);

        // 2. 如果 (意图 + 情绪) 未命中，则尝试仅匹配 (意图)
        if (rule == null) {
            log.info("规则引擎：未命中 (意图: {}, 情绪: {})，尝试仅匹配意图...", intent, emotion);
            QueryWrapper<DecisionRule> intentQuery = new QueryWrapper<>();
            intentQuery.eq("trigger_intent", intent)
                    .and(qw -> qw.isNull("trigger_emotion").or().eq("trigger_emotion", ""))
                    .orderByDesc("priority")
                    .last("LIMIT 1");
            rule = ruleMapper.selectOne(intentQuery);
        }

        // 3. 如果都未命中
        if (rule == null) {
            log.warn("规则引擎：(意图: {}) 未命中任何规则，返回空策略。", intent);
            return ""; // 返回空字符串，ChatService 会知道如何处理
        }

        // 4. 命中规则，查询对应的 "话术卡牌" (Strategy)
        log.info("规则引擎：命中规则 ID: {} (优先级: {})，策略键: {}", rule.getId(), rule.getPriority(), rule.getStrategyKey());

        Strategy strategy = strategyMapper.selectOne(
                new QueryWrapper<Strategy>().eq("strategy_key", rule.getStrategyKey())
        );

        if (strategy == null || !strategy.getIsActive()) {
            log.error("规则引擎：规则 ID: {} 指向的策略键 '{}' 不存在或未激活！", rule.getId(), rule.getStrategyKey());
            return "";
        }

        return strategy.getStrategyValue();
    }
}