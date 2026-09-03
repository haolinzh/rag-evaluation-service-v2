package com.rag.eval.controller;

import com.rag.eval.model.OpsReport;
import com.rag.eval.service.AuthService;
import com.rag.eval.service.NotificationService;
import com.rag.eval.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;
    private final AuthService authService;
    private final NotificationService notificationService;

    public ReportController(ReportService reportService, AuthService authService,
                            NotificationService notificationService) {
        this.reportService = reportService;
        this.authService = authService;
        this.notificationService = notificationService;
    }

    @GetMapping("/summary")
    public OpsReport summary() {
        return reportService.getSummary();
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> downloadCsv() {
        String csv = reportService.generateCsv();
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        notificationService.notify("report", "下载报告", "导出运维指标 CSV", authService.currentUser(), null);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=operations_report.csv")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(bytes);
    }
}
