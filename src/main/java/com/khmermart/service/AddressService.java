package com.khmermart.service;

import com.khmermart.model.request.AddressRequest;
import com.khmermart.model.response.AddressDto;
import java.util.List;

public interface AddressService {

  List<AddressDto> getAddresses(Long userId);

  AddressDto getAddress(Long userId, Long addressId);

  AddressDto createAddress(Long userId, AddressRequest request);

  AddressDto updateAddress(Long userId, Long addressId, AddressRequest request);

  void deleteAddress(Long userId, Long addressId);
}
