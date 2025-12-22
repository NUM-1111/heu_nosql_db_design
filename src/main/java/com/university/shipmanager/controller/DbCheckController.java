package com.university.shipmanager.controller;

import com.university.shipmanager.entity.mongo.PingDoc;
import com.university.shipmanager.repository.PingRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DbCheckController {

    private final JdbcTemplate jdbcTemplate;
    private final PingRepository pingRepository;

    public DbCheckController(JdbcTemplate jdbcTemplate, PingRepository pingRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.pingRepository = pingRepository;
    }

    @GetMapping("/check/db")
    public Map<String, Object> checkDb() {
        // MySQL：查一下当前数据库名
        String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

        // Mongo：插入一条文档
        PingDoc saved = pingRepository.save(new PingDoc("mongo ok"));

        return Map.of(
                "mysql_database", dbName,
                "mongo_saved_id", saved.getId(),
                "mongo_msg", saved.getMsg()
        );
    }
}
