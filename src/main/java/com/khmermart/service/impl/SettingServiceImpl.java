package com.khmermart.service.impl;

import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.Setting;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.model.response.PublicSettingsDto;
import com.khmermart.repository.PromotionRepository;
import com.khmermart.repository.SettingRepository;
import com.khmermart.service.SettingService;
import java.time.Instant;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SettingServiceImpl implements SettingService {

  private final SettingRepository settingRepository;
  private final PromotionRepository promotionRepository;
  private final AppProperty appProperties;

  public SettingServiceImpl(
      SettingRepository settingRepository,
      PromotionRepository promotionRepository,
      AppProperty appProperties) {
    this.settingRepository = settingRepository;
    this.promotionRepository = promotionRepository;
    this.appProperties = appProperties;
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
        promotionRepository.findFirstActiveByType(PromotionType.SEASONAL, now);
    if (seasonalOpt.isPresent()) {
      Promotion s = seasonalOpt.get();
      seasonalBanner =
          new PublicSettingsDto.SeasonalBannerDto(
              s.getName(), s.getNameKm(), s.getBannerImage(), s.getLink());
    }

    // Check flash sale
    PublicSettingsDto.FlashSaleInfoDto flashSale = null;
    Optional<Promotion> flashOpt =
        promotionRepository.findFirstActiveByType(PromotionType.FLASH_SALE, now);
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
}
