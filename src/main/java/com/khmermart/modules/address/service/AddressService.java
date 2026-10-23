package com.khmermart.modules.address.service;

import com.khmermart.common.api.ApiException;
import com.khmermart.common.api.ErrorCode;
import com.khmermart.modules.address.dto.AddressDTO;
import com.khmermart.modules.address.dto.CreateAddressRequest;
import com.khmermart.modules.address.dto.UpdateAddressRequest;
import com.khmermart.modules.address.entity.Address;
import com.khmermart.modules.address.mapper.AddressMapper;
import com.khmermart.modules.address.repository.AddressRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AddressService {

  private final AddressRepository addressRepository;
  private final AddressMapper addressMapper;

  public AddressService(AddressRepository addressRepository, AddressMapper addressMapper) {
    this.addressRepository = addressRepository;
    this.addressMapper = addressMapper;
  }

  @Transactional(readOnly = true)
  public List<AddressDTO> getAddresses(Long userId) {
    return addressRepository.findByUserIdOrderByIdAsc(userId).stream()
        .map(addressMapper::toDTO)
        .toList();
  }

  public AddressDTO createAddress(Long userId, CreateAddressRequest request) {
    long existingCount = addressRepository.countByUserId(userId);
    boolean shouldBeDefault = existingCount == 0 || Boolean.TRUE.equals(request.isDefault());

    if (shouldBeDefault && existingCount > 0) {
      addressRepository.clearDefaultForUser(userId);
    }

    Address address = addressMapper.toEntity(request);
    address.setUserId(userId);
    address.setDefault(shouldBeDefault);

    Address saved = addressRepository.save(address);
    return addressMapper.toDTO(saved);
  }

  public AddressDTO updateAddress(Long userId, Long addressId, UpdateAddressRequest request) {
    Address address =
        addressRepository
            .findByIdAndUserId(addressId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Address not found"));

    if (request.label() != null) {
      address.setLabel(request.label().trim());
    }
    if (request.recipientName() != null) {
      address.setRecipientName(request.recipientName().trim());
    }
    if (request.phone() != null) {
      address.setPhone(request.phone().trim());
    }
    if (request.line1() != null) {
      address.setLine1(request.line1().trim());
    }
    if (request.line2() != null) {
      address.setLine2(request.line2().trim());
    }
    if (request.district() != null) {
      address.setDistrict(request.district().trim());
    }
    if (request.province() != null) {
      address.setProvince(request.province().trim());
    }
    if (request.postalCode() != null) {
      address.setPostalCode(request.postalCode().trim());
    }

    if (request.isDefault() != null) {
      if (request.isDefault()) {
        addressRepository.clearDefaultForUser(userId);
        address.setDefault(true);
      } else if (address.isDefault()) {
        List<Address> others =
            addressRepository.findByUserIdOrderByIdAsc(userId).stream()
                .filter(a -> !a.getId().equals(addressId))
                .toList();
        if (others.isEmpty()) {
          address.setDefault(true); // Must remain default if it's the only one
        } else {
          others.get(0).setDefault(true);
          addressRepository.save(others.get(0));
          address.setDefault(false);
        }
      }
    }

    Address saved = addressRepository.save(address);
    return addressMapper.toDTO(saved);
  }

  public void deleteAddress(Long userId, Long addressId) {
    Address address =
        addressRepository
            .findByIdAndUserId(addressId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Address not found"));

    boolean wasDefault = address.isDefault();
    addressRepository.delete(address);

    if (wasDefault) {
      List<Address> remaining = addressRepository.findByUserIdOrderByIdAsc(userId);
      if (!remaining.isEmpty()) {
        remaining.get(0).setDefault(true);
        addressRepository.save(remaining.get(0));
      }
    }
  }
}
