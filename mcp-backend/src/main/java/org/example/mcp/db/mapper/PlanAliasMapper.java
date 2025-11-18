package org.example.mcp.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.mcp.db.entity.PlanAlias;
// import org.springframework.stereotype.Repository; // <-- 【关键】删除此行

/**
 * 套餐别名 (PlanAlias) 的 Mapper 接口
 * 继承 BaseMapper，自动拥有对 PlanAlias 实体的 CRUD 能力
 * 【修改】移除了 @Repository 注解，交由 @MapperScan 统一管理
 */
// @Repository // <-- 【关键】删除此注解
public interface PlanAliasMapper extends BaseMapper<PlanAlias> {
    // 自动拥有 CRUD
}