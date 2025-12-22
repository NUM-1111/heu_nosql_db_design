package com.university.shipmanager.controller.dto;

import java.util.Map;

public class CreateDocRequest {
    public Long shipId;
    public String title;
    public String category;
    public Map<String, Object> metadata;
    public String content;
}
