package com.khmermart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.common.constant.AppConstant;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.event.InventoryLowEvent;
import com.khmermart.event.OrderPlacedEvent;
import com.khmermart.mapper.AddressMapper;
import com.khmermart.model.entity.Address;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.entity.Cart;
import com.khmermart.model.entity.CartItem;
import com.khmermart.model.entity.Coupon;
import com.khmermart.model.entity.CouponRedemption;
import com.khmermart.model.entity.IdempotencyRecord;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.InventoryMovement;
import com.khmermart.model.entity.Order;
import com.khmermart.model.entity.OrderItem;
import com.khmermart.model.entity.OrderStatusHistory;
import com.khmermart.model.entity.Payment;
import com.khmermart.model.entity.Product;
import com.khmermart.model.entity.ProductImage;
import com.khmermart.model.entity.ProductVariant;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.PromotionProduct;
import com.khmermart.model.entity.ShippingMethod;
import com.khmermart.model.enumerate.CouponRedemptionStatus;
import com.khmermart.model.enumerate.InventoryMovementType;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.model.request.CheckoutRequest;
import com.khmermart.model.response.AddressDto;
import com.khmermart.model.response.CartItemDto;
import com.khmermart.model.response.CheckoutPreviewDto;
import com.khmermart.model.response.CheckoutResponseDto;
import com.khmermart.model.response.OrderDto;
import com.khmermart.model.response.OrderItemDto;
import com.khmermart.model.response.OrderTimelineDto;
import com.khmermart.model.response.PromotionDiscountDto;
import com.khmermart.model.response.ShippingMethodDto;
import com.khmermart.repository.AddressRepository;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.CartItemRepository;
import com.khmermart.repository.CartRepository;
import com.khmermart.repository.CouponRedemptionRepository;
import com.khmermart.repository.CouponRepository;
import com.khmermart.repository.IdempotencyRecordRepository;
import com.khmermart.repository.InventoryMovementRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.OrderRepository;
import com.khmermart.repository.OrderStatusHistoryRepository;
import com.khmermart.repository.PaymentRepository;
import com.khmermart.repository.ProductImageRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.ProductVariantRepository;
import com.khmermart.repository.PromotionProductRepository;
import com.khmermart.repository.PromotionRepository;
import com.khmermart.repository.SettingRepository;
import com.khmermart.repository.ShippingMethodRepository;
import com.khmermart.service.CheckoutService;
import com.khmermart.service.PricingItem;
import com.khmermart.service.PricingResult;
import com.khmermart.service.PricingService;
import com.khmermart.service.ShippingService;
import com.khmermart.util.MoneyUtil;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutServiceImpl implements CheckoutService {

  private final AddressRepository addressRepository;
  private final CartRepository cartRepository;
  private final CartItemRepository cartItemRepository;
  private final ProductRepository productRepository;
  private final ProductVariantRepository variantRepository;
  private final ProductImageRepository imageRepository;
  private final InventoryRepository inventoryRepository;
  private final InventoryMovementRepository movementRepository;
  private final ShippingMethodRepository shippingMethodRepository;
  private final CouponRepository couponRepository;
  private final CouponRedemptionRepository couponRedemptionRepository;
  private final PromotionRepository promotionRepository;
  private final PromotionProductRepository promotionProductRepository;
  private final FlashSalePriceResolver flashSalePriceResolver;
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderStatusHistoryRepository orderStatusHistoryRepository;
  private final PaymentRepository paymentRepository;
  private final SettingRepository settingRepository;
  private final IdempotencyRecordRepository idempotencyRecordRepository;
  private final AppUserRepository appUserRepository;
  private final ShippingService shippingService;
  private final PricingService pricingService;
  private final AddressMapper addressMapper;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  public CheckoutServiceImpl(
      AddressRepository addressRepository,
      CartRepository cartRepository,
      CartItemRepository cartItemRepository,
      ProductRepository productRepository,
      ProductVariantRepository variantRepository,
      ProductImageRepository imageRepository,
      InventoryRepository inventoryRepository,
      InventoryMovementRepository movementRepository,
      ShippingMethodRepository shippingMethodRepository,
      CouponRepository couponRepository,
      CouponRedemptionRepository couponRedemptionRepository,
      PromotionRepository promotionRepository,
      PromotionProductRepository promotionProductRepository,
      FlashSalePriceResolver flashSalePriceResolver,
      OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      OrderStatusHistoryRepository orderStatusHistoryRepository,
      PaymentRepository paymentRepository,
      SettingRepository settingRepository,
      IdempotencyRecordRepository idempotencyRecordRepository,
      AppUserRepository appUserRepository,
      ShippingService shippingService,
      PricingService pricingService,
      AddressMapper addressMapper,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher) {
    this.addressRepository = addressRepository;
    this.cartRepository = cartRepository;
    this.cartItemRepository = cartItemRepository;
    this.productRepository = productRepository;
    this.variantRepository = variantRepository;
    this.imageRepository = imageRepository;
    this.inventoryRepository = inventoryRepository;
    this.movementRepository = movementRepository;
    this.shippingMethodRepository = shippingMethodRepository;
    this.couponRepository = couponRepository;
    this.couponRedemptionRepository = couponRedemptionRepository;
    this.promotionRepository = promotionRepository;
    this.promotionProductRepository = promotionProductRepository;
    this.flashSalePriceResolver = flashSalePriceResolver;
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.orderStatusHistoryRepository = orderStatusHistoryRepository;
    this.paymentRepository = paymentRepository;
    this.settingRepository = settingRepository;
    this.idempotencyRecordRepository = idempotencyRecordRepository;
    this.appUserRepository = appUserRepository;
    this.shippingService = shippingService;
    this.pricingService = pricingService;
    this.addressMapper = addressMapper;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional(readOnly = true)
  public CheckoutPreviewDto preview(Long userId, CheckoutRequest request) {
    Address address =
        addressRepository
            .findById(request.addressId())
            .filter(a -> a.getUserId().equals(userId))
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, "Delivery address not found"));

    ShippingMethod shippingMethod =
        shippingMethodRepository
            .findByCode(request.shippingMethodCode())
            .orElseThrow(
                () ->
                    new CustomException(
                        ErrorCode.NOT_FOUND,
                        "Shipping method not found: " + request.shippingMethodCode()));

    if ("SAME_DAY".equalsIgnoreCase(shippingMethod.getCode())) {
      boolean isPhnomPenh =
          "Phnom Penh".equalsIgnoreCase(address.getProvince())
              || "PHNOM_PENH".equalsIgnoreCase(address.getProvince())
              || "រាជធានីភ្នំពេញ".equals(address.getProvince());
      if (!isPhnomPenh) {
        throw new CustomException(
            ErrorCode.VALIDATION_ERROR,
            "Same-day delivery is only available in Phnom Penh",
            Map.of("shippingMethodCode", "Same-day delivery is only available in Phnom Penh"));
      }
    }

    Cart cart =
        cartRepository
            .findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.VALIDATION_ERROR, "Cart is empty"));

    List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
    if (cartItems.isEmpty()) {
      throw new CustomException(ErrorCode.VALIDATION_ERROR, "Cart is empty");
    }

    List<CartItemDto> itemDtos = new ArrayList<>();
    List<PricingItem> pricingItems = new ArrayList<>();

    Instant now = Instant.now();
    List<Promotion> activePromotions =
        promotionRepository.findAll().stream()
            .filter(
                p -> p.isActive() && !now.isBefore(p.getStartsAt()) && !now.isAfter(p.getEndsAt()))
            .toList();

    Optional<Promotion> bogoPromo =
        activePromotions.stream().filter(p -> p.getType() == PromotionType.BOGO).findFirst();

    Optional<Promotion> seasonalPromo =
        activePromotions.stream().filter(p -> p.getType() == PromotionType.SEASONAL).findFirst();

    for (CartItem item : cartItems) {
      Product product = productRepository.findById(item.getProductId()).orElse(null);
      if (product == null) continue;

      final ProductVariant variant =
          item.getVariantId() != null
              ? variantRepository
                  .findById(item.getVariantId())
                  .filter(ProductVariant::isActive)
                  .orElseThrow(
                      () ->
                          new CustomException(
                              ErrorCode.OUT_OF_STOCK, "Product variant is no longer available"))
              : null;

      Long currentUnitPrice =
          (variant != null && variant.getPriceCents() != null)
              ? variant.getPriceCents()
              : product.getPriceCents();

      Inventory inv =
          (variant != null)
              ? inventoryRepository
                  .findByProductIdAndVariantId(product.getId(), variant.getId())
                  .orElse(null)
              : inventoryRepository.findByProductIdAndVariantIdIsNull(product.getId()).orElse(null);

      int available =
          (inv != null) ? Math.max(0, inv.getQuantityOnHand() - inv.getQuantityReserved()) : 0;

      Long flashSalePrice =
          flashSalePriceResolver.resolve(
              activePromotions, product.getId(), variant != null ? variant.getId() : null);

      String imageUrl =
          imageRepository.findByProductIdOrderBySortOrderAsc(product.getId()).stream()
              .findFirst()
              .map(ProductImage::getUrl)
              .orElse(null);

      Map<String, String> attributes =
          (variant != null && variant.getAttributes() != null) ? variant.getAttributes() : null;

      itemDtos.add(
          new CartItemDto(
              item.getId(),
              product.getId(),
              variant != null ? variant.getId() : null,
              product.getName(),
              product.getNameKm(),
              imageUrl,
              product.getSlug(),
              attributes,
              currentUnitPrice,
              item.getQuantity(),
              currentUnitPrice * item.getQuantity(),
              available,
              List.of()));

      boolean bogoEligible = false;
      if (bogoPromo.isPresent()) {
        bogoEligible =
            promotionProductRepository
                .findByPromotionIdAndProductId(bogoPromo.get().getId(), product.getId())
                .stream()
                .anyMatch(
                    pp ->
                        pp.getVariantId() == null
                            || (variant != null && pp.getVariantId().equals(variant.getId())));
      }

      Integer seasonalPercent = null;
      if (seasonalPromo.isPresent()) {
        boolean seasonalEligible =
            promotionProductRepository
                .findByPromotionIdAndProductId(seasonalPromo.get().getId(), product.getId())
                .stream()
                .anyMatch(
                    pp ->
                        pp.getVariantId() == null
                            || (variant != null && pp.getVariantId().equals(variant.getId())));
        seasonalPercent = seasonalEligible ? seasonalPromo.get().getDiscountPercent() : 0;
      }

      pricingItems.add(
          new PricingItem(
              product.getId(),
              variant != null ? variant.getId() : null,
              currentUnitPrice,
              item.getQuantity(),
              flashSalePrice,
              bogoEligible,
              seasonalPercent));
    }

    Coupon coupon = null;
    if (cart.getCouponId() != null) {
      coupon = couponRepository.findById(cart.getCouponId()).orElse(null);
    }

    PricingResult pricing =
        pricingService.calculate(pricingItems, activePromotions, coupon, shippingMethod);

    long khrRate = AppConstant.DEFAULT_USD_KHR_RATE;
    var settingOpt = settingRepository.findById("USD_KHR_RATE");
    if (settingOpt.isPresent()) {
      try {
        khrRate = Long.parseLong(settingOpt.get().getValue());
      } catch (Exception ignored) {
      }
    }

    long totalKhr = MoneyUtil.toKhr(pricing.totalCents(), khrRate);

    AddressDto addressDto = addressMapper.toDto(address);
    ShippingMethodDto shippingMethodDto = shippingService.toDto(shippingMethod);

    List<PaymentMethod> availablePaymentMethods =
        List.of(
            PaymentMethod.KHQR,
            PaymentMethod.COD,
            PaymentMethod.BANK_TRANSFER,
            PaymentMethod.CARD,
            PaymentMethod.WALLET);

    return new CheckoutPreviewDto(
        itemDtos,
        pricing.subtotalCents(),
        pricing.discountCents(),
        pricing.shippingCents(),
        pricing.totalCents(),
        totalKhr,
        shippingMethodDto,
        addressDto,
        availablePaymentMethods);
  }

  @Override
  @Transactional
  public CheckoutResponseDto checkout(Long userId, String idempotencyKey, CheckoutRequest request) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new CustomException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key header is required");
    }

    String redisKey = "idem:" + userId + ":" + idempotencyKey.trim();

    // 1. Check idempotency in Redis
    String cachedJson = redisTemplate.opsForValue().get(redisKey);
    if (cachedJson != null) {
      try {
        return objectMapper.readValue(cachedJson, CheckoutResponseDto.class);
      } catch (Exception ignored) {
      }
    }

    // Check DB fallback
    Optional<IdempotencyRecord> dbRecord = idempotencyRecordRepository.findById(redisKey);
    if (dbRecord.isPresent()) {
      try {
        return objectMapper.readValue(dbRecord.get().getResponseJson(), CheckoutResponseDto.class);
      } catch (Exception ignored) {
      }
    }

    // 2. Validate address
    Address address =
        addressRepository
            .findById(request.addressId())
            .filter(a -> a.getUserId().equals(userId))
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, "Delivery address not found"));

    ShippingMethod shippingMethod =
        shippingMethodRepository
            .findByCode(request.shippingMethodCode())
            .orElseThrow(
                () ->
                    new CustomException(
                        ErrorCode.NOT_FOUND,
                        "Shipping method not found: " + request.shippingMethodCode()));

    if ("SAME_DAY".equalsIgnoreCase(shippingMethod.getCode())) {
      boolean isPhnomPenh =
          "Phnom Penh".equalsIgnoreCase(address.getProvince())
              || "PHNOM_PENH".equalsIgnoreCase(address.getProvince())
              || "រាជធានីភ្នំពេញ".equals(address.getProvince());
      if (!isPhnomPenh) {
        throw new CustomException(
            ErrorCode.VALIDATION_ERROR,
            "Same-day delivery is only available in Phnom Penh",
            Map.of("shippingMethodCode", "Same-day delivery is only available in Phnom Penh"));
      }
    }

    // 3. Validate Cart
    Cart cart =
        cartRepository
            .findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.VALIDATION_ERROR, "Cart is empty"));

    List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
    if (cartItems.isEmpty()) {
      throw new CustomException(ErrorCode.VALIDATION_ERROR, "Cart is empty");
    }

    // 4. Validate prices against DB
    List<PricingItem> pricingItems = new ArrayList<>();
    Instant now = Instant.now();
    List<Promotion> activePromotions =
        promotionRepository.findAll().stream()
            .filter(
                p -> p.isActive() && !now.isBefore(p.getStartsAt()) && !now.isAfter(p.getEndsAt()))
            .toList();

    Optional<Promotion> bogoPromo =
        activePromotions.stream().filter(p -> p.getType() == PromotionType.BOGO).findFirst();

    Optional<Promotion> seasonalPromo =
        activePromotions.stream().filter(p -> p.getType() == PromotionType.SEASONAL).findFirst();

    for (CartItem item : cartItems) {
      Product product =
          productRepository
              .findById(item.getProductId())
              .filter(Product::isActive)
              .orElseThrow(
                  () ->
                      new CustomException(
                          ErrorCode.OUT_OF_STOCK, "Product is no longer available"));

      final ProductVariant variant =
          item.getVariantId() != null
              ? variantRepository
                  .findById(item.getVariantId())
                  .filter(ProductVariant::isActive)
                  .orElseThrow(
                      () ->
                          new CustomException(
                              ErrorCode.OUT_OF_STOCK, "Product variant is no longer available"))
              : null;

      Long currentUnitPrice =
          (variant != null && variant.getPriceCents() != null)
              ? variant.getPriceCents()
              : product.getPriceCents();

      if (!currentUnitPrice.equals(item.getUnitPriceCentsSnapshot())) {
        throw new CustomException(
            ErrorCode.PRICE_CHANGED,
            "Price has changed for " + product.getName(),
            Map.of(
                "productId",
                product.getId(),
                "oldPrice",
                item.getUnitPriceCentsSnapshot(),
                "newPrice",
                currentUnitPrice));
      }

      Long flashSalePrice =
          flashSalePriceResolver.resolve(
              activePromotions, product.getId(), variant != null ? variant.getId() : null);

      boolean bogoEligible = false;
      if (bogoPromo.isPresent()) {
        bogoEligible =
            promotionProductRepository
                .findByPromotionIdAndProductId(bogoPromo.get().getId(), product.getId())
                .stream()
                .anyMatch(
                    pp ->
                        pp.getVariantId() == null
                            || (variant != null && pp.getVariantId().equals(variant.getId())));
      }

      Integer seasonalPercent = null;
      if (seasonalPromo.isPresent()) {
        boolean seasonalEligible =
            promotionProductRepository
                .findByPromotionIdAndProductId(seasonalPromo.get().getId(), product.getId())
                .stream()
                .anyMatch(
                    pp ->
                        pp.getVariantId() == null
                            || (variant != null && pp.getVariantId().equals(variant.getId())));
        seasonalPercent = seasonalEligible ? seasonalPromo.get().getDiscountPercent() : 0;
      }

      pricingItems.add(
          new PricingItem(
              product.getId(),
              variant != null ? variant.getId() : null,
              currentUnitPrice,
              item.getQuantity(),
              flashSalePrice,
              bogoEligible,
              seasonalPercent));
    }

    Coupon coupon = null;
    if (cart.getCouponId() != null) {
      coupon = couponRepository.findById(cart.getCouponId()).orElse(null);
    }

    PricingResult pricing =
        pricingService.calculate(pricingItems, activePromotions, coupon, shippingMethod);

    // 5. Reserve Inventory
    List<InventoryMovement> movements = new ArrayList<>();
    for (CartItem item : cartItems) {
      Inventory inv =
          (item.getVariantId() != null)
              ? inventoryRepository
                  .findByProductIdAndVariantId(item.getProductId(), item.getVariantId())
                  .orElse(null)
              : inventoryRepository
                  .findByProductIdAndVariantIdIsNull(item.getProductId())
                  .orElse(null);

      if (inv == null) {
        throw new CustomException(ErrorCode.OUT_OF_STOCK, "Inventory item not found");
      }

      int rows = inventoryRepository.tryReserve(inv.getId(), item.getQuantity());
      if (rows != 1) {
        // Fresh scalar read for the message; the loaded entity may predate concurrent reservations.
        int availableNow = Math.max(0, inventoryRepository.availableOf(inv.getId()));
        throw new CustomException(
            ErrorCode.OUT_OF_STOCK,
            "Only " + availableNow + " left in stock",
            Map.of("productId", item.getProductId(), "available", availableNow));
      }

      // Never save(inv) here: that would write back the stale quantity_reserved loaded above.
      int availableAfter = Math.max(0, inventoryRepository.availableOf(inv.getId()));
      int threshold = inv.getLowStockThreshold();
      if (availableAfter <= threshold
          && inventoryRepository.markLowStockAlerted(inv.getId()) == 1) {
        Product prod = productRepository.findById(item.getProductId()).orElse(null);
        String prodName = prod != null ? prod.getName() : "Product #" + item.getProductId();
        String sku = prod != null ? prod.getSku() : "";
        eventPublisher.publishEvent(
            new InventoryLowEvent(item.getProductId(), prodName, sku, availableAfter, threshold));
      }

      InventoryMovement movement =
          InventoryMovement.builder()
              .inventoryId(inv.getId())
              .type(InventoryMovementType.RESERVE)
              .quantity(item.getQuantity())
              .reason("Order checkout reservation")
              .actorId(userId)
              .createdAt(Instant.now())
              .build();
      movements.add(movement);
    }

    // 6. Generate order number
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Phnom_Penh"));
    String dateStr = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    Long seq = redisTemplate.opsForValue().increment("order:seq:" + dateStr);
    String orderNumber = String.format("KM-%s-%06d", dateStr, seq != null ? seq : 1L);

    OrderStatus initialStatus;
    PaymentStatus initialPaymentStatus;
    String nextAction;
    Instant confirmedAt = null;
    Instant paymentDeadline = null;

    if (request.paymentMethod() == PaymentMethod.COD) {
      initialStatus = OrderStatus.CONFIRMED;
      initialPaymentStatus = PaymentStatus.PENDING;
      nextAction = "ORDER_SUCCESS";
      confirmedAt = Instant.now();
    } else if (request.paymentMethod() == PaymentMethod.KHQR) {
      initialStatus = OrderStatus.PENDING_PAYMENT;
      initialPaymentStatus = PaymentStatus.PENDING;
      nextAction = "KHQR_PAYMENT";
      paymentDeadline = Instant.now().plus(Duration.ofMinutes(10));
    } else if (request.paymentMethod() == PaymentMethod.BANK_TRANSFER) {
      initialStatus = OrderStatus.PENDING_PAYMENT;
      initialPaymentStatus = PaymentStatus.PENDING;
      nextAction = "BANK_TRANSFER_SLIP";
      paymentDeadline = Instant.now().plus(Duration.ofHours(24));
    } else {
      initialStatus = OrderStatus.CONFIRMED;
      initialPaymentStatus = PaymentStatus.PAID;
      nextAction = "ORDER_SUCCESS";
      confirmedAt = Instant.now();
    }

    AddressDto addressDto = addressMapper.toDto(address);
    String addressJson;
    try {
      addressJson = objectMapper.writeValueAsString(addressDto);
    } catch (Exception ex) {
      addressJson = "{}";
    }

    String discountBreakdownJson = null;
    try {
      List<Map<String, Object>> promoList = new ArrayList<>();
      if (pricing.promotionDiscounts() != null) {
        for (PromotionDiscountDto p : pricing.promotionDiscounts()) {
          promoList.add(Map.of("label", p.label(), "amountCents", p.amountCents()));
        }
      }
      long promoSum =
          pricing.promotionDiscounts() != null
              ? pricing.promotionDiscounts().stream()
                  .mapToLong(PromotionDiscountDto::amountCents)
                  .sum()
              : 0L;
      long couponAmountCents = Math.max(0L, pricing.discountCents() - promoSum);

      Map<String, Object> couponMap = null;
      if (coupon != null && couponAmountCents > 0) {
        couponMap = Map.of("code", coupon.getCode(), "amountCents", couponAmountCents);
      }

      Map<String, Object> breakdown = new HashMap<>();
      breakdown.put("promotions", promoList);
      if (couponMap != null) {
        breakdown.put("coupon", couponMap);
      }
      discountBreakdownJson = objectMapper.writeValueAsString(breakdown);
    } catch (Exception ignored) {
    }

    Order order =
        Order.builder()
            .orderNumber(orderNumber)
            .userId(userId)
            .status(initialStatus)
            .paymentStatus(initialPaymentStatus)
            .paymentMethod(request.paymentMethod())
            .subtotalCents(pricing.subtotalCents())
            .discountCents(pricing.discountCents())
            .shippingCents(pricing.shippingCents())
            .totalCents(pricing.totalCents())
            .currency("USD")
            .couponCode(coupon != null ? coupon.getCode() : null)
            .discountBreakdown(discountBreakdownJson)
            .shippingAddress(addressJson)
            .shippingMethodCode(shippingMethod.getCode())
            .notes(request.notes())
            .paymentDeadline(paymentDeadline)
            .placedAt(Instant.now())
            .confirmedAt(confirmedAt)
            .build();

    order = orderRepository.save(order);

    for (InventoryMovement m : movements) {
      m.setRefOrderId(order.getId());
      movementRepository.save(m);
    }

    List<OrderItemDto> orderItemDtos = new ArrayList<>();
    for (CartItem item : cartItems) {
      Product product = productRepository.findById(item.getProductId()).get();
      ProductVariant variant =
          item.getVariantId() != null
              ? variantRepository.findById(item.getVariantId()).orElse(null)
              : null;

      String imageUrl =
          imageRepository.findByProductIdOrderBySortOrderAsc(product.getId()).stream()
              .findFirst()
              .map(ProductImage::getUrl)
              .orElse(null);

      Long currentUnitPrice =
          (variant != null && variant.getPriceCents() != null)
              ? variant.getPriceCents()
              : product.getPriceCents();

      String attrJson = null;
      if (variant != null && variant.getAttributes() != null) {
        try {
          attrJson = objectMapper.writeValueAsString(variant.getAttributes());
        } catch (Exception ignored) {
        }
      }

      OrderItem orderItem =
          OrderItem.builder()
              .orderId(order.getId())
              .productId(product.getId())
              .variantId(variant != null ? variant.getId() : null)
              .productNameSnapshot(product.getName())
              .skuSnapshot(variant != null ? variant.getSku() : product.getSku())
              .attributesSnapshot(attrJson)
              .imageUrlSnapshot(imageUrl)
              .unitPriceCents(currentUnitPrice)
              .quantity(item.getQuantity())
              .lineTotalCents(currentUnitPrice * item.getQuantity())
              .build();

      orderItem = orderItemRepository.save(orderItem);

      Map<String, String> attributes =
          (variant != null && variant.getAttributes() != null) ? variant.getAttributes() : null;

      orderItemDtos.add(
          new OrderItemDto(
              orderItem.getId(),
              product.getId(),
              product.getSlug(),
              product.getName(),
              imageUrl,
              orderItem.getSkuSnapshot(),
              attributes,
              currentUnitPrice,
              item.getQuantity(),
              orderItem.getLineTotalCents(),
              false));
    }

    OrderStatusHistory history =
        OrderStatusHistory.builder()
            .orderId(order.getId())
            .fromStatus(null)
            .toStatus(initialStatus.name())
            .note("Order placed via checkout")
            .actorId(userId)
            .createdAt(Instant.now())
            .build();
    orderStatusHistoryRepository.save(history);

    Payment payment =
        Payment.builder()
            .orderId(order.getId())
            .method(request.paymentMethod())
            .status(initialPaymentStatus)
            .amountCents(pricing.totalCents())
            .currency("USD")
            .createdAt(Instant.now())
            .build();
    paymentRepository.save(payment);

    if (coupon != null) {
      CouponRedemption redemption =
          CouponRedemption.builder()
              .couponId(coupon.getId())
              .userId(userId)
              .orderId(order.getId())
              .status(
                  initialStatus == OrderStatus.CONFIRMED
                      ? CouponRedemptionStatus.REDEEMED
                      : CouponRedemptionStatus.PENDING)
              .build();
      couponRedemptionRepository.save(redemption);
      if (initialStatus == OrderStatus.CONFIRMED) {
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        couponRepository.save(coupon);
      }
    }

    // Clear cart
    cartItemRepository.deleteByCartId(cart.getId());
    cart.setCouponId(null);
    cart.setUpdatedAt(Instant.now());
    cartRepository.save(cart);

    List<OrderTimelineDto> timeline =
        List.of(
            new OrderTimelineDto(
                initialStatus.name(), "Order placed via checkout", order.getPlacedAt()));

    boolean canCancel =
        order.getStatus() == OrderStatus.PENDING_PAYMENT
            || order.getStatus() == OrderStatus.CONFIRMED;

    OrderDto orderDto =
        new OrderDto(
            order.getId(),
            order.getOrderNumber(),
            order.getStatus(),
            order.getPaymentStatus(),
            order.getPaymentMethod(),
            orderItemDtos,
            order.getSubtotalCents(),
            order.getDiscountCents(),
            order.getShippingCents(),
            order.getTotalCents(),
            order.getCouponCode(),
            addressDto,
            shippingService.toDto(shippingMethod),
            order.getTrackingNumber(),
            order.getCarrier(),
            order.getPaymentDeadline(),
            order.getPlacedAt(),
            order.getConfirmedAt(),
            order.getShippedAt(),
            order.getDeliveredAt(),
            order.getCancelledAt(),
            order.getCancelReason(),
            timeline,
            canCancel,
            false,
            "/api/orders/" + order.getOrderNumber() + "/invoice.pdf");

    CheckoutResponseDto responseDto = new CheckoutResponseDto(orderDto, nextAction);

    // Save Idempotency in Redis & DB
    try {
      String json = objectMapper.writeValueAsString(responseDto);
      redisTemplate.opsForValue().set(redisKey, json, Duration.ofHours(24));
      idempotencyRecordRepository.save(
          IdempotencyRecord.builder()
              .key(redisKey)
              .userId(userId)
              .responseJson(json)
              .createdAt(Instant.now())
              .build());
    } catch (Exception ignored) {
    }

    AppUser user = appUserRepository.findById(userId).orElse(null);
    eventPublisher.publishEvent(
        new OrderPlacedEvent(
            order.getId(),
            order.getOrderNumber(),
            order.getUserId(),
            user != null ? user.getEmail() : null,
            user != null ? user.getFullName() : null,
            user != null ? user.getPhone() : null,
            order.getTotalCents(),
            order.getSubtotalCents(),
            order.getShippingCents(),
            order.getCouponCode()));

    return responseDto;
  }
}
