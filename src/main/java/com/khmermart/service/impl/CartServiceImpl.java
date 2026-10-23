package com.khmermart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.model.entity.Cart;
import com.khmermart.model.entity.CartItem;
import com.khmermart.model.entity.Coupon;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.Product;
import com.khmermart.model.entity.ProductImage;
import com.khmermart.model.entity.ProductVariant;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.PromotionProduct;
import com.khmermart.model.entity.ShippingMethod;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.model.request.CartItemRequest;
import com.khmermart.model.request.UpdateCartItemRequest;
import com.khmermart.model.response.CartDto;
import com.khmermart.model.response.CartItemDto;
import com.khmermart.repository.CartItemRepository;
import com.khmermart.repository.CartRepository;
import com.khmermart.repository.CouponRedemptionRepository;
import com.khmermart.repository.CouponRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.ProductImageRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.ProductVariantRepository;
import com.khmermart.repository.PromotionProductRepository;
import com.khmermart.repository.PromotionRepository;
import com.khmermart.repository.ShippingMethodRepository;
import com.khmermart.service.CartService;
import com.khmermart.service.PricingItem;
import com.khmermart.service.PricingResult;
import com.khmermart.service.PricingService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartServiceImpl implements CartService {

  private final CartRepository cartRepository;
  private final CartItemRepository cartItemRepository;
  private final ProductRepository productRepository;
  private final ProductVariantRepository variantRepository;
  private final ProductImageRepository imageRepository;
  private final InventoryRepository inventoryRepository;
  private final CouponRepository couponRepository;
  private final CouponRedemptionRepository couponRedemptionRepository;
  private final PromotionRepository promotionRepository;
  private final PromotionProductRepository promotionProductRepository;
  private final ShippingMethodRepository shippingMethodRepository;
  private final PricingService pricingService;
  private final ObjectMapper objectMapper;

  public CartServiceImpl(
      CartRepository cartRepository,
      CartItemRepository cartItemRepository,
      ProductRepository productRepository,
      ProductVariantRepository variantRepository,
      ProductImageRepository imageRepository,
      InventoryRepository inventoryRepository,
      CouponRepository couponRepository,
      CouponRedemptionRepository couponRedemptionRepository,
      PromotionRepository promotionRepository,
      PromotionProductRepository promotionProductRepository,
      ShippingMethodRepository shippingMethodRepository,
      PricingService pricingService,
      ObjectMapper objectMapper) {
    this.cartRepository = cartRepository;
    this.cartItemRepository = cartItemRepository;
    this.productRepository = productRepository;
    this.variantRepository = variantRepository;
    this.imageRepository = imageRepository;
    this.inventoryRepository = inventoryRepository;
    this.couponRepository = couponRepository;
    this.couponRedemptionRepository = couponRedemptionRepository;
    this.promotionRepository = promotionRepository;
    this.promotionProductRepository = promotionProductRepository;
    this.shippingMethodRepository = shippingMethodRepository;
    this.pricingService = pricingService;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public Cart getOrCreateCart(Long userId, String guestToken) {
    if (userId != null) {
      return cartRepository
          .findByUserId(userId)
          .orElseGet(
              () ->
                  cartRepository.save(
                      Cart.builder().userId(userId).updatedAt(Instant.now()).build()));
    }

    if (guestToken != null && !guestToken.isBlank()) {
      Optional<Cart> existing = cartRepository.findByGuestToken(guestToken);
      if (existing.isPresent()) {
        return existing.get();
      }
    }

    String token =
        (guestToken != null && !guestToken.isBlank()) ? guestToken : UUID.randomUUID().toString();
    return cartRepository.save(Cart.builder().guestToken(token).updatedAt(Instant.now()).build());
  }

  @Override
  @Transactional(readOnly = true)
  public CartDto getCartDto(Cart cart) {
    List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
    List<CartItemDto> itemDtos = new ArrayList<>();
    List<PricingItem> pricingItems = new ArrayList<>();

    Instant now = Instant.now();
    List<Promotion> activePromotions =
        promotionRepository.findAll().stream()
            .filter(
                p -> p.isActive() && !now.isBefore(p.getStartsAt()) && !now.isAfter(p.getEndsAt()))
            .toList();

    Optional<Promotion> flashSalePromo =
        activePromotions.stream().filter(p -> p.getType() == PromotionType.FLASH_SALE).findFirst();

    Optional<Promotion> bogoPromo =
        activePromotions.stream().filter(p -> p.getType() == PromotionType.BOGO).findFirst();

    for (CartItem item : items) {
      Product product = productRepository.findById(item.getProductId()).orElse(null);
      if (product == null) {
        continue;
      }

      ProductVariant variant = null;
      if (item.getVariantId() != null) {
        variant = variantRepository.findById(item.getVariantId()).orElse(null);
      }

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

      List<String> issues = new ArrayList<>();
      if (available <= 0) {
        issues.add("OUT_OF_STOCK");
      } else if (item.getQuantity() > available) {
        issues.add("QTY_REDUCED");
      }
      if (!currentUnitPrice.equals(item.getUnitPriceCentsSnapshot())) {
        issues.add("PRICE_CHANGED");
      }

      Long flashSalePrice = null;
      if (flashSalePromo.isPresent()) {
        Optional<PromotionProduct> pp =
            promotionProductRepository.findByPromotionIdAndProductId(
                flashSalePromo.get().getId(), product.getId());
        if (pp.isPresent()) {
          flashSalePrice = pp.get().getSalePriceCents();
        }
      }

      String imageUrl =
          imageRepository.findByProductIdOrderBySortOrderAsc(product.getId()).stream()
              .findFirst()
              .map(ProductImage::getUrl)
              .orElse(null);

      Map<String, String> attributes =
          (variant != null && variant.getAttributes() != null) ? variant.getAttributes() : null;

      long lineTotalCents = currentUnitPrice * item.getQuantity();

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
              lineTotalCents,
              available,
              issues));

      boolean bogoEligible = false;
      if (bogoPromo.isPresent()) {
        bogoEligible =
            promotionProductRepository
                .findByPromotionIdAndProductId(bogoPromo.get().getId(), product.getId())
                .isPresent();
      }

      pricingItems.add(
          new PricingItem(
              product.getId(),
              variant != null ? variant.getId() : null,
              currentUnitPrice,
              item.getQuantity(),
              flashSalePrice,
              bogoEligible));
    }

    Coupon coupon = null;
    if (cart.getCouponId() != null) {
      coupon = couponRepository.findById(cart.getCouponId()).orElse(null);
    }

    ShippingMethod defaultShipping = shippingMethodRepository.findByCode("STANDARD").orElse(null);

    PricingResult pricing =
        pricingService.calculate(pricingItems, activePromotions, coupon, defaultShipping);

    int totalItemCount = itemDtos.stream().mapToInt(CartItemDto::quantity).sum();

    return new CartDto(
        cart.getId(),
        itemDtos,
        totalItemCount,
        pricing.subtotalCents(),
        pricing.discountCents(),
        pricing.promotionDiscounts(),
        pricing.coupon(),
        pricing.shippingCents(),
        pricing.totalCents());
  }

  @Override
  @Transactional
  public CartDto addItem(Cart cart, CartItemRequest request) {
    Product product =
        productRepository
            .findById(request.productId())
            .filter(Product::isActive)
            .orElseThrow(
                () ->
                    new CustomException(
                        ErrorCode.NOT_FOUND,
                        "Product not found or inactive: " + request.productId()));

    ProductVariant variant = null;
    if (request.variantId() != null) {
      variant =
          variantRepository
              .findById(request.variantId())
              .filter(v -> v.getProductId().equals(product.getId()) && v.isActive())
              .orElseThrow(
                  () ->
                      new CustomException(
                          ErrorCode.NOT_FOUND,
                          "Variant not found or inactive: " + request.variantId()));
    } else {
      List<ProductVariant> variants =
          variantRepository.findByProductIdAndActiveTrue(product.getId());
      if (!variants.isEmpty()) {
        variant = variants.get(0);
      }
    }

    Long currentPrice =
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

    Optional<CartItem> existing =
        (variant != null)
            ? cartItemRepository.findByCartIdAndProductIdAndVariantId(
                cart.getId(), product.getId(), variant.getId())
            : cartItemRepository.findByCartIdAndProductIdAndVariantIdIsNull(
                cart.getId(), product.getId());

    int existingQty = existing.map(CartItem::getQuantity).orElse(0);
    int newTotalQty = existingQty + request.quantity();

    if (newTotalQty > available || available <= 0) {
      throw new CustomException(
          ErrorCode.OUT_OF_STOCK,
          "Only " + available + " left in stock",
          Map.of("available", available, "productId", product.getId()));
    }

    if (existing.isPresent()) {
      CartItem item = existing.get();
      item.setQuantity(newTotalQty);
      item.setUnitPriceCentsSnapshot(currentPrice);
      cartItemRepository.save(item);
    } else {
      CartItem newItem =
          CartItem.builder()
              .cartId(cart.getId())
              .productId(product.getId())
              .variantId(variant != null ? variant.getId() : null)
              .quantity(request.quantity())
              .unitPriceCentsSnapshot(currentPrice)
              .build();
      cartItemRepository.save(newItem);
    }

    cart.setUpdatedAt(Instant.now());
    cartRepository.save(cart);

    return getCartDto(cart);
  }

  @Override
  @Transactional
  public CartDto updateItem(Cart cart, Long itemId, UpdateCartItemRequest request) {
    CartItem item =
        cartItemRepository
            .findById(itemId)
            .filter(i -> i.getCartId().equals(cart.getId()))
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, "Cart item not found: " + itemId));

    Inventory inv =
        (item.getVariantId() != null)
            ? inventoryRepository
                .findByProductIdAndVariantId(item.getProductId(), item.getVariantId())
                .orElse(null)
            : inventoryRepository
                .findByProductIdAndVariantIdIsNull(item.getProductId())
                .orElse(null);

    int available =
        (inv != null) ? Math.max(0, inv.getQuantityOnHand() - inv.getQuantityReserved()) : 0;

    if (request.quantity() > available) {
      throw new CustomException(
          ErrorCode.OUT_OF_STOCK,
          "Only " + available + " left in stock",
          Map.of("available", available, "productId", item.getProductId()));
    }

    item.setQuantity(request.quantity());
    cartItemRepository.save(item);

    cart.setUpdatedAt(Instant.now());
    cartRepository.save(cart);

    return getCartDto(cart);
  }

  @Override
  @Transactional
  public CartDto removeItem(Cart cart, Long itemId) {
    CartItem item =
        cartItemRepository
            .findById(itemId)
            .filter(i -> i.getCartId().equals(cart.getId()))
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, "Cart item not found: " + itemId));

    cartItemRepository.delete(item);
    cart.setUpdatedAt(Instant.now());
    cartRepository.save(cart);

    return getCartDto(cart);
  }

  @Override
  @Transactional
  public CartDto clearCart(Cart cart) {
    cartItemRepository.deleteByCartId(cart.getId());
    cart.setCouponId(null);
    cart.setUpdatedAt(Instant.now());
    cartRepository.save(cart);

    return getCartDto(cart);
  }

  @Override
  @Transactional
  public CartDto applyCoupon(Cart cart, String couponCode) {
    if (couponCode == null || couponCode.isBlank()) {
      throw new CustomException(ErrorCode.COUPON_INVALID, "Coupon code is required");
    }

    Coupon coupon =
        couponRepository
            .findByCodeIgnoreCase(couponCode.trim())
            .orElseThrow(
                () -> new CustomException(ErrorCode.COUPON_INVALID, "Coupon code is invalid"));

    if (!coupon.isActive()) {
      throw new CustomException(ErrorCode.COUPON_INVALID, "Coupon is no longer active");
    }

    Instant now = Instant.now();
    if ((coupon.getStartsAt() != null && coupon.getStartsAt().isAfter(now))
        || (coupon.getEndsAt() != null && coupon.getEndsAt().isBefore(now))) {
      throw new CustomException(ErrorCode.COUPON_EXPIRED, "Coupon has expired");
    }

    if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
      throw new CustomException(ErrorCode.COUPON_INVALID, "Coupon usage limit reached");
    }

    if (cart.getUserId() != null) {
      int count =
          couponRedemptionRepository.countByCouponIdAndUserId(coupon.getId(), cart.getUserId());
      if (count >= coupon.getPerUserLimit()) {
        throw new CustomException(
            ErrorCode.COUPON_INVALID, "Coupon usage limit reached for this account");
      }
    }

    // Check cart subtotal
    List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
    long currentSubtotal = 0L;
    for (CartItem item : items) {
      currentSubtotal += item.getUnitPriceCentsSnapshot() * item.getQuantity();
    }

    if (coupon.getMinOrderCents() != null && currentSubtotal < coupon.getMinOrderCents()) {
      throw new CustomException(
          ErrorCode.COUPON_MIN_ORDER,
          String.format(
              "Minimum order amount of $%.2f is required for coupon %s",
              coupon.getMinOrderCents() / 100.0, coupon.getCode()));
    }

    cart.setCouponId(coupon.getId());
    cart.setUpdatedAt(Instant.now());
    cartRepository.save(cart);

    return getCartDto(cart);
  }

  @Override
  @Transactional
  public CartDto removeCoupon(Cart cart) {
    cart.setCouponId(null);
    cart.setUpdatedAt(Instant.now());
    cartRepository.save(cart);

    return getCartDto(cart);
  }

  @Override
  @Transactional
  public CartDto mergeGuestCart(Long userId, String guestToken) {
    Cart userCart = getOrCreateCart(userId, null);

    if (guestToken == null || guestToken.isBlank()) {
      return getCartDto(userCart);
    }

    Optional<Cart> guestCartOpt = cartRepository.findByGuestToken(guestToken);
    if (guestCartOpt.isEmpty() || guestCartOpt.get().getId().equals(userCart.getId())) {
      return getCartDto(userCart);
    }

    Cart guestCart = guestCartOpt.get();
    List<CartItem> guestItems = cartItemRepository.findByCartId(guestCart.getId());

    for (CartItem guestItem : guestItems) {
      Product product = productRepository.findById(guestItem.getProductId()).orElse(null);
      if (product == null || !product.isActive()) {
        continue;
      }

      ProductVariant variant = null;
      if (guestItem.getVariantId() != null) {
        variant = variantRepository.findById(guestItem.getVariantId()).orElse(null);
        if (variant == null || !variant.isActive()) {
          continue;
        }
      }

      Long currentPrice =
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

      Optional<CartItem> userItemOpt =
          (variant != null)
              ? cartItemRepository.findByCartIdAndProductIdAndVariantId(
                  userCart.getId(), product.getId(), variant.getId())
              : cartItemRepository.findByCartIdAndProductIdAndVariantIdIsNull(
                  userCart.getId(), product.getId());

      if (userItemOpt.isPresent()) {
        CartItem userItem = userItemOpt.get();
        int combined = userItem.getQuantity() + guestItem.getQuantity();
        int capped = Math.min(combined, available);
        userItem.setQuantity(capped);
        userItem.setUnitPriceCentsSnapshot(currentPrice);
        cartItemRepository.save(userItem);
      } else {
        int capped = Math.min(guestItem.getQuantity(), available);
        if (capped > 0) {
          CartItem newItem =
              CartItem.builder()
                  .cartId(userCart.getId())
                  .productId(product.getId())
                  .variantId(variant != null ? variant.getId() : null)
                  .quantity(capped)
                  .unitPriceCentsSnapshot(currentPrice)
                  .build();
          cartItemRepository.save(newItem);
        }
      }
    }

    if (userCart.getCouponId() == null && guestCart.getCouponId() != null) {
      userCart.setCouponId(guestCart.getCouponId());
    }

    cartItemRepository.deleteByCartId(guestCart.getId());
    cartRepository.delete(guestCart);

    userCart.setUpdatedAt(Instant.now());
    cartRepository.save(userCart);

    return getCartDto(userCart);
  }
}
