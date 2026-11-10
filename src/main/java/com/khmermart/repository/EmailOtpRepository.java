package com.khmermart.repository;

import com.khmermart.model.entity.EmailOtp;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EmailOtp> findByUserId(Long userId);
}
