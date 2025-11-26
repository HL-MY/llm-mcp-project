package org.example.agent.db.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("global_settings")
public class GlobalSetting {
    // 【新增】自增ID作为主键
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String settingKey; // 此时在DB中应设置为 UNIQUE

    private String settingValue; // 自动映射 setting_value
}