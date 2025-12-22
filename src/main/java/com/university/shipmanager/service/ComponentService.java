package com.university.shipmanager.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNodeConfig;
import cn.hutool.core.lang.tree.TreeUtil;
import com.university.shipmanager.entity.mongo.ComponentDoc;
import com.university.shipmanager.repository.ComponentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentService {

    private final ComponentRepository componentRepository;

    /**
     * 创建一个新部件 (节点)
     */
    public ComponentDoc createComponent(Long shipId, String name, String type, String parentId, Map<String, Object> specs) {
        ComponentDoc component = new ComponentDoc();
        component.setShipId(shipId);
        component.setName(name);
        component.setType(type);
        component.setSpecs(specs); // 动态参数直接存

        // 处理树形关系
        if (parentId != null) {
            component.setParentId(parentId);
            // 查出父节点，继承它的祖先列表
            ComponentDoc parent = componentRepository.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("父节点不存在"));

            // 核心逻辑：我的祖先 = 父节点的祖先 + 父节点自己
            List<String> ancestors = new ArrayList<>();
            if (parent.getAncestors() != null) {
                ancestors.addAll(parent.getAncestors());
            }
            ancestors.add(parent.getId());
            component.setAncestors(ancestors);
        } else {
            // 根节点
            component.setAncestors(new ArrayList<>());
        }

        return componentRepository.save(component);
    }

    /**
     * 【高光时刻】获取某艘船的完整 BOM 树
     * 修复说明：修正了 Lambda 参数顺序，node 是树节点，data 是数据库查出来的实体
     */
    public List<Tree<String>> getShipBomTree(Long shipId) {
        // 1. 一次查询取出所有部件
        List<ComponentDoc> allComponents = componentRepository.findByShipId(shipId);

        if (CollUtil.isEmpty(allComponents)) {
            return new ArrayList<>();
        }

        // 2. 配置树形构建器
        TreeNodeConfig treeNodeConfig = new TreeNodeConfig();
        treeNodeConfig.setIdKey("id");
        treeNodeConfig.setParentIdKey("parentId");
        treeNodeConfig.setNameKey("name");
        treeNodeConfig.setDeep(5);

        // 3. 内存中组装树
        // 修正参数顺序：(原始数据, 树节点)
        // Hutool 规则：第一个参数是 List 里的实体，第二个参数是构建出来的 Tree 对象
        return TreeUtil.build(allComponents, null, treeNodeConfig,
                (component, treeNode) -> {
                    // 逻辑：把 component (来源) 的数据 -> 塞给 treeNode (目标)

                    treeNode.setId(component.getId());
                    treeNode.setParentId(component.getParentId());
                    treeNode.setName(component.getName());

                    // 扩展字段
                    treeNode.putExtra("type", component.getType());
                    treeNode.putExtra("specs", component.getSpecs());
                });
    }

    /**
     * 【高光时刻 2】查询某个系统的所有子孙节点 (比如：删除"动力系统"前，查出它下面几千个零件)
     * 只需要查 ancestors 包含该 ID 即可，完全不需要递归！
     */
    public List<ComponentDoc> getSubTree(String systemId) {
        // 这一句查询，体现了 MongoDB 文档设计的精髓
        return componentRepository.findByAncestorsContaining(systemId);
    }
}