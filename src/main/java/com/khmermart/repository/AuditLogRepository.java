package com.khmermart.repository;

import com.khmermart.model.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository
    extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
  Page<AuditLog> findByActorId(Long actorId, Pageable pageable);

  Page<AuditLog> findByEntity(String entity, Pageable pageable);

  Page<AuditLog> findByActorIdAndEntity(Long actorId, String entity, Pageable pageable);
}
