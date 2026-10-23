package com.khmermart.modules.user.controller;

import com.khmermart.common.api.ApiResponse;
import com.khmermart.common.web.CurrentUser;
import com.khmermart.modules.user.dto.ChangePasswordRequest;
import com.khmermart.modules.user.dto.UpdateProfileRequest;
import com.khmermart.modules.user.dto.UserDTO;
import com.khmermart.modules.user.service.UserService;
import com.khmermart.security.UserPrincipal;
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
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  @Operation(summary = "Get current authenticated user profile")
  public ApiResponse<UserDTO> getMe(@CurrentUser UserPrincipal principal) {
    return ApiResponse.ok(userService.getProfile(principal.id()));
  }

  @PatchMapping("/me")
  @Operation(summary = "Update current user profile")
  public ApiResponse<UserDTO> updateMe(
      @CurrentUser UserPrincipal principal, @Valid @RequestBody UpdateProfileRequest request) {
    return ApiResponse.ok(userService.updateProfile(principal.id(), request));
  }

  @PatchMapping("/me/password")
  @Operation(summary = "Change account password")
  public ApiResponse<Void> changePassword(
      @CurrentUser UserPrincipal principal, @Valid @RequestBody ChangePasswordRequest request) {
    userService.changePassword(principal.id(), request);
    return ApiResponse.ok();
  }
}
