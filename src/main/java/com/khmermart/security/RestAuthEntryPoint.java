package com.khmermart.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.common.api.ApiResponse;
import com.khmermart.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(RestAuthEntryPoint.class);
  public static final String EXPIRED_TOKEN_ATTR = "KH_TOKEN_EXPIRED";
  private final ObjectMapper objectMapper;

  public RestAuthEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    log.warn(
        "RestAuthEntryPoint invoked for uri={}, servletPath={}",
        request.getRequestURI(),
        request.getServletPath());
    Boolean tokenExpired = (Boolean) request.getAttribute(EXPIRED_TOKEN_ATTR);
    ErrorCode errorCode =
        Boolean.TRUE.equals(tokenExpired) ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHORIZED;
    String message =
        Boolean.TRUE.equals(tokenExpired) ? "Token has expired" : "Authentication required";

    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    ApiResponse<Void> body = ApiResponse.error(errorCode, message);
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
