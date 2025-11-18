package org.example.mcp.db.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("plan_alias")
public class PlanAlias {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer planId; // 自动映射 plan_id

    private String aliasName; // 自动映射 alias_name
}