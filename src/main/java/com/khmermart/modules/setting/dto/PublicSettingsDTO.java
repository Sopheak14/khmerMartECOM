package com.khmermart.modules.setting.dto;

import java.time.Instant;

public record PublicSettingsDTO(
    String storeName,
    long khrRate,
    String khqrMode,
    String defaultCurrency,
    long freeShippingOverCents,
    SeasonalBannerDTO seasonalBanner,
    FlashSaleInfoDTO flashSale) {

  public record SeasonalBannerDTO(
      String title, String titleKm, String imageUrl, String link) {}

  public record FlashSaleInfoDTO(Long id, String name, Instant endsAt) {}
}
