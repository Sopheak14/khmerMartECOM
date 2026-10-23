package com.khmermart.modules.audit.repository;

import com.khmermart.modules.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository
    extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
  Page<AuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId, Pageable pageable);

  Page<AuditLog> findByEntityOrderByCreatedAtDesc(String entity, Pageable pageable);
}
