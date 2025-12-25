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
import cn.hutool.core.util.StrUtil; // 记得引入 Hutool
import com.university.shipmanager.entity.mongo.AuditLog;
import com.university.shipmanager.repository.AuditLogRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;


@Slf4j
@Service
@RequiredArgsConstructor // Lombok 自动生成构造函数注入 Bean
public class DocumentService {
    private static final String MINIO_BASE_URL = "http://localhost:9000/ship-files/";

    private final DocIndexMapper docIndexMapper;       // MySQL 操作
    private final ShipDocumentRepository mongoRepository; // Mongo 操作
    private final com.university.shipmanager.common.MinioUtil minioUtil;
    private final AuditLogRepository auditLogRepository;


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
     * 【升级】分页查询文档
     * @param pageNum 当前页码 (1开始)
     * @param pageSize 每页条数
     */
    public IPage<DocIndex> listDocs(Long shipId, String componentId, String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<DocIndex> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(DocIndex::getShipId, shipId);

        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(DocIndex::getTitle, keyword);
        }

        if (StrUtil.isBlank(keyword) && StrUtil.isNotBlank(componentId)) {
            wrapper.eq(DocIndex::getComponentId, componentId);
        }

        wrapper.orderByDesc(DocIndex::getCreatedAt);

        // 执行分页查询
        // MyBatis Plus 会自动生成 LIMIT 0, 10 这种 SQL
        return docIndexMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 【级联删除核心】根据一组 Component ID，删除它们关联的所有文档
     * 步骤：查 MySQL -> 删 MinIO 文件 -> 删 Mongo 详情 -> 删 MySQL 索引
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocumentsByComponentIds(List<String> componentIds) {
        if (componentIds == null || componentIds.isEmpty()) return;

        // 1. 先去 MySQL 查出这就这几个零件下所有的文档
        // SQL: SELECT * FROM doc_index WHERE component_id IN ('id1', 'id2', ...)
        LambdaQueryWrapper<DocIndex> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DocIndex::getComponentId, componentIds);
        List<DocIndex> docsToDelete = docIndexMapper.selectList(wrapper);

        if (docsToDelete.isEmpty()) return;

        // 2. 遍历每一个文档，执行删除
        for (DocIndex doc : docsToDelete) {
            // A. 查出 MongoDB 里的详情 (为了拿到文件名去删 MinIO)
            ShipDocument mongoDoc = mongoRepository.findById(doc.getMongoDocId()).orElse(null);

            if (mongoDoc != null && mongoDoc.getVersions() != null) {
                // B. 遍历所有版本，删掉 MinIO 里的文件
                for (ShipDocument.DocVersion v : mongoDoc.getVersions()) {
                    if (v.getStoragePath() != null) {
                        minioUtil.removeFile(v.getStoragePath()); // 🧹 清理硬盘
                    }
                }
                // C. 删掉 MongoDB 里的记录
                mongoRepository.deleteById(doc.getMongoDocId());
            }

            // D. 删掉 MySQL 里的记录
            docIndexMapper.deleteById(doc.getId());

            // 【新增】记录审计日志
            AuditLog log = new AuditLog();
            log.setAction("DELETE_DOC");
            log.setTargetType("Document");
            log.setTargetName(doc.getTitle());
            log.setOperator("admin"); // 暂时写死
            log.setDetail("级联删除了文档，原所属零件ID: " + doc.getComponentId());
            auditLogRepository.save(log);
        }

        log.info("级联删除了 {} 个文档，涉及零件: {}", docsToDelete.size(), componentIds);
    }

    /**
     * 【新增】更新文档基本信息
     */
    public void updateDocumentInfo(Long id, String title, String category) {
        DocIndex doc = docIndexMapper.selectById(id);
        if (doc == null) throw new RuntimeException("文档不存在");

        doc.setTitle(title);
        doc.setCategory(category);
        // 使用 MyBatis-Plus 的 updateById 更新
        docIndexMapper.updateById(doc);
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