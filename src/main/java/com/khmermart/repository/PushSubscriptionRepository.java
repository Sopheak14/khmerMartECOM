package com.khmermart.repository;

import com.khmermart.model.entity.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
  Optional<PushSubscription> findByEndpoint(String endpoint);

  Optional<PushSubscription> findByUserId(Long userId);

  List<PushSubscription> findAllByUserId(Long userId);

  void deleteByEndpoint(String endpoint);
}
