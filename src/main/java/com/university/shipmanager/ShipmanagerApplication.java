package com.university.shipmanager;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@MapperScan("com.university.shipmanager.mapper")
@EnableMongoRepositories(basePackages = "com.university.shipmanager.repository")
@SpringBootApplication
public class ShipmanagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShipmanagerApplication.class, args);
    }

}
