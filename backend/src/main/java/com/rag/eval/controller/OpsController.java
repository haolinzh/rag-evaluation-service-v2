package com.rag.eval.controller;

import com.rag.eval.model.ChunkPage;
import com.rag.eval.model.OpsStatus;
import com.rag.eval.model.SystemStatus;
import com.rag.eval.service.OpsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final OpsService opsService;

    public OpsController(OpsService opsService) {
        this.opsService = opsService;
    }

    @GetMapping("/status")
    public OpsStatus status() {
        return opsService.status();
    }

    @GetMapping("/system")
    public SystemStatus system() {
        return opsService.systemStatus();
    }

    @GetMapping("/chunks")
    public ChunkPage chunks(@RequestParam(defaultValue = "pg") String backend,
                            @RequestParam(required = false) String fileName,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return opsService.chunks(backend, fileName, page, size);
    }
}
