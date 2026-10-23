package com.khmermart.mapper;

import com.khmermart.config.MapperConfig;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.response.AppUserDto;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfig.class, builder = @Builder(disableBuilder = true))
public interface AppUserMapper {

  @Mapping(target = "emailVerified", expression = "java(user.isEmailVerified())")
  @Mapping(target = "isActive", expression = "java(user.isActive())")
  AppUserDto toDTO(AppUser user);
}
