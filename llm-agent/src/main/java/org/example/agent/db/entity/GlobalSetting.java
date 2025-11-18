package org.example.agent.db.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("global_settings")
public class GlobalSetting {
    @TableId(type = IdType.INPUT)
    private String settingKey; // 自动映射 setting_key

    private String settingValue; // 自动映射 setting_value
}