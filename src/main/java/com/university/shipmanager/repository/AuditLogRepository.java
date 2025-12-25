package com.university.shipmanager.repository;

import com.university.shipmanager.entity.mongo.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    // 自动拥有 save, findAll 等方法
}