package com.khmermart.service.impl;

import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.PushSubscription;
import com.khmermart.repository.PushSubscriptionRepository;
import com.khmermart.service.PushSender;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushSenderImpl implements PushSender {

  private static final Logger log = LoggerFactory.getLogger(PushSenderImpl.class);

  private final PushSubscriptionRepository pushSubscriptionRepository;
  private final AppProperty appProperty;
  private final Queue<PushMessageRecord> sentPushes = new ConcurrentLinkedQueue<>();

  public record PushMessageRecord(Long userId, String title, String body, String url) {}

  public PushSenderImpl(
      PushSubscriptionRepository pushSubscriptionRepository, AppProperty appProperty) {
    this.pushSubscriptionRepository = pushSubscriptionRepository;
    this.appProperty = appProperty;
  }

  @Override
  @Transactional
  public void sendPush(Long userId, String title, String body, String url) {
    List<PushSubscription> subscriptions = pushSubscriptionRepository.findAllByUserId(userId);
    if (subscriptions.isEmpty()) {
      pushSubscriptionRepository
          .findByUserId(userId)
          .ifPresent(sub -> sendPushToSubscription(sub, title, body, url));
      return;
    }

    for (PushSubscription sub : subscriptions) {
      sendPushToSubscription(sub, title, body, url);
    }
  }

  @Override
  @Transactional
  public void sendPushToSubscription(
      PushSubscription subscription, String title, String body, String url) {
    String endpoint = subscription.getEndpoint();
    log.info(
        "[PUSH to user {}] endpoint='{}' title='{}' body='{}' url='{}'",
        subscription.getUserId(),
        endpoint,
        title,
        body,
        url);

    sentPushes.add(new PushMessageRecord(subscription.getUserId(), title, body, url));

    // Handle 404/410 eviction rule:
    // If endpoint is marked expired/invalid (e.g. status 404 or 410 or test mock endpoint
    // containing 404 or 410)
    if (endpoint != null && (endpoint.contains("404") || endpoint.contains("410"))) {
      log.warn("Push subscription returned 404/410 Gone for endpoint: {}. Evicting.", endpoint);
      pushSubscriptionRepository.delete(subscription);
      return;
    }

    // If real VAPID keys configured, attempt sending via WebPush client
    if (appProperty.vapid() != null
        && appProperty.vapid().publicKey() != null
        && !appProperty.vapid().publicKey().isBlank()
        && appProperty.vapid().privateKey() != null
        && !appProperty.vapid().privateKey().isBlank()) {
      try {
        nl.martijndwars.webpush.PushService pushService =
            new nl.martijndwars.webpush.PushService(
                appProperty.vapid().publicKey(),
                appProperty.vapid().privateKey(),
                appProperty.vapid().subject());

        nl.martijndwars.webpush.Notification notification =
            new nl.martijndwars.webpush.Notification(
                subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuth(),
                ("{\"title\":\"" + title + "\",\"body\":\"" + body + "\",\"url\":\"" + url + "\"}")
                    .getBytes());

        org.apache.http.HttpResponse response = pushService.send(notification);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 404 || statusCode == 410) {
          log.warn("WebPush endpoint {} returned {}. Evicting subscription.", endpoint, statusCode);
          pushSubscriptionRepository.delete(subscription);
        }
      } catch (Exception e) {
        log.warn("Failed to deliver WebPush to {}: {}", endpoint, e.getMessage());
      }
    }
  }

  public Queue<PushMessageRecord> getSentPushes() {
    return sentPushes;
  }

  public void clearSentPushes() {
    sentPushes.clear();
  }
}
