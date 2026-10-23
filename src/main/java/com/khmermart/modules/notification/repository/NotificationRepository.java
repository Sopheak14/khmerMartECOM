package com.khmermart.modules.notification.repository;

import com.khmermart.modules.notification.entity.Notification;
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

  long countByUserIdAndReadAtIsNull(Long userId);

  @Modifying
  @Query(
      value =
          "UPDATE notifications SET read_at = now() WHERE user_id = :userId AND read_at IS NULL",
      nativeQuery = true)
  int markAllAsRead(@Param("userId") Long userId);
}
