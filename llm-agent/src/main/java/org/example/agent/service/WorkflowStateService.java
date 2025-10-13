package org.example.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import java.util.*;

@Service
@SessionScope
public class WorkflowStateService {

    private List<String> currentProcesses = new ArrayList<>(Arrays.asList("1. 产品介绍*", "2. 办理确认*", "3. 办理提醒*", "4. 再次确认*", "5. 结束语*"));

    // 【核心修改】替换为下面这个更详细、更口语化的人设模板
    private String personaTemplate = "你是一名顶级的电信公司外呼专家，你的任务不是简单地回答问题，而是通过自然、流畅、有亲和力的对话，向用户介绍和比较电信套餐。" +
            "你的沟通风格应遵循以下原则：" +
            "1. **角色扮演**: 想象你正在打电话给一位客户，语气要友好、主动、耐心，就像一个真人销售顾问，而不是一个问答机器人。" +
            "2. **口语化表达**: 使用生活化的语言，例如“您问的这个问题很好”、“这款套餐可以说性价比非常高”、“简单来说呢，它最大的区别就是...”。避免生硬、书面的表达方式。" +
            "3. **结构化对比**: 当比较两个套餐时，不要仅仅罗列数据。要先总结核心差异，然后结合用户的可能需求进行分析。例如，先说价格和流量，然后补充说明“如果您电话多、流量用得也多，那肯定选A套餐更划算”。" +
            "4. **主动引导**: 在回答完一个问题后，可以主动询问用户的想法，引导对话继续下去。例如，“您看哪一款更符合您的使用习惯呢？”或者“还有其他套餐需要我为您介绍一下吗？”" +
            "5. **处理工具数据**: 当你从工具（tool）获得JSON格式的数据后，你的任务是将其“翻译”成上述风格的自然语言，绝对不要直接输出JSON或者像机器人一样逐条播报数据。你要消化这些数据，并用自己的话术组织起来，向用户推荐。" +
            "记住，你的最终目标是让用户感觉在与一个专业且懂他的真人顾问愉快地交流。";


    private Map<String, List<String>> dependencyRules = new HashMap<>();

    // 【建议修改】开场白也可以更自然一些
    private String openingMonologue = "喂，您好，这边是中国移动流量卡渠道的营销员，请问是尾号3312的机主吗?我们针对您的套餐使用情况推出了59元的优惠套餐，我给您简单介绍下好吗?";

    public List<String> getCurrentProcesses() {
        return currentProcesses;
    }

    public String getPersonaTemplate() {
        return personaTemplate;
    }

    public Map<String, List<String>> getDependencyRules() {
        return dependencyRules;
    }

    public String getOpeningMonologue() {
        return openingMonologue;
    }

    public void updateWorkflow(List<String> newProcesses, String newPersonaTemplate, String dependencies, String openingMonologue) {
        if (newProcesses != null) {
            this.currentProcesses = new ArrayList<>(newProcesses);
        }
        if (newPersonaTemplate != null && !newPersonaTemplate.trim().isEmpty()) {
            this.personaTemplate = newPersonaTemplate;
        }
        if (openingMonologue != null) {
            this.openingMonologue = openingMonologue;
        }
        if (dependencies != null) { /* 解析依赖的逻辑可以保留或简化 */ }
    }
}