package com.khmermart.modules.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "actor_id")
  private Long actorId;

  @Column(nullable = false, length = 100)
  private String action;

  @Column(nullable = false, length = 100)
  private String entity;

  @Column(name = "entity_id", length = 100)
  private String entityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_json", columnDefinition = "jsonb")
  private Object beforeJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_json", columnDefinition = "jsonb")
  private Object afterJson;

  @Column(length = 50)
  private String ip;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
}
