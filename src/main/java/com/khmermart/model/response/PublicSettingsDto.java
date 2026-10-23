package com.khmermart.model.response;

import java.time.Instant;

public record PublicSettingsDto(
    String storeName,
    long khrRate,
    String khqrMode,
    String defaultCurrency,
    long freeShippingOverCents,
    SeasonalBannerDto seasonalBanner,
    FlashSaleInfoDto flashSale) {

  public record SeasonalBannerDto(String title, String titleKm, String imageUrl, String link) {}

  public record FlashSaleInfoDto(Long id, String name, Instant endsAt) {}
}
