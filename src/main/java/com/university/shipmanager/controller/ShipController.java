package com.university.shipmanager.controller;

import com.university.shipmanager.entity.mysql.Ship;
import com.university.shipmanager.mapper.ShipMapper;
import com.university.shipmanager.service.ComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional; // 事务
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/ships")
@RequiredArgsConstructor
public class ShipController {

    private final ShipMapper shipMapper;
    private final ComponentService componentService; // 注入 ComponentService

    @GetMapping
    public List<Ship> list() {
        return shipMapper.selectList(null);
    }

    /**
     * 【核心改造】注册新船 -> 同时创建 MySQL 档案 + MongoDB 根节点
     */
    @PostMapping
    @Transactional(rollbackFor = Exception.class) // 保证两个数据库的一致性
    public Ship create(@RequestBody Ship ship) {
        // 1. 存 MySQL，拿到自增 ID
        shipMapper.insert(ship);
        Long newShipId = ship.getId();

        // 2. 存 MongoDB，自动创建一个同名的根节点
        // parentId = null 表示它是根
        componentService.createComponent(
                newShipId,          // 关联 ID
                ship.getName(),     // 节点名 = 船名 (如: 辽宁号)
                "Ship",             // 类型固定为 Ship
                null,               // 没有父节点
                new HashMap<>()     // 初始参数为空
        );

        return ship;
    }

    @GetMapping("/{id}")
    public Ship get(@PathVariable Long id) {
        return shipMapper.selectById(id);
    }

    /**
     * 【新增】删除船舶 (级联删除：MySQL档案 + MongoDB结构)
     * DELETE /api/ships/{id}
     */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public String delete(@PathVariable Long id) {
        // 1. 删 MySQL 档案
        shipMapper.deleteById(id);

        // 2. 删 MongoDB 里的整棵树
        // 注意：这里为了简单，暂时没去删 MinIO 的文件，
        // 严谨的做法是先查出所有文件路径删 MinIO，再删 Mongo。
        // 但为了课设演示，先保证删掉数据结构即可。
        componentService.deleteShipTree(id);

        return "删除成功";
    }
}