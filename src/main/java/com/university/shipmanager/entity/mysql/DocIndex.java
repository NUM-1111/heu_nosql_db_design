package com.university.shipmanager.entity.mysql;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doc_index")
public class DocIndex {
    private Long id;
    private Long shipId;
    private String title;
    private String category;
    private String status;
    private Integer latestVersion;
    private String mongoDocId;
    private LocalDateTime createdAt;

    // getter/setter...
}
