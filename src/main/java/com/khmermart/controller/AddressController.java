package com.khmermart.controller;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.request.AddressRequest;
import com.khmermart.model.response.AddressDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.AddressService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/addresses")
@Tag(name = "Address", description = "User delivery address management")
public class AddressController {

  private final AddressService addressService;

  public AddressController(AddressService addressService) {
    this.addressService = addressService;
  }

  @GetMapping
  @Operation(summary = "List all delivery addresses for current user")
  public SuccessResponse<List<AddressDto>> getAddresses(@CurrentUser UserPrincipal principal) {
    return ApiResponseUtil.success(addressService.getAddresses(principal.id()));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get single address by id")
  public SuccessResponse<AddressDto> getAddress(
      @CurrentUser UserPrincipal principal, @PathVariable Long id) {
    return ApiResponseUtil.success(addressService.getAddress(principal.id(), id));
  }

  @PostMapping
  @Operation(summary = "Create new delivery address")
  public SuccessResponse<AddressDto> createAddress(
      @CurrentUser UserPrincipal principal, @Valid @RequestBody AddressRequest request) {
    return ApiResponseUtil.success(addressService.createAddress(principal.id(), request));
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update delivery address")
  public SuccessResponse<AddressDto> updateAddress(
      @CurrentUser UserPrincipal principal,
      @PathVariable Long id,
      @Valid @RequestBody AddressRequest request) {
    return ApiResponseUtil.success(addressService.updateAddress(principal.id(), id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete delivery address")
  public SuccessResponse<Void> deleteAddress(
      @CurrentUser UserPrincipal principal, @PathVariable Long id) {
    addressService.deleteAddress(principal.id(), id);
    return ApiResponseUtil.success();
  }
}
