package com.khmermart.controller;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.request.AppUserUpdateRequest;
import com.khmermart.model.request.PasswordRequest;
import com.khmermart.model.response.AppUserDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.AppUserService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "User", description = "User profile and credentials")
public class AppUserController {

  private final AppUserService appUserService;

  public AppUserController(AppUserService appUserService) {
    this.appUserService = appUserService;
  }

  @GetMapping("/me")
  @Operation(summary = "Get current authenticated user profile")
  public SuccessResponse<AppUserDto> getMe(@CurrentUser UserPrincipal principal) {
    return ApiResponseUtil.success(appUserService.getProfile(principal.id()));
  }

  @PatchMapping("/me")
  @Operation(summary = "Update current user profile")
  public SuccessResponse<AppUserDto> updateMe(
      @CurrentUser UserPrincipal principal, @Valid @RequestBody AppUserUpdateRequest request) {
    return ApiResponseUtil.success(appUserService.updateProfile(principal.id(), request));
  }

  @PatchMapping("/me/password")
  @Operation(summary = "Change account password")
  public SuccessResponse<Void> changePassword(
      @CurrentUser UserPrincipal principal, @Valid @RequestBody PasswordRequest request) {
    appUserService.changePassword(principal.id(), request);
    return ApiResponseUtil.success();
  }
}
