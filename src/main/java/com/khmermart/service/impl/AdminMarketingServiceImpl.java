package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.model.entity.Coupon;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.PromotionProduct;
import com.khmermart.model.enumerate.CouponType;
import com.khmermart.model.request.AdminCouponRequest;
import com.khmermart.model.request.AdminPromotionRequest;
import com.khmermart.model.response.AdminCouponDto;
import com.khmermart.model.response.AdminPromotionDto;
import com.khmermart.repository.CouponRepository;
import com.khmermart.repository.PromotionProductRepository;
import com.khmermart.repository.PromotionRepository;
import com.khmermart.service.AdminMarketingService;
import com.khmermart.service.AuditLogService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMarketingServiceImpl implements AdminMarketingService {

  private final CouponRepository couponRepository;
  private final PromotionRepository promotionRepository;
  private final PromotionProductRepository promotionProductRepository;
  private final AuditLogService auditLogService;

  public AdminMarketingServiceImpl(
      CouponRepository couponRepository,
      PromotionRepository promotionRepository,
      PromotionProductRepository promotionProductRepository,
      AuditLogService auditLogService) {
    this.couponRepository = couponRepository;
    this.promotionRepository = promotionRepository;
    this.promotionProductRepository = promotionProductRepository;
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

    if (request.productIds() != null && !request.productIds().isEmpty()) {
      for (Long pid : request.productIds()) {
        promotionProductRepository.save(
            PromotionProduct.builder().promotionId(saved.getId()).productId(pid).build());
      }
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

    if (request.productIds() != null) {
      promotionProductRepository.deleteByPromotionId(id);
      for (Long pid : request.productIds()) {
        promotionProductRepository.save(
            PromotionProduct.builder().promotionId(updated.getId()).productId(pid).build());
      }
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
    List<Long> productIds =
        promotionProductRepository.findByPromotionId(p.getId()).stream()
            .map(PromotionProduct::getProductId)
            .distinct()
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
        productIds);
  }
}
