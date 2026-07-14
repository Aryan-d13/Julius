package com.julius.clipper.domain;

import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageEventId implements Serializable {
    private String id;
    private LocalDateTime createdAt;
}
