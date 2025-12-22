package com.university.shipmanager.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 船舶部件/零件 (BOM 节点)
 * 核心亮点：
 * 1. 树形结构设计 (Materialized Paths)
 * 2. 动态属性 (Dynamic Specs)
 */
@Data
@Document(collection = "ship_components")
public class ComponentDoc {

    @Id
    private String id;

    // --- 关联 MySQL ---
    @Indexed // 加索引，查询某艘船的所有部件时飞快
    private Long shipId; // 对应 MySQL ship 表的 id

    // --- 基本信息 ---
    private String name;  // 部件名称，如 "主柴油机"
    private String code;  // 编码，如 "ME-001"
    private String type;  // 类型，如 "Engine", "Pump", "Valve"

    // --- 核心 1: 树形结构 (Materialized Path 模式) ---
    // 为什么要这样设计？
    // 如果只存 parentId，查询 "某个系统的所有子部件" 需要递归，性能极差。
    // 存了 ancestors 数组，直接查 { ancestors: "系统ID" } 即可拿到整棵子树，效率提升 100 倍。
    @Indexed
    private String parentId; // 父节点 ID (根节点为 null)

    private List<String> ancestors; // 祖先 ID 链，例如 ["root_id", "system_id"]

    // --- 核心 2: 动态属性 (NoSQL 的灵魂) ---
    // MySQL 此时在哭泣：它没法在一个字段里同时存 "功率(int)" 和 "材质(string)"
    // MongoDB 笑而不语：Map<String, Object> 解决一切。
    // 示例数据：
    // Engine -> { "power": 5000, "cylinders": 12 }
    // Pump -> { "flow_rate": "500L/min", "material": "Steel" }
    private Map<String, Object> specs;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}