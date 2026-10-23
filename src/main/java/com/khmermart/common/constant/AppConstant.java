package com.khmermart.common.constant;

public final class AppConstant {

  private AppConstant() {}

  public static final String REFRESH_TOKEN_COOKIE_NAME = "km_rt";
  public static final String GUEST_CART_COOKIE_NAME = "km_guest";
  public static final String AUTH_COOKIE_PATH = "/api/auth";
  public static final String DEFAULT_CURRENCY = "USD";
  public static final long DEFAULT_USD_KHR_RATE = 4100L;
  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String MDC_REQUEST_ID_KEY = "requestId";
}
