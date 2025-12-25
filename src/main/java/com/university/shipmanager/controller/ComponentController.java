package com.university.shipmanager.controller;

import com.university.shipmanager.service.ComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/components")
@RequiredArgsConstructor // 1. 自动生成构造函数，注入 final 字段
public class ComponentController {

    // 2. 注入 Service (必须是 final)
    private final ComponentService componentService;

    /**
     * 级联删除接口
     * DELETE /api/components/{id}
     */
    @DeleteMapping("{id}")
    public String deleteComponent(@PathVariable String id) {
        // 调用 Service 执行“毁灭打击”
        componentService.deleteComponentAndChildren(id);
        return "删除成功！该节点及其子树、关联文档、物理文件已全部清理。";
    }
}