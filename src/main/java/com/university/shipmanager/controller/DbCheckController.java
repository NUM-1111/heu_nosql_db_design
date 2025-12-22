package com.university.shipmanager.controller;

import com.university.shipmanager.entity.mongo.ComponentDoc;
import com.university.shipmanager.entity.mongo.PingDoc;
import com.university.shipmanager.repository.PingRepository;
import com.university.shipmanager.service.ComponentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.university.shipmanager.service.DocumentService; // 导入 Service

import java.util.Map;

@RestController
public class DbCheckController {

    private final JdbcTemplate jdbcTemplate;
    private final PingRepository pingRepository;
    private final DocumentService documentService; // 注入新写的 Service
    private final ComponentService componentService;

    // 构造函数加上 DocumentService
    public DbCheckController(JdbcTemplate jdbcTemplate, PingRepository pingRepository, DocumentService documentService, ComponentService componentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.pingRepository = pingRepository;
        this.documentService = documentService;
        this.componentService = componentService;
    }

    @GetMapping("/check/db")
    public Map<String, Object> checkDb() {
        // MySQL：查一下当前数据库名
        String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

        // Mongo：插入一条文档
        PingDoc saved = pingRepository.save(new PingDoc("mongo ok"));

        return Map.of(
                "mysql_database", dbName,
                "mongo_saved_id", saved.getId(),
                "mongo_msg", saved.getMsg()
        );
    }

    // 新增测试接口
    @GetMapping("/test/upload")
    public String testUpload() {
        // 模拟一个前端请求
        DocumentService.UploadRequest request = new DocumentService.UploadRequest();
        request.setShipId(101L);
        request.setTitle("主引擎液压系统图");
        request.setCategory("Drawing");
        request.setFileName("engine_hydraulics.pdf");

        // 模拟动态参数 (NoSQL 特性)
        request.setMetadata(java.util.Map.of(
                "dpi", 300,
                "designer", "Senior Engineer Wang",
                "approver", "Chief Li"
        ));

        documentService.uploadNewDocument(request);

        return "上传测试成功！请去 MySQL 表 doc_index 和 MongoDB 集合 ship_document_details 查看数据关联。";
    }

    @GetMapping("/test/tree")
    public Object testTree() {
        Long shipId = 888L; // 假设是一艘新船

        // 1. 造根节点：全船
        ComponentDoc root = componentService.createComponent(shipId, "远望7号测量船", "Ship", null, null);

        // 2. 造一级节点：动力系统
        ComponentDoc powerSystem = componentService.createComponent(shipId, "动力系统", "System", root.getId(), null);

        // 3. 造二级节点：主柴油机 (带参数)
        Map<String, Object> engineSpecs = Map.of("power", "12000KW", "model", "MAN-B&W");
        componentService.createComponent(shipId, "1号主柴油机", "Engine", powerSystem.getId(), engineSpecs);

        // 4. 造二级节点：螺旋桨 (带完全不同的参数)
        Map<String, Object> propSpecs = Map.of("diameter", "5.2m", "blades", 4);
        componentService.createComponent(shipId, "左舷螺旋桨", "Propeller", powerSystem.getId(), propSpecs);

        // 5. 查出整棵树
        return componentService.getShipBomTree(shipId);
    }
}
