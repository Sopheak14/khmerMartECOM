package com.khmermart.repository;

import com.khmermart.model.entity.Notification;
import com.khmermart.model.enumerate.NotificationChannel;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
  Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  Page<Notification> findByUserIdAndChannelOrderByCreatedAtDesc(
      Long userId, NotificationChannel channel, Pageable pageable);

  long countByUserIdAndReadAtIsNull(Long userId);

  @Modifying
  @Query(
      "UPDATE Notification n SET n.readAt = :readAt WHERE n.userId = :userId AND n.readAt IS NULL")
  int markAllAsRead(@Param("userId") Long userId, @Param("readAt") Instant readAt);
}
