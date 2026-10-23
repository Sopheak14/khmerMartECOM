package com.khmermart.modules.user.mapper;

import com.khmermart.modules.user.dto.UserDTO;
import com.khmermart.modules.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

  @Mapping(target = "emailVerified", expression = "java(user.getEmailVerifiedAt() != null)")
  @Mapping(target = "isActive", source = "active")
  UserDTO toDTO(User user);
}
