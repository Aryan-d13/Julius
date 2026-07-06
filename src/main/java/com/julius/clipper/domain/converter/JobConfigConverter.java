package com.julius.clipper.domain.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.dto.JobConfig;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class JobConfigConverter implements AttributeConverter<JobConfig, String> {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(JobConfig attribute) {
        if (attribute == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error writing JobConfig to JSON", e);
        }
    }

    @Override
    public JobConfig convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank() || dbData.equals("{}")) {
            return new JobConfig();
        }
        try {
            return objectMapper.readValue(dbData, JobConfig.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error reading JobConfig from JSON", e);
        }
    }
}
