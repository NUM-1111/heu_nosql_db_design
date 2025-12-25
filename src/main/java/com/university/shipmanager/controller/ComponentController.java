package com.university.shipmanager.controller;

import cn.hutool.core.lang.tree.Tree;
import com.university.shipmanager.entity.mongo.ComponentDoc;
import com.university.shipmanager.service.ComponentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j; // 1. 记得引入这个

@Slf4j
@RestController
@RequestMapping("/api/components")
@RequiredArgsConstructor
public class ComponentController {

    private final ComponentService componentService;

    /**
     * 获取零件树
     */
    @GetMapping("/tree")
    public List<Tree<String>> getTree(@RequestParam Long shipId) {
        return componentService.getShipBomTree(shipId);
    }

    /**
     * 【新增】创建新零件 (带日志版)
     */
    @PostMapping
    public ComponentDoc create(@RequestBody CreateRequest request) {
        // 🔍 探头 1：打印接收到的参数
        log.info("【收到创建请求】 船ID: {}, 名称: {}, 类型: {}, 父节点ID: {}",
                request.getShipId(), request.getName(), request.getType(), request.getParentId());

        // 打印动态参数 (看看 specs 传没传)
        log.info(" -> 动态参数 Specs: {}", request.getSpecs());

        try {
            ComponentDoc created = componentService.createComponent(
                    request.getShipId(),
                    request.getName(),
                    request.getType(),
                    request.getParentId(),
                    request.getSpecs()
            );
            // 🔍 探头 2：打印成功结果
            log.info("【创建成功】 新节点ID: {}", created.getId());
            return created;
        } catch (Exception e) {
            // 🔍 探头 3：打印报错原因 (这一步非常关键！)
            log.error("【创建失败】 发生异常: ", e);
            throw e; // 继续抛出，让全局异常处理器处理
        }
    }

    /**
     * 【新增】更新零件信息
     * PUT /api/components/{id}
     */
    @PutMapping("/{id}")
    public ComponentDoc update(@PathVariable String id, @RequestBody CreateRequest request) {
        return componentService.updateComponent(
                id,
                request.getName(),
                request.getType(),
                request.getSpecs()
        );
    }

    /**
     * 删除接口
     */
    @DeleteMapping("/{id}")
    public String deleteComponent(@PathVariable String id) {
        componentService.deleteComponentAndChildren(id);
        return "删除成功";
    }

    /**
     * 【新增】拖拽移动节点接口
     * PUT /api/components/{id}/move?newParentId=xxx
     */
    @PutMapping("/{id}/move")
    public String move(@PathVariable String id, @RequestParam String newParentId) {
        componentService.moveComponent(id, newParentId);
        return "移动成功";
    }

    // --- DTO: 接收前端参数 ---
    @Data
    public static class CreateRequest {
        private Long shipId;
        private String name;
        private String type;     // System, Engine, Pump...
        private String parentId; // 父节点ID (如果是根节点则为 null)
        private Map<String, Object> specs; // 动态参数
    }
}