package com.khmermart.service.impl;

import com.khmermart.common.constant.MessageConstant;
import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.mapper.AddressMapper;
import com.khmermart.model.entity.Address;
import com.khmermart.model.request.AddressRequest;
import com.khmermart.model.response.AddressDto;
import com.khmermart.repository.AddressRepository;
import com.khmermart.service.AddressService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AddressServiceImpl implements AddressService {

  private final AddressRepository addressRepository;
  private final AddressMapper addressMapper;

  public AddressServiceImpl(AddressRepository addressRepository, AddressMapper addressMapper) {
    this.addressRepository = addressRepository;
    this.addressMapper = addressMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AddressDto> getAddresses(Long userId) {
    return addressRepository.findByUserIdOrderByIdAsc(userId).stream()
        .map(addressMapper::toDTO)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public AddressDto getAddress(Long userId, Long addressId) {
    Address address =
        addressRepository
            .findByIdAndUserId(addressId, userId)
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, MessageConstant.ADDRESS_NOT_FOUND));
    return addressMapper.toDTO(address);
  }

  @Override
  public AddressDto createAddress(Long userId, AddressRequest request) {
    long existingCount = addressRepository.countByUserId(userId);
    boolean shouldBeDefault = existingCount == 0 || request.isDefault();

    if (shouldBeDefault && existingCount > 0) {
      addressRepository.clearDefaultForUser(userId);
    }

    requireBothCoordinates(request);
    Address address = addressMapper.toEntity(request);
    address.setUserId(userId);
    address.setDefault(shouldBeDefault);

    Address saved = addressRepository.save(address);
    return addressMapper.toDTO(saved);
  }

  @Override
  public AddressDto updateAddress(Long userId, Long addressId, AddressRequest request) {
    Address address =
        addressRepository
            .findByIdAndUserId(addressId, userId)
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, MessageConstant.ADDRESS_NOT_FOUND));

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

    if (request.latitude() != null || request.longitude() != null) {
      requireBothCoordinates(request);
      address.setLatitude(request.latitude());
      address.setLongitude(request.longitude());
    }

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

    Address saved = addressRepository.save(address);
    return addressMapper.toDTO(saved);
  }

  private static void requireBothCoordinates(AddressRequest request) {
    if ((request.latitude() == null) != (request.longitude() == null)) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR,
          "Latitude and longitude must be provided together",
          org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
    }
  }

  @Override
  public void deleteAddress(Long userId, Long addressId) {
    Address address =
        addressRepository
            .findByIdAndUserId(addressId, userId)
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, MessageConstant.ADDRESS_NOT_FOUND));

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
