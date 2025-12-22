package com.university.shipmanager.entity.mongo;

import lombok.Data;

import java.time.Instant;

@Data
public class DocVersion {
    private Integer version;
    private String status;   // DRAFT / PUBLISHED
    private String content;  // demo先存文本，后面可换成文件URL
    private Instant createdAt;

    // getter/setter...
}
