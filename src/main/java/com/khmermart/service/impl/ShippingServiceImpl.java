package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.model.entity.ShippingMethod;
import com.khmermart.model.response.ProvinceDto;
import com.khmermart.model.response.ShippingMethodDto;
import com.khmermart.repository.ProvinceRepository;
import com.khmermart.repository.ShippingMethodRepository;
import com.khmermart.service.ShippingService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingServiceImpl implements ShippingService {

  private final ShippingMethodRepository shippingMethodRepository;
  private final ProvinceRepository provinceRepository;

  public ShippingServiceImpl(
      ShippingMethodRepository shippingMethodRepository, ProvinceRepository provinceRepository) {
    this.shippingMethodRepository = shippingMethodRepository;
    this.provinceRepository = provinceRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ShippingMethodDto> getShippingMethods() {
    return shippingMethodRepository.findAllByOrderByFeeCentsAsc().stream()
        .map(this::toDto)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProvinceDto> getProvinces() {
    return provinceRepository.findAllByOrderByCodeAsc().stream()
        .map(p -> new ProvinceDto(p.getCode(), p.getName(), p.getNameKm()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ShippingMethod getShippingMethodEntity(String code) {
    return shippingMethodRepository
        .findByCode(code)
        .orElseThrow(
            () ->
                new CustomException(
                    ErrorCode.NOT_FOUND, "Shipping method not found with code: " + code));
  }

  @Override
  public ShippingMethodDto toDto(ShippingMethod entity) {
    return new ShippingMethodDto(
        entity.getCode(),
        entity.getName(),
        entity.getNameKm(),
        entity.getFeeCents(),
        entity.getFreeOverCents(),
        entity.getEtaDaysMin(),
        entity.getEtaDaysMax(),
        entity.isPhnomPenhOnly());
  }
}
