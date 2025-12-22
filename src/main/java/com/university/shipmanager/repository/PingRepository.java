package com.university.shipmanager.repository;

import com.university.shipmanager.entity.mongo.PingDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PingRepository extends MongoRepository<PingDoc, String> {
}
