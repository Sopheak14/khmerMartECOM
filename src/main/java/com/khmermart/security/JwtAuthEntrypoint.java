package com.khmermart.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.common.constant.MessageConstant;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.common.response.ErrorResponse;
import com.khmermart.util.ApiResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthEntrypoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  public JwtAuthEntrypoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {

    String expiredHeader = (String) request.getAttribute("TOKEN_EXPIRED");
    ErrorCode errorCode =
        "true".equalsIgnoreCase(expiredHeader) ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHORIZED;
    String message =
        "true".equalsIgnoreCase(expiredHeader)
            ? ErrorCode.TOKEN_EXPIRED.getDefaultMessage()
            : MessageConstant.AUTH_REQUIRED;

    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    ErrorResponse body = ApiResponseUtil.error(errorCode, message);
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
