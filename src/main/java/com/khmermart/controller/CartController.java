package com.khmermart.controller;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.entity.Cart;
import com.khmermart.model.request.ApplyCouponRequest;
import com.khmermart.model.request.CartItemRequest;
import com.khmermart.model.request.UpdateCartItemRequest;
import com.khmermart.model.response.CartDto;
import com.khmermart.security.CookieUtil;
import com.khmermart.security.CurrentUserService;
import com.khmermart.service.CartService;
import com.khmermart.util.ApiResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cart")
public class CartController {

  private final CartService cartService;
  private final CurrentUserService currentUserService;
  private final CookieUtil cookieUtil;

  public CartController(
      CartService cartService, CurrentUserService currentUserService, CookieUtil cookieUtil) {
    this.cartService = cartService;
    this.currentUserService = currentUserService;
    this.cookieUtil = cookieUtil;
  }

  private Cart resolveCart(HttpServletRequest request, HttpServletResponse response) {
    Optional<Long> userIdOpt = currentUserService.getCurrentUserId();
    if (userIdOpt.isPresent()) {
      return cartService.getOrCreateCart(userIdOpt.get(), null);
    }

    Optional<String> guestTokenOpt = cookieUtil.getGuestTokenFromCookie(request);
    String guestToken;
    if (guestTokenOpt.isPresent() && !guestTokenOpt.get().isBlank()) {
      guestToken = guestTokenOpt.get();
    } else {
      guestToken = UUID.randomUUID().toString();
      ResponseCookie cookie = cookieUtil.createGuestCartCookie(guestToken);
      response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
    return cartService.getOrCreateCart(null, guestToken);
  }

  @GetMapping
  public ResponseEntity<SuccessResponse<CartDto>> getCart(
      HttpServletRequest request, HttpServletResponse response) {
    Cart cart = resolveCart(request, response);
    return ResponseEntity.ok(ApiResponseUtil.success(cartService.getCartDto(cart)));
  }

  @PostMapping("/items")
  public ResponseEntity<SuccessResponse<CartDto>> addItem(
      @Valid @RequestBody CartItemRequest itemRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    Cart cart = resolveCart(request, response);
    return ResponseEntity.ok(ApiResponseUtil.success(cartService.addItem(cart, itemRequest)));
  }

  @PatchMapping("/items/{id}")
  public ResponseEntity<SuccessResponse<CartDto>> updateItem(
      @PathVariable("id") Long itemId,
      @Valid @RequestBody UpdateCartItemRequest itemRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    Cart cart = resolveCart(request, response);
    return ResponseEntity.ok(
        ApiResponseUtil.success(cartService.updateItem(cart, itemId, itemRequest)));
  }

  @DeleteMapping("/items/{id}")
  public ResponseEntity<SuccessResponse<CartDto>> removeItem(
      @PathVariable("id") Long itemId, HttpServletRequest request, HttpServletResponse response) {
    Cart cart = resolveCart(request, response);
    return ResponseEntity.ok(ApiResponseUtil.success(cartService.removeItem(cart, itemId)));
  }

  @DeleteMapping
  public ResponseEntity<SuccessResponse<CartDto>> clearCart(
      HttpServletRequest request, HttpServletResponse response) {
    Cart cart = resolveCart(request, response);
    return ResponseEntity.ok(ApiResponseUtil.success(cartService.clearCart(cart)));
  }

  @PostMapping("/coupon")
  public ResponseEntity<SuccessResponse<CartDto>> applyCoupon(
      @Valid @RequestBody ApplyCouponRequest couponRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    Cart cart = resolveCart(request, response);
    return ResponseEntity.ok(
        ApiResponseUtil.success(cartService.applyCoupon(cart, couponRequest.code())));
  }

  @DeleteMapping("/coupon")
  public ResponseEntity<SuccessResponse<CartDto>> removeCoupon(
      HttpServletRequest request, HttpServletResponse response) {
    Cart cart = resolveCart(request, response);
    return ResponseEntity.ok(ApiResponseUtil.success(cartService.removeCoupon(cart)));
  }

  @PostMapping("/merge")
  public ResponseEntity<SuccessResponse<CartDto>> mergeCart(
      HttpServletRequest request, HttpServletResponse response) {
    Long userId =
        currentUserService
            .getCurrentUserId()
            .orElseThrow(
                () -> new CustomException(ErrorCode.UNAUTHORIZED, "Authentication required"));

    String guestToken = cookieUtil.getGuestTokenFromCookie(request).orElse(null);
    CartDto mergedCart = cartService.mergeGuestCart(userId, guestToken);

    ResponseCookie clearCookie = cookieUtil.deleteGuestCartCookie();
    response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());

    return ResponseEntity.ok(ApiResponseUtil.success(mergedCart));
  }
}
