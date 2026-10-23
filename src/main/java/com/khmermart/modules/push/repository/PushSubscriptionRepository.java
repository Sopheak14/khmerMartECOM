package com.khmermart.modules.push.repository;

import com.khmermart.modules.push.entity.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
  List<PushSubscription> findByUserId(Long userId);

  Optional<PushSubscription> findByEndpoint(String endpoint);

  void deleteByEndpoint(String endpoint);
}
