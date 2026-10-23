package com.khmermart.mapper;

import com.khmermart.config.MapperConfig;
import com.khmermart.model.entity.Address;
import com.khmermart.model.request.AddressRequest;
import com.khmermart.model.response.AddressDto;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapperConfig.class, builder = @Builder(disableBuilder = true))
public interface AddressMapper {

  @Mapping(target = "isDefault", expression = "java(address.isDefault())")
  AddressDto toDTO(Address address);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "userId", ignore = true)
  @Mapping(target = "default", expression = "java(request.isDefault())")
  Address toEntity(AddressRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "userId", ignore = true)
  @Mapping(target = "default", expression = "java(request.isDefault())")
  void updateEntityFromRequest(AddressRequest request, @MappingTarget Address address);
}
