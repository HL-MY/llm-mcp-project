package org.example.mcp.db.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
// autoResultMap = true 是让 JacksonTypeHandler 生效的关键
@TableName(value = "plan", autoResultMap = true)
public class Plan {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String planName; // 自动映射 plan_name

    private String description;

    // 【关键】使用 MP 的 JacksonTypeHandler 来自动处理 JSON 字段
    // 它会将数据库中的 JSON 字符串 自动转为 Map<String, Object>
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> detailsJson;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    // (这个字段在 'plan' 表中不存在，用于 Service 层临时存放别名)
    @TableField(exist = false)
    private List<String> aliases;
}