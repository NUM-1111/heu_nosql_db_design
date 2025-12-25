package com.university.shipmanager.controller;

import com.university.shipmanager.entity.mysql.DocIndex;
import com.university.shipmanager.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/docs") // 定义统一的路由前缀
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 真正的上传接口
     * 请求方式: POST
     * Content-Type: multipart/form-data
     */
    @PostMapping("/upload")
    public String upload(
            // 1. 捕获文件 (前端 key 必须叫 "file")
            @RequestParam("file") MultipartFile file,

            // 2. 捕获基本信息 (前端 form-data 里的 key)
            @RequestParam("shipId") Long shipId,
            @RequestParam("title") String title,
            @RequestParam("category") String category,
            @RequestParam(value = "componentId", required = false) String componentId, // 【新增】可选

            // 3. 简化处理：暂时不强求前端传复杂的 JSON metadata，我们后端模拟一下
            // 如果要传，可以让前端传一个 JSON 字符串，这里用 String 接收再转 Map
            @RequestParam(value = "metadata", required = false) String metadataJson) {
        log.info("接收到上传请求: shipId={}, title={}, filename={}", shipId, title, file.getOriginalFilename());

        // 组装 DTO
        DocumentService.UploadRequest request = new DocumentService.UploadRequest();
        request.setShipId(shipId);
        request.setTitle(title);
        request.setCategory(category);
        request.setComponentId(componentId);

        // 模拟一些动态参数 (实际项目中应该解析 metadataJson)
        request.setMetadata(Map.of(
                "uploaded_via", "Web API",
                "original_name", file.getOriginalFilename()));

        // 调用 Service (这里 file 就传进去了！)
        documentService.uploadNewDocument(request, file);

        return "上传成功！文件名: " + file.getOriginalFilename();
    }

    /**
     * 获取文档列表 (支持搜索)
     * GET /api/docs/list?shipId=888&keyword=手册
     */
    @GetMapping("/list")
    public List<DocIndex> list(
            @RequestParam Long shipId,
            @RequestParam(required = false) String componentId,
            @RequestParam(required = false) String keyword // 【新增】
    ) {
        return documentService.listDocs(shipId, componentId, keyword);
    }

    /**
     * 获取文档详情
     * GET /api/docs/1
     */
    @GetMapping("/{id}")
    public DocumentService.DocumentDetailVO getDetail(@PathVariable Long id) {
        return documentService.getDocumentDetail(id);
    }
}