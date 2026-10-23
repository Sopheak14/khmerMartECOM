package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.model.entity.Notification;
import com.khmermart.model.enumerate.NotificationChannel;
import com.khmermart.model.response.NotificationDto;
import com.khmermart.model.response.UnreadCountDto;
import com.khmermart.repository.NotificationRepository;
import com.khmermart.service.NotificationService;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

  private final NotificationRepository notificationRepository;

  public NotificationServiceImpl(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  @Override
  @Transactional
  public Notification createNotification(
      Long userId,
      NotificationChannel channel,
      String type,
      String title,
      String body,
      String link,
      String payload) {
    Instant now = Instant.now();
    Notification notification =
        Notification.builder()
            .userId(userId)
            .channel(channel)
            .type(type)
            .title(title)
            .body(body)
            .link(link)
            .payload(payload)
            .status(channel == NotificationChannel.IN_APP ? "DELIVERED" : "SENT")
            .sentAt(now)
            .createdAt(now)
            .build();
    return notificationRepository.save(notification);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<NotificationDto> getNotifications(Long userId, Pageable pageable) {
    return notificationRepository
        .findByUserIdOrderByCreatedAtDesc(userId, pageable)
        .map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public UnreadCountDto getUnreadCount(Long userId) {
    long count = notificationRepository.countByUserIdAndReadAtIsNull(userId);
    return new UnreadCountDto(count);
  }

  @Override
  @Transactional
  public NotificationDto markAsRead(Long notificationId, Long userId) {
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Notification not found"));

    if (!notification.getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN, "Access denied to notification");
    }

    if (notification.getReadAt() == null) {
      notification.setReadAt(Instant.now());
      notification = notificationRepository.save(notification);
    }
    return toDto(notification);
  }

  @Override
  @Transactional
  public void markAllAsRead(Long userId) {
    notificationRepository.markAllAsRead(userId, Instant.now());
  }

  private NotificationDto toDto(Notification n) {
    return new NotificationDto(
        n.getId(),
        n.getType(),
        n.getTitle(),
        n.getBody(),
        n.getLink(),
        n.getReadAt(),
        n.getCreatedAt());
  }
}
