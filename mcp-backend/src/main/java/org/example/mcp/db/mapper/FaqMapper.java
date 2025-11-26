package org.example.mcp.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.mcp.db.entity.FaqKb;
// import org.springframework.stereotype.Repository; // <-- 【关键】删除此行

/**
 * FAQ 知识库的 Mapper 接口
 * 继承 BaseMapper 后，自动拥有了对 FaqKb 实体的 CRUD (增删改查) 能力
 * 【修改】移除了 @Repository 注解，交由 @MapperScan 统一管理
 */
// @Repository // <-- 【关键】删除此注解
public interface FaqMapper extends BaseMapper<FaqKb> {
    // BaseMapper 已包含:
    // insert(), deleteById(), updateById(), selectById(), selectList() 等
    // MP会根据 QueryWrapper 自动生成SQL，我们无需手写
}