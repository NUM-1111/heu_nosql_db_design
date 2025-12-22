package com.university.shipmanager.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 船舶文档详情 (存储 MySQL 存不下的复杂数据)
 * 核心亮点：
 * 1. 嵌入式版本管理 (Embedded Versions)
 * 2. 动态元数据
 */
@Data
@Document(collection = "ship_document_details")
public class ShipDocument {

    @Id
    private String id; // 这个 ID 会被存入 MySQL 的 doc_index 表的 mongo_doc_id 字段

    // 冗余字段，方便在 Mongo 内部排查问题
    private Long mysqlIndexId;

    // --- 核心 3: 动态元数据 ---
    // 图纸有 "比例尺"，说明书有 "页数"，合同有 "金额"
    private Map<String, Object> metadata;

    // --- 核心 4: 版本控制 (文档内嵌) ---
    // 因为一个文档的版本通常不会超过 100 个，嵌入数组比关联表查询快得多。
    private List<DocVersion> versions;

    // 内部类：版本详情
    @Data
    public static class DocVersion {
        private String versionNo;    // V1.0, V1.1
        private String storagePath;  // MinIO/OSS 文件路径
        private Long fileSize;       // 字节数
        private String uploadedBy;   // 上传人用户名
        private String commitMsg;    // 修改记录
        private LocalDateTime uploadTime = LocalDateTime.now();
    }
}