package com.khmermart.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

  public Optional<UserPrincipal> getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
      return Optional.of(principal);
    }
    return Optional.empty();
  }

  public Optional<Long> getCurrentUserId() {
    return getCurrentUser().map(UserPrincipal::id);
  }
}
