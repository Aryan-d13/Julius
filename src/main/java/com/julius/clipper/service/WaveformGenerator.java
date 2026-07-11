package com.julius.clipper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

@Service
public class WaveformGenerator {
    private static final Logger log = LoggerFactory.getLogger(WaveformGenerator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateWaveformJson(File wavFile, int targetPoints) {
        log.info("Generating audio waveform envelope from WAV: {}", wavFile.getName());
        try {
            long fileLength = wavFile.length();
            if (fileLength <= 44) {
                return "[]";
            }

            long dataBytes = fileLength - 44;
            int totalSamples = (int) (dataBytes / 2); // 16-bit = 2 bytes per sample
            if (totalSamples <= 0) {
                return "[]";
            }

            int step = Math.max(1, totalSamples / targetPoints);
            List<Float> peaks = new ArrayList<>(targetPoints);
            float globalMax = 0.0f;

            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(wavFile))) {
                // Skip 44-byte WAV header
                long skipped = bis.skip(44);
                if (skipped < 44) {
                    log.warn("WAV file skipped less than header bytes: {}", skipped);
                }

                byte[] buffer = new byte[2];
                float currentMax = 0.0f;
                int sampleCount = 0;

                while (bis.read(buffer) == 2) {
                    short sample = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getShort();
                    float val = Math.abs((float) sample);
                    if (val > currentMax) {
                        currentMax = val;
                    }
                    sampleCount++;

                    if (sampleCount >= step) {
                        peaks.add(currentMax);
                        if (currentMax > globalMax) {
                            globalMax = currentMax;
                        }
                        currentMax = 0.0f;
                        sampleCount = 0;
                    }
                }
            }

            // Normalize peaks between 0.0 and 1.0
            List<Float> normalized = new ArrayList<>(peaks.size());
            float div = globalMax > 0 ? globalMax : 1.0f;
            for (float p : peaks) {
                normalized.add(p / div);
            }

            // Guarantee exactly targetPoints length by padding/trimming
            while (normalized.size() < targetPoints) {
                normalized.add(0.0f);
            }
            if (normalized.size() > targetPoints) {
                normalized = normalized.subList(0, targetPoints);
            }

            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            log.error("Failed to generate waveform envelope: {}", e.getMessage(), e);
            return "[]";
        }
    }
}
