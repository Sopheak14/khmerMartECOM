package com.khmermart.modules.user.repository;

import com.khmermart.modules.user.entity.User;
import com.khmermart.modules.user.model.Role;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
  Optional<User> findByEmailIgnoreCase(String email);

  default Optional<User> findByEmail(String email) {
    return findByEmailIgnoreCase(email);
  }

  boolean existsByEmailIgnoreCase(String email);

  default boolean existsByEmail(String email) {
    return existsByEmailIgnoreCase(email);
  }

  Page<User> findByRole(Role role, Pageable pageable);
}
