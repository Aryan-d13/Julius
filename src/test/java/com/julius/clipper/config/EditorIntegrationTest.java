package com.julius.clipper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.*;
import com.julius.clipper.repository.*;
import com.julius.clipper.service.AuthService;
import com.julius.clipper.service.AuthService.AuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class EditorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobClipRepository jobClipRepository;

    @Autowired
    private EditSessionRepository sessionRepository;

    @Autowired
    private ClipVersionRepository versionRepository;

    @Autowired
    private SubtitleStyleRepository styleRepository;

    @Autowired
    private RenderProfileRepository profileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.julius.clipper.service.WaveformGenerator waveformGenerator;

    private String authToken;
    private String jobClipId;

    @BeforeEach
    public void setUp() throws Exception {
        sessionRepository.deleteAll();
        jobClipRepository.deleteAll();
        userRepository.deleteAll();

        if (roleRepository.findByName("ROLE_USER").isEmpty()) {
            roleRepository.save(new Role("role-user-uuid-placeholder-1111", "ROLE_USER"));
        }

        User user = authService.register("editor@julius.com", "Password123!", "Editor User");
        AuthResponse auth = authService.login("editor@julius.com", "Password123!", "127.0.0.1", "agent", "corr", "req");
        this.authToken = auth.accessToken();

        // Seed a JobClip
        JobClip clip = JobClip.builder()
                .id("clip-uuid-9999")
                .jobId("job-uuid-1111")
                .clipIndex(1)
                .filename("clip1.mp4")
                .storageKey("jobs/job-1111/clips/clip1.mp4")
                .url("http://storage/clip1.mp4")
                .durationSeconds(25.0)
                .sizeBytes(1024 * 1024L)
                .createdAt(LocalDateTime.now())
                .build();
        clip = jobClipRepository.save(clip);
        this.jobClipId = clip.getId();
    }

    @Test
    public void testEditSessionLifecycle() throws Exception {
        // 1. Create edit session
        String createRes = mockMvc.perform(post("/api/editor/sessions")
                .header("Authorization", "Bearer " + authToken)
                .param("clipId", jobClipId)
                .param("name", "My Reels Cut"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("My Reels Cut"))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> session = objectMapper.readValue(createRes, Map.class);
        String sessionId = (String) session.get("id");

        // 2. Fetch latest version (created automatically as seed)
        String latestRes = mockMvc.perform(get("/api/editor/sessions/" + sessionId + "/latest")
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.timelineState").isNotEmpty())
                .andExpect(jsonPath("$.stylePreset").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> latest = objectMapper.readValue(latestRes, Map.class);
        Map<?, ?> style = (Map<?, ?>) latest.get("stylePreset");
        String styleId = (String) style.get("id");

        // 3. Trigger autosave
        String updatedTimeline = "{\"durationSeconds\":25.0,\"tracks\":[{\"id\":\"track-sub\",\"type\":\"SUBTITLE\",\"segments\":[{\"id\":\"seg-1\",\"assetId\":\"clip-uuid-9999\",\"sourceStart\":0.0,\"timelineStart\":0.0,\"duration\":25.0,\"words\":[{\"text\":\"Hello\",\"start\":0.0,\"end\":1.0}]}]}]}";
        mockMvc.perform(post("/api/editor/sessions/" + sessionId + "/autosave")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "timelineState", updatedTimeline,
                        "stylePresetId", styleId
                ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTOSAVED"))
                .andExpect(jsonPath("$.versionNumber").value(2));

        // 4. Trigger named checkpoint
        mockMvc.perform(post("/api/editor/sessions/" + sessionId + "/checkpoint")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", "V2 Final Polish",
                        "timelineState", updatedTimeline,
                        "stylePresetId", styleId
                ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKPOINT_SAVED"))
                .andExpect(jsonPath("$.versionNumber").value(3));

        // Validate final database state matches version history
        Optional<ClipVersion> latestVer = versionRepository.findLatestVersionForSession(sessionId);
        assertThat(latestVer).isPresent();
        assertThat(latestVer.get().getVersionNumber()).isEqualTo(3);
        assertThat(latestVer.get().getName()).isEqualTo("V2 Final Polish");
    }

    @Test
    public void testTimelineValidationConstraints() throws Exception {
        String createRes = mockMvc.perform(post("/api/editor/sessions")
                .header("Authorization", "Bearer " + authToken)
                .param("clipId", jobClipId)
                .param("name", "Reels Trim"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> session = objectMapper.readValue(createRes, Map.class);
        String sessionId = (String) session.get("id");

        Optional<ClipVersion> latestVer = versionRepository.findLatestVersionForSession(sessionId);
        String styleId = latestVer.get().getStylePreset().getId();

        // Timeline state with overlapping segments (seg-1 end > seg-2 start)
        String invalidTimeline = "{\"durationSeconds\":25.0,\"tracks\":[{\"id\":\"track-sub\",\"type\":\"SUBTITLE\",\"segments\":[" +
                "{\"id\":\"seg-1\",\"assetId\":\"clip-uuid-9999\",\"sourceStart\":0.0,\"timelineStart\":0.0,\"duration\":15.0,\"words\":[]}," +
                "{\"id\":\"seg-2\",\"assetId\":\"clip-uuid-9999\",\"sourceStart\":0.0,\"timelineStart\":10.0,\"duration\":10.0,\"words\":[]}" +
                "]}]}";

        mockMvc.perform(post("/api/editor/sessions/" + sessionId + "/autosave")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "timelineState", invalidTimeline,
                        "stylePresetId", styleId
                ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Overlapping locked segments detected")));
    }

    @Test
    public void testTimelineValidationNegativeDuration() throws Exception {
        String createRes = mockMvc.perform(post("/api/editor/sessions")
                .header("Authorization", "Bearer " + authToken)
                .param("clipId", jobClipId)
                .param("name", "Reels Trim Negative"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> session = objectMapper.readValue(createRes, Map.class);
        String sessionId = (String) session.get("id");

        Optional<ClipVersion> latestVer = versionRepository.findLatestVersionForSession(sessionId);
        String styleId = latestVer.get().getStylePreset().getId();

        String negativeDurTimeline = "{\"durationSeconds\":25.0,\"tracks\":[{\"id\":\"track-sub\",\"type\":\"SUBTITLE\",\"segments\":[" +
                "{\"id\":\"seg-1\",\"assetId\":\"clip-uuid-9999\",\"sourceStart\":0.0,\"timelineStart\":0.0,\"duration\":-5.0,\"words\":[]}" +
                "]}]}";

        mockMvc.perform(post("/api/editor/sessions/" + sessionId + "/autosave")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "timelineState", negativeDurTimeline,
                        "stylePresetId", styleId
                ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Segment duration must be positive")));
    }

    @Test
    public void testWaveformGenerator() throws Exception {
        java.io.File tempWav = java.io.File.createTempFile("test_wave_", ".wav");
        tempWav.deleteOnExit();

        // Write dummy WAV header (44 bytes) and some samples
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempWav)) {
            byte[] header = new byte[44];
            fos.write(header);
            // Write 800 bytes of pcm samples (400 samples)
            byte[] samples = new byte[800];
            for (int i = 0; i < samples.length; i++) {
                samples[i] = (byte) (i % 127);
            }
            fos.write(samples);
        }

        String json = waveformGenerator.generateWaveformJson(tempWav, 20);
        assertThat(json).startsWith("[");
        assertThat(json).endsWith("]");

        // Parse json array
        double[] peaks = objectMapper.readValue(json, double[].class);
        assertThat(peaks.length).isEqualTo(20);
        for (double p : peaks) {
            assertThat(p).isBetween(0.0, 1.0);
        }
        tempWav.delete();
    }
}
