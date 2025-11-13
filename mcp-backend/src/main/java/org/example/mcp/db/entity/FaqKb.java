package org.example.mcp.db.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.Instant;

@Data
@TableName("faq_kb") // 对应数据库表 faq_kb
public class FaqKb {

    @TableId(type = IdType.AUTO) // 主键自增
    private Integer id;

    private String intentKey; // 自动映射 intent_key

    private String answerText; // 自动映射 answer_text

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT) // 插入时自动填充
    private Instant createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE) // 插入和更新时自动填充
    private Instant updatedAt;
}