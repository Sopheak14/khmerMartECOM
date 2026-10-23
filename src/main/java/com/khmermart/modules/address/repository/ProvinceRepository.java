package com.khmermart.modules.address.repository;

import com.khmermart.modules.address.entity.Province;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, String> {
  List<Province> findAllByOrderByNameAsc();
}
