package com.university.shipmanager.controller;

import com.university.shipmanager.controller.dto.CreateDocRequest;
import com.university.shipmanager.service.DocService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/docs")
public class DocController {

    private final DocService docService;

    public DocController(DocService docService) {
        this.docService = docService;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateDocRequest req) {
        return docService.createDoc(req.shipId, req.title, req.category, req.metadata, req.content);
    }
}
