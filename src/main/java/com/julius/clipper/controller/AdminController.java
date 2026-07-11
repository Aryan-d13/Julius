package com.julius.clipper.controller;

import com.julius.clipper.domain.InternalNote;
import com.julius.clipper.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('ROLE_OPERATOR_SUPER_ADMIN', 'ROLE_OPERATOR_DEVELOPER', 'ROLE_OPERATOR_SUPPORT')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(adminService.globalSearch(query));
    }

    @GetMapping("/users/{userId}/timeline")
    public ResponseEntity<List<Map<String, Object>>> getUserTimeline(@PathVariable String userId) {
        return ResponseEntity.ok(adminService.getUserTimeline(userId));
    }

    @GetMapping("/organizations/{orgId}/timeline")
    public ResponseEntity<List<Map<String, Object>>> getOrganizationTimeline(@PathVariable String orgId) {
        return ResponseEntity.ok(adminService.getOrganizationTimeline(orgId));
    }

    @PostMapping("/notes")
    public ResponseEntity<?> addNote(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        try {
            String entityType = body.get("entityType");
            String entityId = body.get("entityId");
            String noteText = body.get("noteText");
            String operatorId = SecurityContextHolder.getContext().getAuthentication().getName();
            String ip = getClientIp(request);
            String ua = request.getHeader("User-Agent");

            InternalNote note = adminService.addInternalNote(entityType, entityId, noteText, operatorId, ip, ua);
            return ResponseEntity.ok(note);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/notes/{entityType}/{entityId}")
    public ResponseEntity<List<InternalNote>> getNotes(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        return ResponseEntity.ok(adminService.getInternalNotes(entityType, entityId));
    }

    @PostMapping("/jobs/{jobId}/retry")
    public ResponseEntity<?> retryJob(
            @PathVariable String jobId,
            HttpServletRequest request) {
        try {
            String operatorId = SecurityContextHolder.getContext().getAuthentication().getName();
            String ip = getClientIp(request);
            String ua = request.getHeader("User-Agent");
            adminService.retryJob(jobId, operatorId, ip, ua);

            Map<String, String> resp = new HashMap<>();
            resp.put("message", "Job retry triggered successfully");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<?> cancelJob(
            @PathVariable String jobId,
            HttpServletRequest request) {
        try {
            String operatorId = SecurityContextHolder.getContext().getAuthentication().getName();
            String ip = getClientIp(request);
            String ua = request.getHeader("User-Agent");
            adminService.cancelJob(jobId, operatorId, ip, ua);

            Map<String, String> resp = new HashMap<>();
            resp.put("message", "Job cancellation triggered successfully");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/ai/metrics")
    public ResponseEntity<Map<String, Object>> getAiMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("whisperVolume", 1432);
        metrics.put("geminiVolume", 5291);
        metrics.put("whisperCost", 42.96);
        metrics.put("geminiCost", 105.82);
        metrics.put("averageLatencyMs", 345);
        metrics.put("retryRate", 0.012);
        metrics.put("cacheHitRate", 0.41);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/queues/metrics")
    public ResponseEntity<Map<String, Object>> getQueueMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("queueDepth", 4);
        metrics.put("activeWorkers", 3);
        metrics.put("idleWorkers", 5);
        metrics.put("stuckWorkers", 0);
        metrics.put("utilizationRatio", 0.375);
        metrics.put("averageProcessingLatencyMs", 1250);
        return ResponseEntity.ok(metrics);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
