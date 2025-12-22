package com.university.shipmanager.service;

import com.university.shipmanager.entity.mongo.DocVersion;
import com.university.shipmanager.entity.mongo.ShipDocument;
import com.university.shipmanager.entity.mysql.DocIndex;
import com.university.shipmanager.mapper.DocIndexMapper;
import com.university.shipmanager.repository.ShipDocumentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class DocService {

    private final ShipDocumentRepository shipDocumentRepository;
    private final DocIndexMapper docIndexMapper;

    public DocService(ShipDocumentRepository shipDocumentRepository, DocIndexMapper docIndexMapper) {
        this.shipDocumentRepository = shipDocumentRepository;
        this.docIndexMapper = docIndexMapper;
    }

    public Map<String, Object> createDoc(Long shipId, String title, String category,
                                         Map<String, Object> metadata, String content) {

        // 1) 写 Mongo（内容库）
        ShipDocument mongoDoc = new ShipDocument();
        mongoDoc.setShipId(shipId);
        mongoDoc.setTitle(title);
        mongoDoc.setCategory(category);
        mongoDoc.setMetadata(metadata);
        mongoDoc.setCreatedAt(Instant.now());

        DocVersion v1 = new DocVersion();
        v1.setVersion(1);
        v1.setStatus("DRAFT");
        v1.setContent(content);
        v1.setCreatedAt(Instant.now());

        mongoDoc.setVersions(List.of(v1));
        mongoDoc.setCurrentVersion(1);

        ShipDocument savedMongo = shipDocumentRepository.save(mongoDoc);
        String mongoId = savedMongo.getId();

        // 2) 写 MySQL（索引库）并回填 mongo_doc_id
        DocIndex idx = new DocIndex();
        idx.setShipId(shipId);
        idx.setTitle(title);
        idx.setCategory(category);
        idx.setStatus("DRAFT");
        idx.setLatestVersion(1);
        idx.setMongoDocId(mongoId);
        idx.setCreatedAt(LocalDateTime.now());

        try {
            docIndexMapper.insert(idx);
        } catch (Exception ex) {
            // MySQL失败：回滚Mongo（避免“孤儿文档”）
            shipDocumentRepository.deleteById(mongoId);
            throw ex;
        }

        return Map.of(
                "docIndexId", idx.getId(),
                "mongoId", mongoId
        );
    }
}
