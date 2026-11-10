package com.khmermart.security;

import com.khmermart.common.constant.MessageConstant;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis fixed-window limiter for register / verify-email / resend-verification. */
@Component
public class OtpRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(OtpRateLimiter.class);

  private final StringRedisTemplate redis;
  private final int ipLimit;
  private final int emailLimit;
  private final Duration window;

  public OtpRateLimiter(
      StringRedisTemplate redis,
      @Value("${app.otp.ip-limit:30}") int ipLimit,
      @Value("${app.otp.email-limit:10}") int emailLimit,
      @Value("${app.otp.window-seconds:900}") long windowSeconds) {
    this.redis = redis;
    this.ipLimit = ipLimit;
    this.emailLimit = emailLimit;
    this.window = Duration.ofSeconds(windowSeconds);
  }

  public void check(String clientIp, String email) {
    hit("rl:otp:ip:" + clientIp, ipLimit);
    hit("rl:otp:email:" + (email == null ? "" : email.toLowerCase().trim()), emailLimit);
  }

  private void hit(String key, int limit) {
    Long count;
    try {
      count = redis.opsForValue().increment(key);
      if (count != null && count == 1L) {
        redis.expire(key, window);
      }
    } catch (Exception e) {
      // Redis outage must not lock everyone out of signing up.
      log.warn("OTP rate limiter unavailable ({}), allowing request", e.getClass().getSimpleName());
      return;
    }
    if (count != null && count > limit) {
      throw new CustomException(ErrorCode.RATE_LIMITED, MessageConstant.RATE_LIMITED);
    }
  }
}
