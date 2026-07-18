package com.nocteon.nocteon_api.auth.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.nocteon.nocteon_api.auth.dto.response.UserResponse;
import com.nocteon.nocteon_api.auth.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(source = "profile.firstName", target = "firstName")
    @Mapping(source = "profile.lastName", target = "lastName")
    @Mapping(source = "profile.avatarUrl", target = "avatarUrl")
    UserResponse toResponse(User user);
}