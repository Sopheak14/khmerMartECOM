package com.khmermart.modules.home.dto;

import com.khmermart.modules.category.dto.CategoryDTO;
import com.khmermart.modules.product.dto.ProductCardDTO;
import com.khmermart.modules.setting.dto.PublicSettingsDTO;
import java.time.Instant;
import java.util.List;

public record HomeDTO(
    PublicSettingsDTO.SeasonalBannerDTO seasonalBanner,
    FlashSaleDTO flashSale,
    List<CategoryDTO> categories,
    List<ProductCardDTO> bestSellers,
    List<ProductCardDTO> newArrivals) {

  public record FlashSaleDTO(String name, Instant endsAt, List<ProductCardDTO> products) {}
}
