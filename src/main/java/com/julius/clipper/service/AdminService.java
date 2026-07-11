package com.julius.clipper.service;

import com.julius.clipper.domain.*;
import com.julius.clipper.pipeline.QueueProvider;
import com.julius.clipper.pipeline.TaskStatus;
import com.julius.clipper.pipeline.TaskType;
import com.julius.clipper.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final JobRepository jobRepository;
    private final MembershipRepository membershipRepository;
    private final InternalNoteRepository internalNoteRepository;
    private final AdminAuditEventRepository adminAuditEventRepository;
    private final LoginAuditLogRepository loginAuditLogRepository;
    private final QueueProvider queueProvider;

    public AdminService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            WorkspaceRepository workspaceRepository,
            JobRepository jobRepository,
            MembershipRepository membershipRepository,
            InternalNoteRepository internalNoteRepository,
            AdminAuditEventRepository adminAuditEventRepository,
            LoginAuditLogRepository loginAuditLogRepository,
            QueueProvider queueProvider) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.workspaceRepository = workspaceRepository;
        this.jobRepository = jobRepository;
        this.membershipRepository = membershipRepository;
        this.internalNoteRepository = internalNoteRepository;
        this.adminAuditEventRepository = adminAuditEventRepository;
        this.loginAuditLogRepository = loginAuditLogRepository;
        this.queueProvider = queueProvider;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> globalSearch(String query) {
        Map<String, Object> results = new HashMap<>();
        String normalizedQuery = "%" + query.toLowerCase() + "%";

        // 1. Search Users
        List<Map<String, String>> users = userRepository.findAll().stream()
                .filter(u -> u.getEmail().toLowerCase().contains(query.toLowerCase()) || 
                             u.getFullName().toLowerCase().contains(query.toLowerCase()))
                .limit(10)
                .map(u -> Map.of("id", u.getId(), "email", u.getEmail(), "fullName", u.getFullName()))
                .toList();
        results.put("users", users);

        // 2. Search Organizations
        List<Map<String, String>> orgs = organizationRepository.findAll().stream()
                .filter(o -> o.getName().toLowerCase().contains(query.toLowerCase()) && o.getDeletedAt() == null)
                .limit(10)
                .map(o -> Map.of("id", o.getId(), "name", o.getName()))
                .toList();
        results.put("organizations", orgs);

        // 3. Search Workspaces
        List<Map<String, String>> workspaces = workspaceRepository.findAll().stream()
                .filter(w -> w.getName().toLowerCase().contains(query.toLowerCase()) && w.getDeletedAt() == null)
                .limit(10)
                .map(w -> Map.of("id", w.getId(), "name", w.getName(), "orgId", w.getOrganization().getId()))
                .toList();
        results.put("workspaces", workspaces);

        // 4. Search Jobs
        List<Map<String, String>> jobs = jobRepository.findAll().stream()
                .filter(j -> j.getId().equalsIgnoreCase(query) || j.toApiStatus().contains(query.toLowerCase()))
                .limit(10)
                .map(j -> Map.of("id", j.getId(), "status", j.toApiStatus(), "workspaceId", j.getWorkspaceId() != null ? j.getWorkspaceId() : "none"))
                .toList();
        results.put("jobs", jobs);

        return results;
    }

    @Transactional
    public InternalNote addInternalNote(String entityType, String entityId, String noteText, String operatorId, String ipAddress, String userAgent) {
        InternalNote note = InternalNote.builder()
                .entityType(entityType.toUpperCase())
                .entityId(entityId)
                .operatorUserId(operatorId)
                .noteText(noteText)
                .build();
        note = internalNoteRepository.save(note);

        // Audit the action
        logAdminAction(operatorId, "NOTE_ATTACHED", entityId, ipAddress, userAgent, 
                       String.format("Attached internal note to %s: '%s'", entityType, noteText.substring(0, Math.min(noteText.length(), 30))));

        return note;
    }

    @Transactional(readOnly = true)
    public List<InternalNote> getInternalNotes(String entityType, String entityId) {
        return internalNoteRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType.toUpperCase(), entityId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserTimeline(String userId) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Registration Event
        timeline.add(Map.of(
                "timestamp", user.getCreatedAt(),
                "event", "USER_REGISTERED",
                "description", "User account registered: " + user.getEmail()
        ));

        // Membership updates
        List<Membership> memberships = membershipRepository.findActiveMembershipsForUser(userId);
        memberships.forEach(m -> timeline.add(Map.of(
                "timestamp", m.getCreatedAt(),
                "event", "MEMBERSHIP_ACTIVE",
                "description", String.format("Joined organization '%s' with role %s", m.getOrganization().getName(), m.getRole().getName())
        )));

        // Login history
        List<LoginAuditLog> logins = loginAuditLogRepository.findAll().stream()
                .filter(l -> userId.equals(l.getUserId()))
                .limit(10)
                .toList();
        logins.forEach(l -> timeline.add(Map.of(
                "timestamp", l.getCreatedAt(),
                "event", l.getEventType(),
                "description", String.format("Authentication trigger from IP: %s (UA: %s)", l.getIpAddress(), l.getUserAgent())
        )));

        timeline.sort((a, b) -> ((LocalDateTime) b.get("timestamp")).compareTo((LocalDateTime) a.get("timestamp")));
        return timeline;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getOrganizationTimeline(String orgId) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        Organization org = organizationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found or deleted"));

        // Creation Event
        timeline.add(Map.of(
                "timestamp", org.getCreatedAt(),
                "event", "ORGANIZATION_CREATED",
                "description", "Organization created: " + org.getName()
        ));

        // Workspace creation events
        List<Workspace> workspaces = workspaceRepository.findByOrganizationIdAndDeletedAtIsNull(orgId);
        workspaces.forEach(w -> timeline.add(Map.of(
                "timestamp", w.getCreatedAt(),
                "event", "WORKSPACE_CREATED",
                "description", "Workspace created inside organization: " + w.getName()
        )));

        timeline.sort((a, b) -> ((LocalDateTime) b.get("timestamp")).compareTo((LocalDateTime) a.get("timestamp")));
        return timeline;
    }

    @Transactional
    public void retryJob(String jobId, String operatorId, String ipAddress, String userAgent) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        job.setStatus(JobDBStatus.PENDING);
        job.setErrorMessage(null);
        job.setErrorCode(null);
        job.setRetryCount(job.getRetryCount() + 1);
        jobRepository.save(job);

        // Build Task payload copying original params
        Map<String, Object> taskPayload = new HashMap<>();
        taskPayload.put("job_id", job.getId());
        taskPayload.put("user_id", job.getUserId());
        taskPayload.put("workspace_id", job.getWorkspaceId() != null ? job.getWorkspaceId() : "none");
        taskPayload.put("url", job.getConfig().getUrl());
        taskPayload.put("count", job.getConfig().getCount());
        taskPayload.put("copy_language", job.getConfig().getCopyLanguage() != null ? job.getConfig().getCopyLanguage() : "en");
        taskPayload.put("min_duration", job.getConfig().getMinDuration() > 0 ? job.getConfig().getMinDuration() : 30.0);
        taskPayload.put("max_duration", job.getConfig().getMaxDuration() > 0 ? job.getConfig().getMaxDuration() : 900.0);

        Map<String, Object> taskMetadata = new HashMap<>();
        taskMetadata.put("correlation_id", job.getCorrelationId());
        taskMetadata.put("request_id", "retry-" + UUID.randomUUID().toString().substring(0, 8));

        Task downloadTask = Task.builder()
                .id(UUID.randomUUID().toString())
                .type(TaskType.DOWNLOAD)
                .payload(taskPayload)
                .metadata(taskMetadata)
                .status(TaskStatus.PENDING)
                .build();

        queueProvider.push(downloadTask);

        logAdminAction(operatorId, "JOB_RETRY", jobId, ipAddress, userAgent, "Triggered manual retry for Job " + jobId);
    }

    @Transactional
    public void cancelJob(String jobId, String operatorId, String ipAddress, String userAgent) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        job.setStatus(JobDBStatus.CANCELLED);
        jobRepository.save(job);

        logAdminAction(operatorId, "JOB_CANCEL", jobId, ipAddress, userAgent, "Triggered manual cancellation for Job " + jobId);
    }

    private void logAdminAction(String operatorId, String action, String resourceId, String ipAddress, String userAgent, String details) {
        AdminAuditEvent event = AdminAuditEvent.builder()
                .operatorUserId(operatorId)
                .action(action)
                .targetResourceId(resourceId)
                .ipAddress(ipAddress != null ? ipAddress : "unknown")
                .userAgent(userAgent)
                .details(details)
                .build();
        adminAuditEventRepository.save(event);
    }
}
