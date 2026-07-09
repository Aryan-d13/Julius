package com.julius.clipper;

import com.julius.clipper.domain.Job;
import com.julius.clipper.domain.JobDBStatus;
import com.julius.clipper.domain.Task;
import com.julius.clipper.domain.dto.JobConfig;
import com.julius.clipper.pipeline.*;
import com.julius.clipper.repository.JobClipRepository;
import com.julius.clipper.repository.JobRepository;
import com.julius.clipper.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "clipper.queue.type=db"
})
@ActiveProfiles("test")
public class ClipperPipelineTest {

    // Mock Redis dependencies to allow standard Spring Boot startup without a live Redis server
    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private StringRedisTemplate stringRedisTemplate;

    @MockBean(name = "DOWNLOADWorker")
    private com.julius.clipper.pipeline.Worker downloadWorker;

    @MockBean(name = "TRANSCRIBEWorker")
    private com.julius.clipper.pipeline.Worker transcribeWorker;

    @MockBean(name = "ANALYZEWorker")
    private com.julius.clipper.pipeline.Worker analyzeWorker;

    @MockBean(name = "SMART_RENDERWorker")
    private com.julius.clipper.pipeline.Worker smartRenderWorker;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobClipRepository jobClipRepository;

    @Autowired
    private DbQueue dbQueue;

    @Autowired
    private WorkerRunner workerRunner;

    @Test
    public void testEndToEndPipelineExecution() throws Exception {
        // Mock Redis Stream additions and Sequence increments
        when(stringRedisTemplate.opsForValue().increment(anyString())).thenReturn(1L);
        when(stringRedisTemplate.opsForSet().add(anyString(), anyString())).thenReturn(1L);
        when(stringRedisTemplate.opsForSet().size(anyString())).thenReturn(2L); // Trigger Join Gate immediately

        // Stub the mocked workers to return appropriate result maps
        when(downloadWorker.process(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            if (task.getType() == TaskType.DOWNLOAD_VIDEO) {
                return Map.of("video_key", "mock_video.mp4");
            } else {
                return Map.of("clip_id", "dQw4w9WgXcQ", "storage_key", "mock_audio.wav");
            }
        });

        when(transcribeWorker.process(any(Task.class))).thenReturn(Map.of(
            "clip_id", "dQw4w9WgXcQ",
            "transcript_key", "mock_transcript.json"
        ));

        when(analyzeWorker.process(any(Task.class))).thenReturn(Map.of(
            "analysis_results_cache", "mock_analysis_results_cache"
        ));

        when(smartRenderWorker.process(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            String jId = task.getJobId();
            return Map.of(
                "index", 1,
                "filename", "clip-01_untitled-source_template_" + jId.substring(0, Math.min(jId.length(), 8)) + ".mp4",
                "storage_key", "mock-rendered-clip.mp4",
                "url", "http://localhost:8080/data/jobs/" + jId + "/clips/clip.mp4",
                "duration_seconds", 30.0,
                "size_bytes", 2048L
            );
        });

        // 1. Create a mock Job
        String jobId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        JobConfig config = JobConfig.builder()
                .url("https://youtube.com/watch?v=dQw4w9WgXcQ")
                .count(1)
                .minDuration(10)
                .maxDuration(60)
                .templateRef("test-template")
                .languageMode("auto")
                .copyLanguage("en")
                .build();

        Job job = Job.builder()
                .id(jobId)
                .userId(userId)
                .correlationId("correlation-123")
                .config(config)
                .clipCount(1)
                .status(JobDBStatus.PENDING)
                .build();
        job = jobRepository.save(job);

        // 2. Enqueue the starting DOWNLOAD Task
        Map<String, Object> payload = new HashMap<>();
        payload.put("job_id", jobId);
        payload.put("user_id", userId);
        payload.put("url", config.getUrl());
        payload.put("count", config.getCount());
        payload.put("copy_language", config.getCopyLanguage());
        
        Task downloadTask = Task.builder()
                .id(UUID.randomUUID().toString())
                .type(TaskType.DOWNLOAD)
                .payload(payload)
                .status(TaskStatus.PENDING)
                .build();
        dbQueue.push(downloadTask);

        // Verify task is enqueued
        Task enqueuedTask = dbQueue.pop(TaskType.DOWNLOAD);
        assertNotNull(enqueuedTask);
        assertEquals(jobId, enqueuedTask.getJobId());
        
        // Reset status to PENDING for the worker runner to poll it
        enqueuedTask.setStatus(TaskStatus.PENDING);
        dbQueue.push(enqueuedTask);

        // 3. Start the Worker Runner to execute the pipeline concurrently
        workerRunner.start();

        // 4. Wait for the pipeline to finish processing the Fork-Join stages
        boolean isCompleted = false;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(200);
            Job currentJob = jobRepository.findById(jobId).orElse(null);
            if (currentJob != null && currentJob.getStatus() == JobDBStatus.COMPLETED) {
                isCompleted = true;
                break;
            }
        }

