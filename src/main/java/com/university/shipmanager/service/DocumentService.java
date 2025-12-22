package com.university.shipmanager.service;

import com.university.shipmanager.entity.mongo.ShipDocument;
import com.university.shipmanager.entity.mysql.DocIndex;
import com.university.shipmanager.mapper.DocIndexMapper;
import com.university.shipmanager.repository.ShipDocumentRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor // Lombok 自动生成构造函数注入 Bean
public class DocumentService {

    private final DocIndexMapper docIndexMapper;       // MySQL 操作
    private final ShipDocumentRepository mongoRepository; // Mongo 操作

    /**
     * 核心功能：上传新文档
     * 场景：用户点击“上传”，填写了一堆属性，选了一个文件
     */
    @Transactional(rollbackFor = Exception.class) // MySQL 事务
    public void uploadNewDocument(UploadRequest request) {

        // 1. 准备 MongoDB 的数据对象 (血肉)
        ShipDocument mongoDoc = new ShipDocument();
        mongoDoc.setMetadata(request.getMetadata()); // 动态属性直接存

        // 构建第一个版本信息
        ShipDocument.DocVersion v1 = new ShipDocument.DocVersion();
        v1.setVersionNo("V1.0");
        v1.setCommitMsg("Initial Upload");
        v1.setFileSize(1024L); // 假装有个文件大小
        v1.setStoragePath("/minio/fake-path/" + request.getFileName()); // 假装存了文件
        v1.setUploadedBy("admin"); // 暂时写死

        mongoDoc.setVersions(new ArrayList<>(List.of(v1)));

        // 2. 【关键】先存 MongoDB，拿到 ID
        ShipDocument savedMongoDoc = mongoRepository.save(mongoDoc);
        log.info("MongoDB 保存成功，ID: {}", savedMongoDoc.getId());

        try {
            // 3. 准备 MySQL 的数据对象 (骨架)
            DocIndex sqlIndex = new DocIndex();
            sqlIndex.setShipId(request.getShipId());
            sqlIndex.setTitle(request.getTitle());
            sqlIndex.setCategory(request.getCategory());
            sqlIndex.setStatus("DRAFT");
            sqlIndex.setLatestVersion(1);
            sqlIndex.setCreatedAt(LocalDateTime.now());

            // --- 建立关联 ---
            sqlIndex.setMongoDocId(savedMongoDoc.getId()); // 把 Mongo ID 存进 MySQL

            // 4. 存入 MySQL
            docIndexMapper.insert(sqlIndex);
            log.info("MySQL 保存成功，IndexID: {}", sqlIndex.getId());

        } catch (Exception e) {
            // 5. 【手动回滚】如果 MySQL 挂了，要把刚才 Mongo 里存的垃圾数据删掉！
            log.error("MySQL 保存失败，执行 MongoDB 回滚...");
            mongoRepository.deleteById(savedMongoDoc.getId());
            throw e; // 继续抛出异常，让 Controller 知道失败了
        }
    }

    // --- DTO: 前端传来的参数 ---
    @Data
    public static class UploadRequest {
        private Long shipId;
        private String title;
        private String fileName;
        private String category;
        // 动态参数：前端传 JSON，后端直接用 Map 接，NoSQL 的优势！
        private Map<String, Object> metadata;
    }
}