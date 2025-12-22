package com.university.shipmanager.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Document("ship_documents")
public class ShipDocument {
    @Id
    private String id;

    private Long shipId;
    private String title;
    private String category;

    // NoSQL优势：动态字段（不同船型字段不同，不改表）
    private Map<String, Object> metadata;

    // 版本数组：演示“版本控制”
    private List<DocVersion> versions;

    private Integer currentVersion;
    private Instant createdAt;

    // getter/setter 省略（你可用 Lombok @Data）
    // ...
}
