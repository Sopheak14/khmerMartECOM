package com.khmermart.modules.address.mapper;

import com.khmermart.modules.address.dto.AddressDTO;
import com.khmermart.modules.address.dto.CreateAddressRequest;
import com.khmermart.modules.address.entity.Address;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface AddressMapper {

  @Mapping(target = "isDefault", source = "default")
  AddressDTO toDTO(Address address);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "userId", ignore = true)
  @Mapping(target = "default", expression = "java(Boolean.TRUE.equals(request.isDefault()))")
  Address toEntity(CreateAddressRequest request);
}
