package com.khmermart.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.Notification;
import com.khmermart.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TransactionRollbackEventTest.RollbackTestService.class)
class TransactionRollbackEventTest extends AbstractIntegrationTest {

  @Autowired private RollbackTestService rollbackTestService;
  @Autowired private NotificationRepository notificationRepository;

  @TestComponent
  public static class RollbackTestService {
    @Autowired private ApplicationEventPublisher eventPublisher;

    @Transactional
    public void executeAndFail(Long orderId, String orderNumber, Long userId, String email) {
      // Publish event inside transaction
      eventPublisher.publishEvent(
          new OrderPlacedEvent(
              orderId,
              orderNumber,
              userId,
              email,
              "Rollback Customer",
              "+85512345678",
              5000L,
              4850L,
              150L,
              null));

      // Simulate failure causing rollback
      throw new RuntimeException("Simulated transaction failure to trigger rollback");
    }
  }

  @Test
  @DisplayName(
      "5. Transaction rollback test: proof that @TransactionalEventListener(phase=AFTER_COMMIT) does not fire when TX rolls back")
  void testNoEventOnTransactionRollback() {
    String testOrderNum = "KM-ROLLBACK-" + UUID.randomUUID().toString().substring(0, 8);
    Long testUserId = 99999L;

    long beforeCount = notificationRepository.count();

    try {
      rollbackTestService.executeAndFail(
          99999L, testOrderNum, testUserId, "rollback@khmermart.local");
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).contains("Simulated transaction failure");
    }

    // Wait a brief moment to ensure no async listener executed
    try {
      Thread.sleep(300);
    } catch (InterruptedException ignored) {
    }

    // Verify NO Notification record was saved for this rolled-back order
    List<Notification> matchingNotifs =
        notificationRepository.findAll().stream()
            .filter(n -> n.getBody() != null && n.getBody().contains(testOrderNum))
            .toList();

    assertThat(matchingNotifs).isEmpty();
  }
}
