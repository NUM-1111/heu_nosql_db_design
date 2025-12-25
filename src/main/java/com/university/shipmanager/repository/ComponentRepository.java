package com.university.shipmanager.repository;

import com.university.shipmanager.entity.mongo.ComponentDoc;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ComponentRepository extends MongoRepository<ComponentDoc, String> {

    // 自动生成查询：找某艘船的所有部件
    List<ComponentDoc> findByShipId(Long shipId);

    // 自动生成查询：找某个父节点下的所有直接子节点
    List<ComponentDoc> findByParentId(String parentId);

    // 高级查询：找某个节点的所有后代（用于删除子树或展示整棵树）
    // MongoDB 语法: { ancestors: "目标ID" }
    List<ComponentDoc> findByAncestorsContaining(String ancestorId);

    // 【新增】统计某艘船的根节点数量 (ParentId 为 null 的就是根)
    long countByShipIdAndParentIdIsNull(Long shipId);

    void deleteByShipId(Long shipId);
}