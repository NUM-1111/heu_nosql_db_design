package com.university.shipmanager.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private static final String MINIO_BASE_URL = "http://localhost:9000/ship-files/";

    private final DocIndexMapper docIndexMapper;       // MySQL 操作
    private final ShipDocumentRepository mongoRepository; // Mongo 操作
    private final com.university.shipmanager.common.MinioUtil minioUtil;


    /**
     * 核心功能：上传新文档
     * 场景：用户点击“上传”，填写了一堆属性，选了一个文件
     */
    @Transactional(rollbackFor = Exception.class) // MySQL 事务
    public void uploadNewDocument(UploadRequest request, org.springframework.web.multipart.MultipartFile file) {

        // 1. 【真实上传】先传文件到 MinIO
        String storagePath = minioUtil.uploadFile(file);

        // 2. 准备 MongoDB 数据
        ShipDocument mongoDoc = new ShipDocument();
        mongoDoc.setMetadata(request.getMetadata());

        ShipDocument.DocVersion v1 = new ShipDocument.DocVersion();
        v1.setVersionNo("V1.0");
        v1.setCommitMsg("Initial Upload");
        v1.setFileSize(file.getSize()); // 【真实】文件大小
        v1.setStoragePath(storagePath); // 【真实】MinIO 路径

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
            sqlIndex.setComponentId(request.getComponentId()); // 【新增】存入关联

            // --- 建立关联 ---
            sqlIndex.setMongoDocId(savedMongoDoc.getId()); // 把 Mongo ID 存进 MySQL

            // 4. 存入 MySQL
            docIndexMapper.insert(sqlIndex);
            log.info("MySQL 保存成功，IndexID: {}", sqlIndex.getId());

        } catch (Exception e) {
            // 5. 【手动回滚】如果 MySQL 挂了，要把刚才 Mongo 里存的垃圾数据删掉！
            log.error("MySQL 保存失败，执行 MongoDB 回滚...");
            minioUtil.removeFile(storagePath);
            throw e; // 继续抛出异常，让 Controller 知道失败了
        }
    }


    /**
     * 获取文档详情 + 下载链接
     * @param id 这是 MySQL 表里的 id (主键)
     */
    public DocumentDetailVO getDocumentDetail(Long id) {
        // 1. 先查 MySQL，拿到 mongoDocId
        DocIndex index = docIndexMapper.selectById(id);
        if (index == null) throw new RuntimeException("文档不存在");

        // 2. 再查 MongoDB，拿到详情
        ShipDocument mongoDoc = mongoRepository.findById(index.getMongoDocId())
                .orElseThrow(() -> new RuntimeException("文档详情丢失"));

        // 3. 组装返回给前端的对象 (VO)
        DocumentDetailVO vo = new DocumentDetailVO();
        vo.setId(index.getId());
        vo.setTitle(index.getTitle());
        vo.setMetadata(mongoDoc.getMetadata()); // 动态参数

        // 4. 【关键】处理版本列表，把 storagePath 变成真正的 URL
        List<DocumentDetailVO.VersionVO> versionVOs = new ArrayList<>();
        if (mongoDoc.getVersions() != null) {
            for (ShipDocument.DocVersion v : mongoDoc.getVersions()) {
                DocumentDetailVO.VersionVO vVo = new DocumentDetailVO.VersionVO();
                vVo.setVersionNo(v.getVersionNo());
                vVo.setFileSize(v.getFileSize());
                vVo.setUploadTime(v.getUploadTime());

                // 拼接 MinIO 公开访问地址
                // 例如: http://localhost:9000/ship-files/uuid-engine.pdf
                vVo.setDownloadUrl(MINIO_BASE_URL + v.getStoragePath());

                versionVOs.add(vVo);
            }
        }
        vo.setVersions(versionVOs);

        return vo;
    }

    /**
     * 查询文档列表 (支持按 船 或 按零件 筛选)
     */
    public List<DocIndex> listDocs(Long shipId, String componentId) {
        LambdaQueryWrapper<DocIndex> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(DocIndex::getShipId, shipId);

        // 如果传了 componentId，就查这个零件的；没传就查整艘船的(或者只查属于船级的文件，看你业务定义)
        if (componentId != null && !componentId.isEmpty()) {
            wrapper.eq(DocIndex::getComponentId, componentId);
        }

        wrapper.orderByDesc(DocIndex::getCreatedAt);
        return docIndexMapper.selectList(wrapper);
    }

    // --- 在 Service 内部或单独定义一个 VO 类 (View Object) ---
    @Data
    public static class DocumentDetailVO {
        private Long id;
        private String title;
        private Map<String, Object> metadata;
        private List<VersionVO> versions;

        @Data
        public static class VersionVO {
            private String versionNo;
            private String downloadUrl; // 前端要这个！
            private Long fileSize;
            private LocalDateTime uploadTime;
        }
    }

    // --- DTO: 前端传来的参数 ---
    @Data
    public static class UploadRequest {
        private Long shipId;
        private String componentId; // 【新增】
        private String title;
        private String fileName;
        private String category;
        // 动态参数：前端传 JSON，后端直接用 Map 接，NoSQL 的优势！
        private Map<String, Object> metadata;
    }
}