        // 5. Stop the Worker Runner
        workerRunner.stop();

        // 6. Assertions
        assertTrue(isCompleted, "Job should have successfully transitioned to COMPLETED status");
        
        Job finishedJob = jobRepository.findById(jobId).orElse(null);
        assertNotNull(finishedJob);
        assertEquals(JobDBStatus.COMPLETED, finishedJob.getStatus());
        assertEquals(1, finishedJob.getClipsReady());
        
        long registeredClips = jobClipRepository.countByJobId(jobId);
        assertEquals(1, registeredClips, "One clip output should be registered in the database");
    }

    @Autowired
    private SegmentMerger segmentMerger;

    @Autowired
    private SlidingWindowSelector slidingWindowSelector;

    @Test
    public void testSegmentMerger() {
        List<SegmentMerger.RawSegment> rawSegments = new ArrayList<>();
        
        rawSegments.add(new SegmentMerger.RawSegment("Hello.", 0.0, 2.0, Collections.emptyList()));
        rawSegments.add(new SegmentMerger.RawSegment("This is a segment", 2.1, 4.5, Collections.emptyList()));
        rawSegments.add(new SegmentMerger.RawSegment("after a silent pause.", 6.0, 9.0, Collections.emptyList()));

        List<SegmentMerger.MergedSegment> merged = segmentMerger.merge(rawSegments, false);
        
        assertEquals(3, merged.size());
        assertEquals("Hello.", merged.get(0).getText());
        assertEquals("This is a segment", merged.get(1).getText());
        assertEquals("after a silent pause.", merged.get(2).getText());
    }

    @Test
    public void testSlidingWindowSelector() {
        List<SlidingWindowSelector.Chunk> chunks = new ArrayList<>();
        chunks.add(new SlidingWindowSelector.Chunk(0.0, 20.0, 80.0, "Chunk 1"));
        chunks.add(new SlidingWindowSelector.Chunk(20.0, 40.0, 95.0, "Chunk 2"));
        chunks.add(new SlidingWindowSelector.Chunk(40.0, 60.0, 90.0, "Chunk 3"));
        chunks.add(new SlidingWindowSelector.Chunk(60.0, 80.0, 50.0, "Chunk 4"));
        chunks.add(new SlidingWindowSelector.Chunk(80.0, 100.0, 40.0, "Chunk 5"));

        SlidingWindowSelector selector = new SlidingWindowSelector(60.0, 60.0);
        List<SlidingWindowSelector.Window> best = selector.findTopNWindows(chunks, 1);
        
        assertEquals(1, best.size());
        assertEquals(0.0, best.get(0).getStart());
        assertEquals(60.0, best.get(0).getEnd());
        assertEquals(88.3, best.get(0).getScore(), 0.1);
    }
}
