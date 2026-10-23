package com.khmermart.service;

import com.khmermart.model.entity.PushSubscription;

public interface PushSender {
  void sendPush(Long userId, String title, String body, String url);

  void sendPushToSubscription(PushSubscription subscription, String title, String body, String url);
}
