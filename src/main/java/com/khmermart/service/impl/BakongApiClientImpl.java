package com.khmermart.service.impl;

import com.khmermart.config.AppProperty;
import com.khmermart.model.request.BakongCheckRequest;
import com.khmermart.model.response.BakongCheckResponse;
import com.khmermart.service.BakongApiClient;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class BakongApiClientImpl implements BakongApiClient {

  private static final Logger log = LoggerFactory.getLogger(BakongApiClientImpl.class);

  private final RestClient restClient;
  private final AppProperty appProperty;
  private final AtomicReference<Instant> lastTokenAlertTime = new AtomicReference<>(Instant.EPOCH);

  @org.springframework.beans.factory.annotation.Autowired
  public BakongApiClientImpl(AppProperty appProperty, RestClient.Builder restClientBuilder) {
    this.appProperty = appProperty;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(8));

    this.restClient =
        restClientBuilder.baseUrl(appProperty.khqr().apiBase()).requestFactory(factory).build();
  }

  public BakongApiClientImpl(AppProperty appProperty, RestClient restClient) {
    this.appProperty = appProperty;
    this.restClient = restClient;
  }

  @Override
  public BakongCheckResponse checkTransactionByMd5(String md5) {
    try {
      String token = appProperty.khqr().token();
      return restClient
          .post()
          .uri("/v1/check_transaction_by_md5")
          .contentType(MediaType.APPLICATION_JSON)
          .headers(
              headers -> {
                if (token != null && !token.isBlank()) {
                  headers.setBearerAuth(token);
                }
              })
          .body(new BakongCheckRequest(md5))
          .retrieve()
          .onStatus(
              HttpStatusCode::is4xxClientError,
              (req, res) -> {
                if (res.getStatusCode().value() == 401) {
                  triggerTokenAlertOncePerHour();
                  log.error("Bakong API returned 401 Unauthorized - Bakong token expired");
                } else if (res.getStatusCode().value() == 403) {
                  log.error(
                      "Bakong API returned 403 Forbidden - Bakong API unreachable — run the backend on a machine/VPS located in Cambodia");
                } else {
                  log.warn("Bakong API returned client error {}", res.getStatusCode());
                }
              })
          .onStatus(
              HttpStatusCode::is5xxServerError,
              (req, res) -> {
                log.warn("Bakong API returned server error {}", res.getStatusCode());
              })
          .body(BakongCheckResponse.class);
    } catch (HttpClientErrorException.Unauthorized e) {
      triggerTokenAlertOncePerHour();
      log.error("Bakong API 401 Unauthorized - Bakong token expired");
      return null;
    } catch (HttpClientErrorException.Forbidden e) {
      log.error(
          "Bakong API unreachable — run the backend on a machine/VPS located in Cambodia: {}",
          e.getMessage());
      return null;
    } catch (HttpClientErrorException | HttpServerErrorException e) {
      log.warn("Bakong API error: {}", e.getMessage());
      return null;
    } catch (ResourceAccessException e) {
      log.warn("Bakong API network timeout/connection error: {}", e.getMessage());
      return null;
    } catch (Exception e) {
      log.error("Unexpected error contacting Bakong API: {}", e.getMessage(), e);
      return null;
    }
  }

  private void triggerTokenAlertOncePerHour() {
    Instant now = Instant.now();
    Instant last = lastTokenAlertTime.get();
    if (Duration.between(last, now).toHours() >= 1) {
      if (lastTokenAlertTime.compareAndSet(last, now)) {
        log.error(
            "ALERT [ADMIN]: Bakong token expired! Please renew token via POST /v1/renew_token.");
      }
    }
  }
}
