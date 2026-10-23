package com.khmermart.service;

import com.khmermart.model.entity.ShippingMethod;
import com.khmermart.model.response.ProvinceDto;
import com.khmermart.model.response.ShippingMethodDto;
import java.util.List;

public interface ShippingService {
  List<ShippingMethodDto> getShippingMethods();

  List<ProvinceDto> getProvinces();

  ShippingMethod getShippingMethodEntity(String code);

  ShippingMethodDto toDto(ShippingMethod entity);
}
