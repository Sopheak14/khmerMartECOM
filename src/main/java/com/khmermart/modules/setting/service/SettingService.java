package com.khmermart.modules.setting.service;

import com.khmermart.config.AppProperties;
import com.khmermart.modules.promotion.entity.Promotion;
import com.khmermart.modules.promotion.model.PromotionType;
import com.khmermart.modules.promotion.repository.PromotionRepository;
import com.khmermart.modules.setting.dto.PublicSettingsDTO;
import com.khmermart.modules.setting.entity.Setting;
import com.khmermart.modules.setting.repository.SettingRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SettingService {

  private final SettingRepository settingRepository;
  private final PromotionRepository promotionRepository;
  private final AppProperties appProperties;

  public SettingService(
      SettingRepository settingRepository,
      PromotionRepository promotionRepository,
      AppProperties appProperties) {
    this.settingRepository = settingRepository;
    this.promotionRepository = promotionRepository;
    this.appProperties = appProperties;
  }

  @Cacheable(value = "settings", key = "'public'")
  public PublicSettingsDTO getPublicSettings() {
    String storeName = getSettingValue("STORE_NAME", "KhmerMart");
    long khrRate = Long.parseLong(getSettingValue("USD_KHR_RATE", String.valueOf(appProperties.khqr().usdKhrRate())));
    String khqrMode = getSettingValue("KHQR_MODE", appProperties.khqr().mode());
    String defaultCurrency = getSettingValue("DEFAULT_CURRENCY", "USD");
    long freeShippingOverCents = Long.parseLong(getSettingValue("FREE_SHIPPING_OVER_CENTS", "5000"));

    Instant now = Instant.now();

    // Check seasonal banner
    PublicSettingsDTO.SeasonalBannerDTO seasonalBanner = null;
    Optional<Promotion> seasonalOpt =
        promotionRepository.findFirstActiveByType(PromotionType.SEASONAL, now);
    if (seasonalOpt.isPresent()) {
      Promotion s = seasonalOpt.get();
      seasonalBanner =
          new PublicSettingsDTO.SeasonalBannerDTO(
              s.getName(), s.getNameKm(), s.getBannerImage(), s.getLink());
    }

    // Check flash sale
    PublicSettingsDTO.FlashSaleInfoDTO flashSale = null;
    Optional<Promotion> flashOpt =
        promotionRepository.findFirstActiveByType(PromotionType.FLASH_SALE, now);
    if (flashOpt.isPresent()) {
      Promotion f = flashOpt.get();
      flashSale = new PublicSettingsDTO.FlashSaleInfoDTO(f.getId(), f.getName(), f.getEndsAt());
    }

    return new PublicSettingsDTO(
        storeName,
        khrRate,
        khqrMode,
        defaultCurrency,
        freeShippingOverCents,
        seasonalBanner,
        flashSale);
  }

  public String getSettingValue(String key, String defaultValue) {
    return settingRepository.findById(key).map(Setting::getValue).orElse(defaultValue);
  }
}
