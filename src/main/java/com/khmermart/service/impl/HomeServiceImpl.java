package com.khmermart.service.impl;

import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.PromotionProduct;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.model.response.CategoryDto;
import com.khmermart.model.response.HomeDto;
import com.khmermart.model.response.ProductCardDto;
import com.khmermart.model.response.PublicSettingsDto;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.PromotionProductRepository;
import com.khmermart.repository.PromotionRepository;
import com.khmermart.service.CategoryService;
import com.khmermart.service.HomeService;
import com.khmermart.service.ProductService;
import com.khmermart.service.SettingService;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HomeServiceImpl implements HomeService {

  private final SettingService settingService;
  private final PromotionRepository promotionRepository;
  private final PromotionProductRepository promotionProductRepository;
  private final ProductRepository productRepository;
  private final CategoryService categoryService;
  private final ProductService productService;

  public HomeServiceImpl(
      SettingService settingService,
      PromotionRepository promotionRepository,
      PromotionProductRepository promotionProductRepository,
      ProductRepository productRepository,
      CategoryService categoryService,
      ProductService productService) {
    this.settingService = settingService;
    this.promotionRepository = promotionRepository;
    this.promotionProductRepository = promotionProductRepository;
    this.productRepository = productRepository;
    this.categoryService = categoryService;
    this.productService = productService;
  }

  @Override
  @Cacheable(value = "home", key = "'data'")
  public HomeDto getHomeData() {
    PublicSettingsDto publicSettings = settingService.getPublicSettings();
    PublicSettingsDto.SeasonalBannerDto seasonalBanner = publicSettings.seasonalBanner();

    Instant now = Instant.now();
    HomeDto.FlashSaleDto flashSale = null;
    Optional<Promotion> flashPromoOpt =
        promotionRepository.findFirstActiveByType(PromotionType.FLASH_SALE, now);
    if (flashPromoOpt.isPresent()) {
      Promotion promo = flashPromoOpt.get();
      List<PromotionProduct> promoProducts =
          promotionProductRepository.findByPromotionId(promo.getId());
      List<Long> productIds =
          promoProducts.stream().map(PromotionProduct::getProductId).distinct().toList();
      List<ProductCardDto> products =
          productIds.isEmpty()
              ? Collections.emptyList()
              : productService.getFlashSaleCards(productIds);
      flashSale =
          new HomeDto.FlashSaleDto(promo.getId(), promo.getName(), promo.getEndsAt(), products);
    }

    List<CategoryDto> categories = categoryService.getCategoryTree();
    List<ProductCardDto> bestSellers = productService.getBestSellers(8);
    List<ProductCardDto> newArrivals = productService.getNewArrivals(8);

    return new HomeDto(seasonalBanner, flashSale, categories, bestSellers, newArrivals);
  }
}
