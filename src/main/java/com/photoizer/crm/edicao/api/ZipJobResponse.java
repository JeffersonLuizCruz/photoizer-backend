package com.photoizer.crm.edicao.api;

import java.util.UUID;

public record ZipJobResponse(
    UUID jobId,
    String status,
    String downloadUrl
) {}
