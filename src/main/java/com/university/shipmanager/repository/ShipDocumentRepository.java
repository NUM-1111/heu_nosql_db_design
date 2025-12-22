package com.university.shipmanager.repository;

import com.university.shipmanager.entity.mongo.ShipDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShipDocumentRepository extends MongoRepository<ShipDocument, String> {
}
