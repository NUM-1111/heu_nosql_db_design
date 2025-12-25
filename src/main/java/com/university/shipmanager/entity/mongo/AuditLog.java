package com.university.shipmanager.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * 审计日志：记录谁干了什么坏事
 */
@Data
@Document(collection = "system_audit_logs")
public class AuditLog {
    @Id
    private String id;
    private String action;      // 操作类型：DELETE, UPLOAD
    private String targetType;  // 对象：Component, Document
    private String targetName;  // 对象名字：主柴油机, 维修手册.pdf
    private String operator;    // 操作人：admin (暂时写死)
    private String detail;      // 详情备注
    private LocalDateTime time = LocalDateTime.now();
}