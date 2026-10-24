package com.khmermart.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.khmermart.model.enumerate.Roles;

public record AdminUserUpdateRequest(
    Roles role, @JsonAlias({"active", "isActive", "is_active"}) Boolean isActive) {}
