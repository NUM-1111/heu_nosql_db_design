package com.university.shipmanager.common;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Component
public class MinioUtil {

    // 硬编码配置 (生产环境应该放 application.yml)
    private static final String ENDPOINT = "http://localhost:9000";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String BUCKET_NAME = "ship-files";

    private final MinioClient minioClient;

    public MinioUtil() {
        this.minioClient = MinioClient.builder()
                .endpoint(ENDPOINT)
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();
    }

    /**
     * 上传文件
     * @return 文件访问 URL (或存储路径)
     */
    public String uploadFile(MultipartFile file) {
        // 生成唯一文件名，防止重名覆盖
        String originalFilename = file.getOriginalFilename();
        String fileName = UUID.randomUUID() + "_" + originalFilename;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET_NAME)                        // 1. 指定存在哪个“桶”里（类似于 Windows 的 D盘）
                            .object(fileName)                           // 2. 指定存进去叫什么名字（UUID 防止重名）
                            .stream(inputStream, file.getSize(), -1)    // 3. 【关键】把水管接上，把数据流灌进去
                            .contentType(file.getContentType())         // 4. 告诉它这是 PDF 还是 JPG
                            .build()
            );
            log.info("文件上传成功: {}", fileName);
            // 返回文件路径 (简单起见，这里只返回文件名，或者你可以返回完整 http URL)
            return fileName;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败");
        }
    }

    /**
     * 删除文件
     */
    public void removeFile(String fileName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(fileName)
                            .build()
            );
            log.info("文件删除成功: {}", fileName);
        } catch (Exception e) {
            log.error("文件删除失败", e);
            // 删除失败不抛异常，打印日志即可，避免影响主业务
        }
    }
}