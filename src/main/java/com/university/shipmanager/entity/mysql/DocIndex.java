package com.university.shipmanager.entity.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应 MySQL 中的 doc_index 表
 * 作用：作为文档的“户口本”，存储核心检索字段
 */
@Data
@TableName("doc_index")
public class DocIndex {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shipId;      // 所属船舶 ID
    private String title;     // 文档标题
    private String category;  // 分类 (如：图纸、合同)
    private String status;    // 状态 (DRAFT, RELEASED)

    private Integer latestVersion; // 当前最新版本号 (冗余字段，方便列表显示)

    // --- 核心指针 ---
    private String mongoDocId; // 指向 MongoDB 中 ship_document_details 的 _id
    private String componentId;

    private LocalDateTime createdAt;

}