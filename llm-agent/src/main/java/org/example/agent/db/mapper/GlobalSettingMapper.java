package org.example.agent.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.agent.db.entity.GlobalSetting;
// import org.springframework.stereotype.Repository; // <-- 【关键】删除此行

/**
 * 全局配置 (GlobalSetting) 的 Mapper 接口
 * 继承 BaseMapper，自动拥有对 GlobalSetting 实体的 CRUD 能力
 * 【修改】移除了 @Repository 注解，交由 @MapperScan 统一管理
 */
// @Repository // <-- 【关键】删除此注解
public interface GlobalSettingMapper extends BaseMapper<GlobalSetting> {
    // 自动拥有 CRUD
}