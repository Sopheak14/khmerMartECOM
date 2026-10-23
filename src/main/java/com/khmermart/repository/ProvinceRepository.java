package com.khmermart.repository;

import com.khmermart.model.entity.Province;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, String> {
  Optional<Province> findByNameIgnoreCase(String name);

  Optional<Province> findByNameKmIgnoreCase(String nameKm);
}
