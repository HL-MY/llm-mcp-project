package org.example.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@SessionScope
public class WorkflowStateService {

    private List<String> currentProcesses = new ArrayList<>(Arrays.asList("1. 产品介绍*", "2. 升级确认*", "3. 升级提醒*", "4. 升级成功通知*", "5. 结束语*"));

    // --- 修改：重新加入 {code} 占位符，并添加打断规则描述 ---
    private String personaTemplate = "你是一名顶级的电信公司外呼专家，你的任务不是简单地回答问题，而是通过自然、流畅、有亲和力的对话，向用户介绍和比较电信套餐。" +
            "你的沟通风格应遵循以下原则：" +
            "1. **角色扮演**: 想象你正在打电话给一位客户，语气要友好、主动、耐心，就像一个真人销售顾问，而不是一个问答机器人。" +
            "2. **口语化表达**: 使用生活化的语言，例如“您问的这个问题很好”、“这款套餐可以说性价比非常高”、“简单来说呢，它最大的区别就是...”。避免生硬、书面的表达方式。" +
            "3. **结构化对比**: 当比较两个套餐时，不要仅仅罗列数据。要先总结核心差异，然后结合用户的可能需求进行分析。例如，先说价格和流量，然后补充说明“如果您电话多、流量用得也多，那肯定选A套餐更划算”。" +
            "4. **主动引导**: 在回答完一个问题后，可以主动询问用户的想法，引导对话继续下去。例如，“您看哪一款更符合您的使用习惯呢？”或者“还有其他套餐需要我为您介绍一下吗？”" +
            "5. **处理工具数据**: 当你从工具（tool）获得JSON格式的数据后，你的任务是将其“翻译”成上述风格的自然语言，绝对不要直接输出JSON或者像机器人一样逐条播报数据。你要消化这些数据，并用自己的话术组织起来，向用户推荐。" +
            "6. **流程推进**: 在完成某个明确的沟通环节（如产品介绍完毕）后，你的回复中必须包含短语 '我已完成流程[流程名]' 来告知系统推进流程。当前可执行的任务有：{tasks}。完整工作流参考：{workflow}。" +
            // --- 新增：使用占位符的打断规则 ---
            "7. **打断处理**: 当前用户的打断状态是 '{code}'。如果状态是 '已触发'，这表示用户刚刚说了'打断'，你必须立即停止当前的长篇介绍，并友好地回应用户的打断，例如 '好的，您请说' 或 '您有什么问题吗？'。如果状态是 '空值'，则正常对话。" +
            "记住，你的最终目标是让用户感觉在与一个专业且懂他的真人顾问愉快地交流。";

    private Map<String, List<String>> dependencyRules = new HashMap<>();

    private String openingMonologue = "喂，您好，这边是中国移动的客服人员，请问是尾号6666的机主吗?咱们来电是针对您现在的套餐想给您推荐一个更划算的升级活动，我先给您简单介绍下您看可以吗?";

    // --- 移除：删除了 code 字段及其 getter/setter ---

    public List<String> getCurrentProcesses() {
        return currentProcesses;
    }

    public String getPersonaTemplate() {
        // 返回包含占位符的原始模板
        return personaTemplate;
    }
    public WorkflowStateService() {
        parseAndSetDependencies("2 -> 1\n3 -> 2\n4 -> 3 \n5 -> 4");
    }

    public Map<String, List<String>> getDependencyRules() {
        return dependencyRules;
    }

    public String getOpeningMonologue() {
        return openingMonologue;
    }


    public void updateWorkflow(List<String> newProcesses, String newPersonaTemplate, String dependencies, String openingMonologue) {
        if (newProcesses != null && !newProcesses.isEmpty()) {
            this.currentProcesses = new ArrayList<>(newProcesses);
        }
        // --- 修改：确保保存的是包含占位符的模板 ---
        if (newPersonaTemplate != null && !newPersonaTemplate.trim().isEmpty()) {
            // 校验模板是否包含必要的占位符 (可选但推荐)
            if (!newPersonaTemplate.contains("{code}")) {
                // 可以抛出异常或记录警告
                log.warn("新的人设模板缺少必要的 '{code}' 占位符！");
                // 也可以自动添加回去，但这可能不是用户想要的
                // newPersonaTemplate += "\n\n打断状态: {code}";
            }
            this.personaTemplate = newPersonaTemplate;
        }
        if (openingMonologue != null) { // 允许设置为空字符串
            this.openingMonologue = openingMonologue;
        }
        parseAndSetDependencies(dependencies);
    }

    // (parseAndSetDependencies 和 findProcessByName ... 保持不变)
    private static final Logger log = LoggerFactory.getLogger(WorkflowStateService.class); // 需要引入 Logger

    private void parseAndSetDependencies(String dependencies) {
        this.dependencyRules.clear();
        if (dependencies == null || dependencies.trim().isEmpty()) {
            return;
        }
        String[] lines = dependencies.split("\\r?\\n");
        for (String line : lines) {
            if (!line.contains("->")) {
                continue;
            }
            String[] parts = line.split("->");
            if (parts.length < 2) {
                continue;
            }
            String process = findProcessByName(parts[0].trim());
            if (process == null) {
                continue;
            }
            List<String> prerequisites = Arrays.stream(parts[1].split(","))
                    .map(String::trim).map(this::findProcessByName)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            if (!prerequisites.isEmpty()) { dependencyRules.put(process, prerequisites); }
        }
    }

    private String findProcessByName(String nameOrId) {
        // 优先匹配完整名称
        for (String p : currentProcesses) {
            if (p.trim().equalsIgnoreCase(nameOrId)) {
                return p;
            }
        }
        // 然后匹配编号
        for (String p : currentProcesses) {
            if (p.trim().matches("^" + Pattern.quote(nameOrId) + "[.\\s].*")) {
                return p;
            }
        }
        // 最后匹配清理后的名称
        for (String p : currentProcesses) {
            String sanitizedName = p.replaceAll("^\\d+\\.?\\s*", "").replace("*","").trim();
            if(sanitizedName.equalsIgnoreCase(nameOrId)){
                return p;
            }
        }
        return null;
    }
}