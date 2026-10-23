package com.khmermart.security;

import com.khmermart.modules.user.model.Role;
import java.io.Serializable;

public record UserPrincipal(Long id, String email, Role role) implements Serializable {}
