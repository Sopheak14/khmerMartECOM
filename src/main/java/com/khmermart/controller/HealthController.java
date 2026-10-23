package com.khmermart.controller;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.config.AppProperty;
import com.khmermart.model.response.HealthDto;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@Tag(name = "Health", description = "System health check")
public class HealthController {

  private final DataSource dataSource;
  private final RedisConnectionFactory redisConnectionFactory;
  private final AppProperty appProperties;

  public HealthController(
      DataSource dataSource,
      RedisConnectionFactory redisConnectionFactory,
      AppProperty appProperties) {
    this.dataSource = dataSource;
    this.redisConnectionFactory = redisConnectionFactory;
    this.appProperties = appProperties;
  }

  @GetMapping
  @Operation(summary = "Health check endpoint")
  public SuccessResponse<HealthDto> checkHealth() {
    String dbStatus = checkDb();
    String redisStatus = checkRedis();
    String overallStatus = ("ok".equals(dbStatus) && "ok".equals(redisStatus)) ? "ok" : "down";
    String khqrMode = appProperties.khqr().mode();

    return ApiResponseUtil.success(new HealthDto(overallStatus, dbStatus, redisStatus, khqrMode));
  }

  private String checkDb() {
    try (var conn = dataSource.getConnection()) {
      return conn.isValid(2) ? "ok" : "down";
    } catch (Exception e) {
      return "down";
    }
  }

  private String checkRedis() {
    try (RedisConnection conn = redisConnectionFactory.getConnection()) {
      return "PONG".equalsIgnoreCase(Objects.toString(conn.ping(), "")) ? "ok" : "down";
    } catch (Exception e) {
      return "down";
    }
  }
}
