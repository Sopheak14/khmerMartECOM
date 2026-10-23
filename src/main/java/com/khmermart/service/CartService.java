package com.khmermart.service;

import com.khmermart.model.entity.Cart;
import com.khmermart.model.request.CartItemRequest;
import com.khmermart.model.request.UpdateCartItemRequest;
import com.khmermart.model.response.CartDto;

public interface CartService {

  Cart getOrCreateCart(Long userId, String guestToken);

  CartDto getCartDto(Cart cart);

  CartDto addItem(Cart cart, CartItemRequest request);

  CartDto updateItem(Cart cart, Long itemId, UpdateCartItemRequest request);

  CartDto removeItem(Cart cart, Long itemId);

  CartDto clearCart(Cart cart);

  CartDto applyCoupon(Cart cart, String couponCode);

  CartDto removeCoupon(Cart cart);

  CartDto mergeGuestCart(Long userId, String guestToken);
}
