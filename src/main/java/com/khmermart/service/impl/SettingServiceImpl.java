package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.Setting;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.model.request.AdminSettingsUpdateRequest;
import com.khmermart.model.response.AdminSettingsDto;
import com.khmermart.model.response.PublicSettingsDto;
import com.khmermart.model.response.ShippingMethodDto;
import com.khmermart.repository.PromotionRepository;
import com.khmermart.repository.SettingRepository;
import com.khmermart.repository.ShippingMethodRepository;
import com.khmermart.service.AuditLogService;
import com.khmermart.service.SettingService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SettingServiceImpl implements SettingService {

  private final SettingRepository settingRepository;
  private final PromotionRepository promotionRepository;
  private final ShippingMethodRepository shippingMethodRepository;
  private final AppProperty appProperties;
  private final AuditLogService auditLogService;

  public SettingServiceImpl(
      SettingRepository settingRepository,
      PromotionRepository promotionRepository,
      ShippingMethodRepository shippingMethodRepository,
      AppProperty appProperties,
      AuditLogService auditLogService) {
    this.settingRepository = settingRepository;
    this.promotionRepository = promotionRepository;
    this.shippingMethodRepository = shippingMethodRepository;
    this.appProperties = appProperties;
    this.auditLogService = auditLogService;
  }

  @Override
  @Cacheable(value = "settings", key = "'public'")
  public PublicSettingsDto getPublicSettings() {
    String storeName = getSettingValue("STORE_NAME", "KhmerMart");
    long khrRate =
        Long.parseLong(
            getSettingValue("USD_KHR_RATE", String.valueOf(appProperties.khqr().usdKhrRate())));
    String khqrMode = getSettingValue("KHQR_MODE", appProperties.khqr().mode());
    String defaultCurrency = getSettingValue("DEFAULT_CURRENCY", "USD");
    long freeShippingOverCents =
        Long.parseLong(getSettingValue("FREE_SHIPPING_OVER_CENTS", "5000"));

    Instant now = Instant.now();

    // Check seasonal banner
    PublicSettingsDto.SeasonalBannerDto seasonalBanner = null;
    Optional<Promotion> seasonalOpt =
        promotionRepository.findActiveByType(PromotionType.SEASONAL, now).stream().findFirst();
    if (seasonalOpt.isPresent()) {
      Promotion s = seasonalOpt.get();
      seasonalBanner =
          new PublicSettingsDto.SeasonalBannerDto(
              s.getName(), s.getNameKm(), s.getBannerImage(), s.getLink());
    }

    // Check flash sale
    PublicSettingsDto.FlashSaleInfoDto flashSale = null;
    Optional<Promotion> flashOpt =
        promotionRepository.findActiveByType(PromotionType.FLASH_SALE, now).stream().findFirst();
    if (flashOpt.isPresent()) {
      Promotion f = flashOpt.get();
      flashSale = new PublicSettingsDto.FlashSaleInfoDto(f.getId(), f.getName(), f.getEndsAt());
    }

    return new PublicSettingsDto(
        storeName,
        khrRate,
        khqrMode,
        defaultCurrency,
        freeShippingOverCents,
        seasonalBanner,
        flashSale);
  }

  @Override
  public String getSettingValue(String key, String defaultValue) {
    return settingRepository.findById(key).map(Setting::getValue).orElse(defaultValue);
  }

  @Override
  public String getKhqrMode() {
    String mode = getSettingValue("KHQR_MODE", appProperties.khqr().mode());
    return mode == null || mode.isBlank() ? "MOCK" : mode.trim().toUpperCase();
  }

  @Override
  public AdminSettingsDto getAdminSettings() {
    String storeName = getSettingValue("STORE_NAME", "KhmerMart");
    long khrRate =
        Long.parseLong(
            getSettingValue("USD_KHR_RATE", String.valueOf(appProperties.khqr().usdKhrRate())));
    String khqrMode = getSettingValue("KHQR_MODE", appProperties.khqr().mode());

    List<ShippingMethodDto> shippingMethods =
        shippingMethodRepository.findAllByOrderByFeeCentsAsc().stream()
            .map(
                sm ->
                    new ShippingMethodDto(
                        sm.getCode(),
                        sm.getName(),
                        sm.getNameKm(),
                        sm.getFeeCents(),
                        sm.getFreeOverCents(),
                        sm.getEtaDaysMin(),
                        sm.getEtaDaysMax(),
                        sm.isPhnomPenhOnly()))
            .toList();

    return new AdminSettingsDto(storeName, khrRate, khqrMode, shippingMethods);
  }

  @Override
  @Transactional
  @CacheEvict(value = "settings", allEntries = true)
  public AdminSettingsDto updateAdminSettings(
      AdminSettingsUpdateRequest request, Long adminId, String ip) {
    AdminSettingsDto beforeState = getAdminSettings();

    if (request.khqrMode() != null && "LIVE".equalsIgnoreCase(request.khqrMode())) {
      String envToken = appProperties.khqr() != null ? appProperties.khqr().token() : null;
      String dbToken = getSettingValue("BAKONG_TOKEN", null);
      if ((envToken == null || envToken.isBlank()) && (dbToken == null || dbToken.isBlank())) {
        throw new CustomException(
            ErrorCode.VALIDATION_ERROR,
            "Switching to LIVE mode requires BAKONG_TOKEN to be configured",
            HttpStatus.UNPROCESSABLE_ENTITY);
      }
    }

    if (request.storeName() != null && !request.storeName().isBlank()) {
      settingRepository.save(new Setting("STORE_NAME", request.storeName().trim()));
    }
    if (request.khrRate() != null && request.khrRate() > 0) {
      settingRepository.save(new Setting("USD_KHR_RATE", String.valueOf(request.khrRate())));
    }
    if (request.khqrMode() != null && !request.khqrMode().isBlank()) {
      settingRepository.save(new Setting("KHQR_MODE", request.khqrMode().trim().toUpperCase()));
    }

    AdminSettingsDto afterState = getAdminSettings();

    auditLogService.log(
        adminId, "UPDATE_SETTINGS", "SETTINGS", "GLOBAL", beforeState, afterState, ip);

    return afterState;
  }
}
