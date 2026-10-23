package com.khmermart.modules.health;

import com.khmermart.common.api.ApiResponse;
import com.khmermart.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@Tag(name = "Health", description = "System health check endpoint")
public class HealthController {

  private final DataSource dataSource;
  private final RedisConnectionFactory redisConnectionFactory;
  private final AppProperties appProperties;

  public HealthController(
      DataSource dataSource,
      RedisConnectionFactory redisConnectionFactory,
      AppProperties appProperties) {
    this.dataSource = dataSource;
    this.redisConnectionFactory = redisConnectionFactory;
    this.appProperties = appProperties;
  }

  @GetMapping
  @Operation(summary = "System health status")
  public ApiResponse<HealthResponse> checkHealth() {
    String dbStatus = checkDb();
    String redisStatus = checkRedis();
    String overallStatus = ("ok".equals(dbStatus) && "ok".equals(redisStatus)) ? "ok" : "degraded";

    return ApiResponse.ok(
        new HealthResponse(overallStatus, dbStatus, redisStatus, appProperties.khqr().mode()));
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
      return "PONG".equalsIgnoreCase(conn.ping()) ? "ok" : "down";
    } catch (Exception e) {
      return "down";
    }
  }
}
