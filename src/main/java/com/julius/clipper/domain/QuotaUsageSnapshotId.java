package com.julius.clipper.domain;

import lombok.*;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotaUsageSnapshotId implements Serializable {
    private String organizationId;
    private String featureId;
}
