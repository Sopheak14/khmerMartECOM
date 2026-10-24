package com.khmermart.service;

import com.khmermart.model.response.AuditLogDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {

  void log(
      Long actorId,
      String action,
      String entity,
      String entityId,
      Object before,
      Object after,
      String ip);

  Page<AuditLogDto> getAuditLogs(Long actorId, String entity, Pageable pageable);
}
