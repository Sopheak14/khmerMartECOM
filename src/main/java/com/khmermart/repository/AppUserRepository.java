package com.khmermart.repository;

import com.khmermart.model.entity.AppUser;
import com.khmermart.model.enumerate.Roles;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AppUserRepository
    extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {
  Optional<AppUser> findByEmail(String email);

  boolean existsByEmail(String email);

  List<AppUser> findByRoleIn(Collection<Roles> roles);
}
