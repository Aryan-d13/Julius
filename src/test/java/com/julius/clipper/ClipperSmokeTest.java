package com.julius.clipper;

import com.julius.clipper.domain.Job;
import com.julius.clipper.domain.JobDBStatus;
import com.julius.clipper.domain.dto.JobConfig;
import com.julius.clipper.pipeline.WorkerRunner;
import com.julius.clipper.repository.JobClipRepository;
import com.julius.clipper.repository.JobRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("live")
@Tag("integration")
public class ClipperSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(ClipperSmokeTest.class);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobClipRepository jobClipRepository;

    @Autowired
    private WorkerRunner workerRunner;

    @Test
    public void runLiveVideoEndToEndPipeline() throws Exception {
        log.info("Starting live environment end-to-end integration smoke test...");

        // Start WorkerRunner task execution threads
        workerRunner.start();

        try {
            JobConfig config = JobConfig.builder()
                    .url("https://www.youtube.com/watch?v=dQw4w9WgXcQ") // Rickroll live test link
                    .count(1)
                    .minDuration(5.0)
                    .maxDuration(30.0)
                    .templateRef("default-dev")
                    .languageMode("auto")
                    .copyLanguage("en")
                    .build();

            // Submit job configuration via REST endpoint POST /api/jobs
            ResponseEntity<Map> postResponse = restTemplate.postForEntity("/api/jobs", config, Map.class);
            assertEquals(HttpStatus.ACCEPTED, postResponse.getStatusCode());
            assertNotNull(postResponse.getBody());
            
            String jobId = (String) postResponse.getBody().get("jobId");
            assertNotNull(jobId);
            log.info("Submitted test job config via REST API. Received jobId: {}", jobId);

            // Wait for pipeline execution to complete cleanly (timeout fence of 8 minutes)
            boolean jobCompleted = false;
            long timeoutMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(8);
            
            while (System.currentTimeMillis() < timeoutMillis) {
                Thread.sleep(5000); // Poll every 5 seconds
                
                Job job = jobRepository.findById(jobId).orElse(null);
                if (job != null) {
                    log.info("Polling Job {} status: {}, step: {}, clips ready: {}/{}", 
                            jobId, job.getStatus(), job.getCurrentStep(), job.getClipsReady(), job.getClipCount());
                    
                    if (job.getStatus() == JobDBStatus.COMPLETED) {
                        jobCompleted = true;
                        break;
                    } else if (job.getStatus() == JobDBStatus.FAILED) {
                        fail("Smoke test pipeline failed on job execution. Error message: " + job.getErrorMessage());
                    }
                }
            }

            assertTrue(jobCompleted, "End-to-end pipeline execution failed to transition Job status to COMPLETED within 8 minutes.");

            // Verify clips were successfully generated and registered in DB
            long registeredClips = jobClipRepository.countByJobId(jobId);
            assertTrue(registeredClips > 0, "No clip fragments were registered in the database for the completed job.");

            log.info("Live end-to-end pipeline smoke test completed successfully!");
        } finally {
            workerRunner.stop();
        }
    }
}
