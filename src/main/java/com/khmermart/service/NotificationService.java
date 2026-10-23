package com.khmermart.service;

import com.khmermart.model.entity.Notification;
import com.khmermart.model.enumerate.NotificationChannel;
import com.khmermart.model.response.NotificationDto;
import com.khmermart.model.response.UnreadCountDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
  Notification createNotification(
      Long userId,
      NotificationChannel channel,
      String type,
      String title,
      String body,
      String link,
      String payload);

  Page<NotificationDto> getNotifications(Long userId, Pageable pageable);

  UnreadCountDto getUnreadCount(Long userId);

  NotificationDto markAsRead(Long notificationId, Long userId);

  void markAllAsRead(Long userId);
}
