package com.university.shipmanager.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNodeConfig;
import cn.hutool.core.lang.tree.TreeUtil;
import cn.hutool.core.util.StrUtil;
import com.university.shipmanager.entity.mongo.AuditLog;
import com.university.shipmanager.entity.mongo.ComponentDoc;
import com.university.shipmanager.repository.AuditLogRepository;
import com.university.shipmanager.repository.ComponentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentService {

    private final ComponentRepository componentRepository;
    private final DocumentService documentService;
    private final AuditLogRepository auditLogRepository; // 【修复】注入日志库

    // ... createComponent 和 getShipBomTree 保持不变 ...
    public ComponentDoc createComponent(Long shipId, String name, String type, String parentId, Map<String, Object> specs) {
        if (StrUtil.isBlank(name) || StrUtil.isBlank(type)) throw new IllegalArgumentException("名称和类型不能为空");
        ComponentDoc component = new ComponentDoc();
        component.setShipId(shipId);
        component.setName(name);
        component.setType(type);
        component.setSpecs(specs);
        if (parentId != null) {
            component.setParentId(parentId);
            ComponentDoc parent = componentRepository.findById(parentId).orElseThrow(() -> new RuntimeException("父节点不存在"));
            List<String> ancestors = new ArrayList<>();
            if (parent.getAncestors() != null) ancestors.addAll(parent.getAncestors());
            ancestors.add(parent.getId());
            component.setAncestors(ancestors);
        } else {
            component.setAncestors(new ArrayList<>());
        }
        return componentRepository.save(component);
    }

    public List<Tree<String>> getShipBomTree(Long shipId) {
        List<ComponentDoc> allComponents = componentRepository.findByShipId(shipId);
        if (CollUtil.isEmpty(allComponents)) return new ArrayList<>();
        TreeNodeConfig config = new TreeNodeConfig();
        config.setIdKey("id");
        config.setParentIdKey("parentId");
        config.setNameKey("name");
        config.setDeep(5);
        return TreeUtil.build(allComponents, null, config, (component, treeNode) -> {
            treeNode.setId(component.getId());
            treeNode.setParentId(component.getParentId());
            treeNode.setName(component.getName());
            treeNode.putExtra("type", component.getType());
            treeNode.putExtra("specs", component.getSpecs());
        });
    }

    public ComponentDoc updateComponent(String id, String name, String type, Map<String, Object> specs) {
        if (StrUtil.isBlank(name) || StrUtil.isBlank(type)) throw new IllegalArgumentException("名称和类型不能为空");
        ComponentDoc doc = componentRepository.findById(id).orElseThrow(() -> new RuntimeException("节点不存在"));
        doc.setName(name);
        doc.setType(type);
        if (specs != null) doc.setSpecs(specs);
        return componentRepository.save(doc);
    }

    public List<ComponentDoc> getSubTree(String systemId) {
        return componentRepository.findByAncestorsContaining(systemId);
    }

    /**
     * 【修复】级联删除 + 审计日志
     */
    @Transactional
    public void deleteComponentAndChildren(String componentId) {
        ComponentDoc self = componentRepository.findById(componentId).orElseThrow(() -> new RuntimeException("节点不存在"));
        List<ComponentDoc> children = componentRepository.findByAncestorsContaining(componentId);

        List<String> allIdsToDelete = new ArrayList<>();
        allIdsToDelete.add(self.getId());
        children.forEach(c -> allIdsToDelete.add(c.getId()));

        log.info("准备删除节点 {} 及其子孙，共 {} 个", componentId, allIdsToDelete.size());

        // 1. 删文档
        documentService.deleteDocumentsByComponentIds(allIdsToDelete);

        // 2. 删零件
        componentRepository.deleteAllById(allIdsToDelete);

        // 3. 【修复】记录“删除零件”的审计日志 (之前只记录了删文档)
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("DELETE_COMPONENT");
        auditLog.setTargetType("Component");
        auditLog.setTargetName(self.getName() + " (及其子节点)");
        auditLog.setOperator("admin");
        auditLog.setDetail("删除了节点ID: " + componentId + ", 影响节点数: " + allIdsToDelete.size());
        auditLogRepository.save(auditLog); // 保存到 Mongo
    }

    /**
     * 【修复】移动节点 (支持移动到根节点)
     */
    @Transactional
    public void moveComponent(String id, String newParentId) {
        ComponentDoc node = componentRepository.findById(id).orElseThrow(() -> new RuntimeException("节点不存在"));

        // 1. 计算新祖先
        List<String> newAncestors = new ArrayList<>();
        String actualParentId = null;

        if (StrUtil.isNotBlank(newParentId) && !"root".equals(newParentId)) {
            // A. 移动到某个父节点下
            ComponentDoc newParent = componentRepository.findById(newParentId)
                    .orElseThrow(() -> new RuntimeException("目标父节点不存在"));
            if (newParent.getAncestors() != null && newParent.getAncestors().contains(id)) {
                throw new RuntimeException("不能把自己移动到自己的子节点下！");
            }
            if (newParent.getAncestors() != null) {
                newAncestors.addAll(newParent.getAncestors());
            }
            newAncestors.add(newParent.getId());
            actualParentId = newParentId;
        } else {
            // B. 【修复】移动到最外层 (根节点)
            // 保持 ancestors 为空，parentId 为 null
            actualParentId = null;
        }

        // 2. 更新自己
        node.setParentId(actualParentId);
        node.setAncestors(newAncestors);
        componentRepository.save(node);

        // 3. 更新子孙 (逻辑简化：重新计算所有子孙的路径)
        // 这里的逻辑稍微复杂，为了 Demo 稳定，我们假设子孙跟随移动。
        // 生产环境通常需要递归更新子孙的 ancestors。
        // 简单修复：如果只是 Demo 演示，只要 TreeUtil 重新构建时 parentId 对了，树就能显示对。
        // 但为了数据一致性，建议重建子孙的 ancestors。
        List<ComponentDoc> children = componentRepository.findByAncestorsContaining(id);
        for (ComponentDoc child : children) {
            // 重新计算子孙的 ancestors: 新的 ancestors + 我 + 我之后的路径
            List<String> childAncestors = child.getAncestors();
            int myIndex = childAncestors.indexOf(id);

            List<String> updated = new ArrayList<>(newAncestors);
            updated.add(id);
            if (myIndex != -1 && myIndex + 1 < childAncestors.size()) {
                updated.addAll(childAncestors.subList(myIndex + 1, childAncestors.size()));
            }
            child.setAncestors(updated);
            componentRepository.save(child);
        }
    }
}