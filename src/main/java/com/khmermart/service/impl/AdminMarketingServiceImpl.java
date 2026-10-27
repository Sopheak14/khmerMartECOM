package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.model.entity.Coupon;
import com.khmermart.model.entity.Product;
import com.khmermart.model.entity.ProductVariant;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.PromotionProduct;
import com.khmermart.model.enumerate.CouponType;
import com.khmermart.model.request.AdminCouponRequest;
import com.khmermart.model.request.AdminPromotionRequest;
import com.khmermart.model.request.PromotionItemRequest;
import com.khmermart.model.response.AdminCouponDto;
import com.khmermart.model.response.AdminPromotionDto;
import com.khmermart.model.response.AdminPromotionItemDto;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.repository.CouponRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.ProductVariantRepository;
import com.khmermart.repository.PromotionProductRepository;
import com.khmermart.repository.PromotionRepository;
import com.khmermart.service.AdminMarketingService;
import com.khmermart.service.AuditLogService;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMarketingServiceImpl implements AdminMarketingService {

  private final CouponRepository couponRepository;
  private final PromotionRepository promotionRepository;
  private final PromotionProductRepository promotionProductRepository;
  private final ProductRepository productRepository;
  private final ProductVariantRepository productVariantRepository;
  private final AuditLogService auditLogService;

  public AdminMarketingServiceImpl(
      CouponRepository couponRepository,
      PromotionRepository promotionRepository,
      PromotionProductRepository promotionProductRepository,
      ProductRepository productRepository,
      ProductVariantRepository productVariantRepository,
      AuditLogService auditLogService) {
    this.couponRepository = couponRepository;
    this.promotionRepository = promotionRepository;
    this.promotionProductRepository = promotionProductRepository;
    this.productRepository = productRepository;
    this.productVariantRepository = productVariantRepository;
    this.auditLogService = auditLogService;
  }

  // --- COUPONS ---

  @Override
  @Transactional(readOnly = true)
  public List<AdminCouponDto> getCoupons() {
    return couponRepository.findAll().stream().map(this::toCouponDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public AdminCouponDto getCoupon(Long id) {
    Coupon coupon =
        couponRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Coupon not found"));
    return toCouponDto(coupon);
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"home", "catalog"},
      allEntries = true)
  public AdminCouponDto createCoupon(AdminCouponRequest request, Long adminId, String ip) {
    validateCouponDatesAndValues(
        request.type(), request.value(), request.startsAt(), request.endsAt());

    String code = request.code().trim().toUpperCase(Locale.ROOT);
    if (couponRepository.existsByCode(code)) {
      throw new CustomException(
          ErrorCode.CONFLICT, "Coupon with code '" + code + "' already exists");
    }

    Coupon coupon =
        Coupon.builder()
            .code(code)
            .type(request.type())
            .value(request.value())
            .description(request.description())
            .minOrderCents(request.minOrderCents())
            .maxDiscountCents(request.maxDiscountCents())
            .usageLimit(request.maxUses() != null ? request.maxUses() : 1000)
            .startsAt(request.startsAt() != null ? request.startsAt() : Instant.now())
            .endsAt(request.endsAt())
            .active(request.active() != null ? request.active() : true)
            .build();

    Coupon saved = couponRepository.save(coupon);

    auditLogService.log(
        adminId, "CREATE_COUPON", "COUPON", String.valueOf(saved.getId()), null, saved, ip);

    return toCouponDto(saved);
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"home", "catalog"},
      allEntries = true)
  public AdminCouponDto updateCoupon(Long id, AdminCouponRequest request, Long adminId, String ip) {
    Coupon coupon =
        couponRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Coupon not found"));

    validateCouponDatesAndValues(
        request.type(), request.value(), request.startsAt(), request.endsAt());

    Map<String, Object> beforeState =
        Map.of("code", coupon.getCode(), "type", coupon.getType(), "value", coupon.getValue());

    String code = request.code().trim().toUpperCase(Locale.ROOT);
    if (!code.equals(coupon.getCode()) && couponRepository.existsByCode(code)) {
      throw new CustomException(
          ErrorCode.CONFLICT, "Coupon with code '" + code + "' already exists");
    }

    coupon.setCode(code);
    coupon.setType(request.type());
    coupon.setValue(request.value());
    coupon.setDescription(request.description());
    coupon.setMinOrderCents(request.minOrderCents());
    coupon.setMaxDiscountCents(request.maxDiscountCents());
    if (request.maxUses() != null) {
      coupon.setUsageLimit(request.maxUses());
    }
    if (request.startsAt() != null) {
      coupon.setStartsAt(request.startsAt());
    }
    if (request.endsAt() != null) {
      coupon.setEndsAt(request.endsAt());
    }
    if (request.active() != null) {
      coupon.setActive(request.active());
    }

    Coupon updated = couponRepository.save(coupon);

    auditLogService.log(
        adminId,
        "UPDATE_COUPON",
        "COUPON",
        String.valueOf(updated.getId()),
        beforeState,
        updated,
        ip);

    return toCouponDto(updated);
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"home", "catalog"},
      allEntries = true)
  public void deleteCoupon(Long id, Long adminId, String ip) {
    Coupon coupon =
        couponRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Coupon not found"));

    couponRepository.delete(coupon);

    auditLogService.log(
        adminId,
        "DELETE_COUPON",
        "COUPON",
        String.valueOf(id),
        Map.of("id", id, "code", coupon.getCode()),
        Map.of("deleted", true),
        ip);
  }

  // --- PROMOTIONS ---

  @Override
  @Transactional(readOnly = true)
  public List<AdminPromotionDto> getPromotions() {
    return promotionRepository.findAll().stream().map(this::toPromotionDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public AdminPromotionDto getPromotion(Long id) {
    Promotion promo =
        promotionRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Promotion not found"));
    return toPromotionDto(promo);
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"home", "catalog"},
      allEntries = true)
  public AdminPromotionDto createPromotion(AdminPromotionRequest request, Long adminId, String ip) {
    validatePromotionDatesAndPercent(
        request.discountPercent(), request.startsAt(), request.endsAt());
    List<PromotionItemRequest> items = resolveItems(request);
    validatePromotionItems(request.type(), items);

    Promotion promo =
        Promotion.builder()
            .name(request.name().trim())
            .nameKm(request.nameKm())
            .type(request.type())
            .discountPercent(request.discountPercent())
            .thresholdCents(request.thresholdCents())
            .startsAt(request.startsAt())
            .endsAt(request.endsAt())
            .active(request.active() != null ? request.active() : true)
            .build();

    Promotion saved = promotionRepository.save(promo);

    if (items != null) {
      saveItems(saved.getId(), items);
    }

    auditLogService.log(
        adminId, "CREATE_PROMOTION", "PROMOTION", String.valueOf(saved.getId()), null, saved, ip);

    return toPromotionDto(saved);
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"home", "catalog"},
      allEntries = true)
  public AdminPromotionDto updatePromotion(
      Long id, AdminPromotionRequest request, Long adminId, String ip) {
    Promotion promo =
        promotionRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Promotion not found"));

    validatePromotionDatesAndPercent(
        request.discountPercent(), request.startsAt(), request.endsAt());
    List<PromotionItemRequest> items = resolveItems(request);
    validatePromotionItems(request.type(), items);

    Map<String, Object> beforeState =
        Map.of(
            "name",
            promo.getName(),
            "type",
            promo.getType(),
            "discountPercent",
            promo.getDiscountPercent() != null ? promo.getDiscountPercent() : 0);

    promo.setName(request.name().trim());
    promo.setNameKm(request.nameKm());
    promo.setType(request.type());
    promo.setDiscountPercent(request.discountPercent());
    promo.setThresholdCents(request.thresholdCents());
    promo.setStartsAt(request.startsAt());
    promo.setEndsAt(request.endsAt());
    if (request.active() != null) {
      promo.setActive(request.active());
    }

    Promotion updated = promotionRepository.save(promo);

    if (items != null) {
      promotionProductRepository.deleteByPromotionId(id);
      promotionProductRepository.flush();
      saveItems(updated.getId(), items);
    }

    auditLogService.log(
        adminId,
        "UPDATE_PROMOTION",
        "PROMOTION",
        String.valueOf(updated.getId()),
        beforeState,
        updated,
        ip);

    return toPromotionDto(updated);
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"home", "catalog"},
      allEntries = true)
  public void deletePromotion(Long id, Long adminId, String ip) {
    Promotion promo =
        promotionRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Promotion not found"));

    promotionProductRepository.deleteByPromotionId(id);
    promotionRepository.delete(promo);

    auditLogService.log(
        adminId,
        "DELETE_PROMOTION",
        "PROMOTION",
        String.valueOf(id),
        Map.of("id", id, "name", promo.getName()),
        Map.of("deleted", true),
        ip);
  }

  /** items wins over the deprecated productIds; null means the request does not touch products. */
  private List<PromotionItemRequest> resolveItems(AdminPromotionRequest request) {
    if (request.items() != null && !request.items().isEmpty()) {
      return request.items();
    }
    if (request.productIds() != null) {
      return request.productIds().stream()
          .map(pid -> new PromotionItemRequest(pid, null, null))
          .toList();
    }
    return request.items();
  }

  private void validatePromotionItems(PromotionType type, List<PromotionItemRequest> items) {
    if (items == null || items.isEmpty()) {
      return;
    }
    Map<String, String> errors = new LinkedHashMap<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < items.size(); i++) {
      PromotionItemRequest item = items.get(i);
      String path = "items[" + i + "]";
      if (item.productId() == null) {
        errors.put(path + ".productId", "productId is required");
        continue;
      }
      if (!seen.add(item.productId() + ":" + item.variantId())) {
        errors.put(path, "Duplicate product/variant in items");
        continue;
      }
      Product product = productRepository.findById(item.productId()).orElse(null);
      if (product == null) {
        errors.put(path + ".productId", "Product not found: " + item.productId());
        continue;
      }
      Long basePrice = product.getPriceCents();
      if (item.variantId() != null) {
        ProductVariant variant = productVariantRepository.findById(item.variantId()).orElse(null);
        if (variant == null || !variant.getProductId().equals(item.productId())) {
          errors.put(path + ".variantId", "Variant " + item.variantId() + " does not belong to product " + item.productId());
          continue;
        }
        if (variant.getPriceCents() != null) {
          basePrice = variant.getPriceCents();
        }
      } else if (type == PromotionType.FLASH_SALE
          && !productVariantRepository.findByProductIdAndActiveTrue(item.productId()).isEmpty()) {
        errors.put(path + ".variantId", "variantId is required: product "
                    + item.productId()
                    + " has variants, a flash sale cannot be product-wide");
        continue;
      }
      Long sale = item.salePriceCents();
      if (sale != null && (sale <= 0 || basePrice == null || sale >= basePrice)) {
        errors.put(path + ".salePriceCents", "salePriceCents must be greater than 0 and less than the price (" + basePrice + ")");
      }
    }
    if (!errors.isEmpty()) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR, "Invalid promotion items", errors);
    }
  }

  private void saveItems(Long promotionId, List<PromotionItemRequest> items) {
    for (PromotionItemRequest item : items) {
      promotionProductRepository.save(
          PromotionProduct.builder()
              .promotionId(promotionId)
              .productId(item.productId())
              .variantId(item.variantId())
              .salePriceCents(item.salePriceCents())
              .build());
    }
  }

  private void validateCouponDatesAndValues(
      CouponType type, Long value, Instant startsAt, Instant endsAt) {
    if (type == CouponType.PERCENT) {
      if (value == null || value < 1 || value > 100) {
        throw new CustomException(
            ErrorCode.VALIDATION_ERROR, "Discount percent must be between 1 and 100");
      }
    }
    if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
      throw new CustomException(ErrorCode.VALIDATION_ERROR, "endsAt must be after startsAt");
    }
  }

  private void validatePromotionDatesAndPercent(
      Integer discountPercent, Instant startsAt, Instant endsAt) {
    if (discountPercent != null && (discountPercent < 1 || discountPercent > 100)) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR, "Discount percent must be between 1 and 100");
    }
    if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
      throw new CustomException(ErrorCode.VALIDATION_ERROR, "endsAt must be after startsAt");
    }
  }

  private AdminCouponDto toCouponDto(Coupon c) {
    return new AdminCouponDto(
        c.getId(),
        c.getCode(),
        c.getType(),
        c.getValue(),
        c.getDescription(),
        c.getMinOrderCents(),
        c.getMaxDiscountCents(),
        c.getUsageLimit(),
        c.getUsedCount(),
        c.getStartsAt(),
        c.getEndsAt(),
        c.isActive());
  }

  private AdminPromotionDto toPromotionDto(Promotion p) {
    List<PromotionProduct> rows = promotionProductRepository.findByPromotionId(p.getId());
    List<Long> productIds = rows.stream().map(PromotionProduct::getProductId).distinct().toList();
    Map<Long, Product> products = new java.util.HashMap<>();
    productRepository.findAllById(productIds).forEach(pr -> products.put(pr.getId(), pr));
    List<AdminPromotionItemDto> items =
        rows.stream()
            .map(
                row -> {
                  Product pr = products.get(row.getProductId());
                  ProductVariant v =
                      row.getVariantId() == null
                          ? null
                          : productVariantRepository.findById(row.getVariantId()).orElse(null);
                  Long price =
                      v != null && v.getPriceCents() != null
                          ? v.getPriceCents()
                          : pr != null ? pr.getPriceCents() : null;
                  return new AdminPromotionItemDto(
                      row.getProductId(),
                      pr != null ? pr.getName() : null,
                      row.getVariantId(),
                      v != null ? v.getSku() : null,
                      v != null ? v.getAttributes() : null,
                      price,
                      row.getSalePriceCents());
                })
            .toList();

    return new AdminPromotionDto(
        p.getId(),
        p.getName(),
        p.getNameKm(),
        p.getType(),
        p.getDiscountPercent(),
        p.getThresholdCents(),
        p.getStartsAt(),
        p.getEndsAt(),
        p.isActive(),
        productIds,
        items);
  }
}
