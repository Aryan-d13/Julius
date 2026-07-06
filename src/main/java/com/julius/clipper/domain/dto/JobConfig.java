package com.julius.clipper.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobConfig {
    private String url;
    private int count;
    
    @JsonProperty("min_duration")
    private double minDuration;
    
    @JsonProperty("max_duration")
    private double maxDuration;
    
    @JsonProperty("template_ref")
    private String templateRef;
    
    @JsonProperty("language_mode")
    private String languageMode;
    
    @JsonProperty("copy_language")
    private String copyLanguage;
    
    @JsonProperty("render_options")
    private Map<String, Object> renderOptions;
    
    @JsonProperty("source_title")
    private String sourceTitle;
    
    @JsonProperty("source_clip_id")
    private String sourceClipId;
    
    @JsonProperty("requested_count")
    private Integer requestedCount;
}
