package org.example.agent.db.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.Instant;

@Data
@TableName("decision_rules")
public class DecisionRule {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer priority;

    private String triggerIntent; // 对应 trigger_intent

    private String triggerEmotion; // 对应 trigger_emotion

    private String strategyKey; // 对应 strategy_key

    private String description; // 对应 description

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}