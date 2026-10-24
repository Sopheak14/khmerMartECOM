package com.khmermart.model.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderImagesRequest(
    @NotEmpty(message = "Image IDs list cannot be empty") List<Long> ids) {}
