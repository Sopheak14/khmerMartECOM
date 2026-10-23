package com.khmermart.service;

import com.khmermart.event.PaymentStatusChangedEvent;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.response.PaymentStatusDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class PaymentSseRegistry {

  private static final Logger log = LoggerFactory.getLogger(PaymentSseRegistry.class);
  private static final long TIMEOUT_MS = 15 * 60 * 1000L; // 15 minutes

  private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

  public SseEmitter register(Long paymentId, PaymentStatusDto initialStatus) {
    SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
    emitters.computeIfAbsent(paymentId, k -> new CopyOnWriteArrayList<>()).add(emitter);

    Runnable cleanup =
        () -> {
          List<SseEmitter> list = emitters.get(paymentId);
          if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
              emitters.remove(paymentId);
            }
          }
        };

    emitter.onCompletion(cleanup);
    emitter.onTimeout(
        () -> {
          emitter.complete();
          cleanup.run();
        });
    emitter.onError(e -> cleanup.run());

    // Send initial status event
    try {
      if (initialStatus != null) {
        emitter.send(SseEmitter.event().name("status").data(initialStatus));
        if (isTerminal(initialStatus.status())) {
          emitter.complete();
        }
      }
    } catch (IOException e) {
      log.warn("Failed to send initial SSE events for payment {}", paymentId, e);
      emitter.completeWithError(e);
    }

    return emitter;
  }

  @EventListener
  public void onPaymentStatusChanged(PaymentStatusChangedEvent event) {
    List<SseEmitter> list = emitters.get(event.paymentId());
    if (list == null || list.isEmpty()) {
      return;
    }

    PaymentStatusDto dto =
        new PaymentStatusDto(
            event.paymentId(),
            event.status(),
            event.orderStatus(),
            event.paidAt(),
            event.bakongHash(),
            event.expiresAt());

    List<SseEmitter> deadEmitters = new ArrayList<>();
    for (SseEmitter emitter : list) {
      try {
        emitter.send(SseEmitter.event().name("status").data(dto));
        if (isTerminal(event.status())) {
          emitter.complete();
          deadEmitters.add(emitter);
        }
      } catch (Exception e) {
        deadEmitters.add(emitter);
      }
    }

    list.removeAll(deadEmitters);
    if (list.isEmpty()) {
      emitters.remove(event.paymentId());
    }
  }

  @Scheduled(fixedRate = 15000)
  public void pingAll() {
    for (Map.Entry<Long, List<SseEmitter>> entry : emitters.entrySet()) {
      List<SseEmitter> dead = new ArrayList<>();
      for (SseEmitter emitter : entry.getValue()) {
        try {
          emitter.send(SseEmitter.event().name("ping").data("ping"));
        } catch (Exception e) {
          dead.add(emitter);
        }
      }
      entry.getValue().removeAll(dead);
    }
  }

  private boolean isTerminal(PaymentStatus status) {
    return status == PaymentStatus.PAID
        || status == PaymentStatus.EXPIRED
        || status == PaymentStatus.FAILED;
  }
}
