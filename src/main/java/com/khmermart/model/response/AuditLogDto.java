package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogDto(
    Long id,
    @JsonProperty("actorId") Long actorId,
    String action,
    String entity,
    @JsonProperty("entityId") String entityId,
    @JsonProperty("beforeJson") JsonNode beforeJson,
    @JsonProperty("afterJson") JsonNode afterJson,
    String ip,
    @JsonProperty("createdAt") Instant createdAt) {}
