package com.khmermart.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.model.entity.AuditLog;
import com.khmermart.model.response.AuditLogDto;
import com.khmermart.repository.AuditLogRepository;
import com.khmermart.service.AuditLogService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogServiceImpl implements AuditLogService {

  private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;

  public AuditLogServiceImpl(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void log(
      Long actorId,
      String action,
      String entity,
      String entityId,
      Object before,
      Object after,
      String ip) {
    try {
      String beforeJson = serialize(before);
      String afterJson = serialize(after);

      AuditLog entry =
          AuditLog.builder()
              .actorId(actorId)
              .action(action)
              .entity(entity)
              .entityId(entityId)
              .beforeJson(beforeJson)
              .afterJson(afterJson)
              .ip(ip)
              .createdAt(Instant.now())
              .build();

      auditLogRepository.save(entry);
    } catch (Exception e) {
      log.error("Failed to write audit log for {} {}: {}", entity, action, e.getMessage(), e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AuditLogDto> getAuditLogs(Long actorId, String entity, Pageable pageable) {
    Specification<AuditLog> spec = Specification.where(null);

    if (actorId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
    }
    if (entity != null && !entity.isBlank()) {
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.equal(cb.upper(root.get("entity")), entity.trim().toUpperCase()));
    }

    return auditLogRepository.findAll(spec, pageable).map(this::mapToDto);
  }

  private AuditLogDto mapToDto(AuditLog log) {
    JsonNode beforeNode = parseJson(log.getBeforeJson());
    JsonNode afterNode = parseJson(log.getAfterJson());

    return new AuditLogDto(
        log.getId(),
        log.getActorId(),
        log.getAction(),
        log.getEntity(),
        log.getEntityId(),
        beforeNode,
        afterNode,
        log.getIp(),
        log.getCreatedAt());
  }

  private String serialize(Object obj) {
    if (obj == null) {
      return null;
    }
    if (obj instanceof String str) {
      return str;
    }
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      log.warn("Could not serialize audit object: {}", e.getMessage());
      return "{\"error\":\"Serialization failed\"}";
    }
  }

  private JsonNode parseJson(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      return null;
    }
  }
}
