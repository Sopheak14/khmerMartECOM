package com.khmermart.modules.address.controller;

import com.khmermart.common.api.ApiResponse;
import com.khmermart.common.web.CurrentUser;
import com.khmermart.modules.address.dto.AddressDTO;
import com.khmermart.modules.address.dto.CreateAddressRequest;
import com.khmermart.modules.address.dto.UpdateAddressRequest;
import com.khmermart.modules.address.service.AddressService;
import com.khmermart.security.UserPrincipal;
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
@Tag(name = "Address", description = "User address book management")
public class AddressController {

  private final AddressService addressService;

  public AddressController(AddressService addressService) {
    this.addressService = addressService;
  }

  @GetMapping
  @Operation(summary = "List current user addresses")
  public ApiResponse<List<AddressDTO>> getAddresses(@CurrentUser UserPrincipal principal) {
    return ApiResponse.ok(addressService.getAddresses(principal.id()));
  }

  @PostMapping
  @Operation(summary = "Add a new address to user address book")
  public ApiResponse<AddressDTO> createAddress(
      @CurrentUser UserPrincipal principal, @Valid @RequestBody CreateAddressRequest request) {
    return ApiResponse.ok(addressService.createAddress(principal.id(), request));
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update an existing address")
  public ApiResponse<AddressDTO> updateAddress(
      @CurrentUser UserPrincipal principal,
      @PathVariable Long id,
      @Valid @RequestBody UpdateAddressRequest request) {
    return ApiResponse.ok(addressService.updateAddress(principal.id(), id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete an address")
  public ApiResponse<Void> deleteAddress(
      @CurrentUser UserPrincipal principal, @PathVariable Long id) {
    addressService.deleteAddress(principal.id(), id);
    return ApiResponse.ok();
  }
}
