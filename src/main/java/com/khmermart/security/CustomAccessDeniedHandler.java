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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {

    response.setStatus(ErrorCode.FORBIDDEN.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    ErrorResponse body = ApiResponseUtil.error(ErrorCode.FORBIDDEN, MessageConstant.ACCESS_DENIED);
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
