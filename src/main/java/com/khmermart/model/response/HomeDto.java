package com.khmermart.model.response;

import java.time.Instant;
import java.util.List;

public record HomeDto(
    PublicSettingsDto.SeasonalBannerDto seasonalBanner,
    FlashSaleDto flashSale,
    List<CategoryDto> categories,
    List<ProductCardDto> bestSellers,
    List<ProductCardDto> newArrivals) {

  public record FlashSaleDto(Long id, String name, Instant endsAt, List<ProductCardDto> products) {}
}
