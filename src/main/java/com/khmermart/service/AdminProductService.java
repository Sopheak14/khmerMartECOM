package com.khmermart.service;

import com.khmermart.model.request.AdminProductCreateRequest;
import com.khmermart.model.request.AdminProductUpdateRequest;
import com.khmermart.model.response.CsvImportResultDto;
import com.khmermart.model.response.ProductCardDto;
import com.khmermart.model.response.ProductDetailDto;
import com.khmermart.model.response.ProductImageDto;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface AdminProductService {

  Page<ProductCardDto> getProducts(
      String query, Long categoryId, Boolean active, Pageable pageable);

  ProductDetailDto getProduct(Long id);

  ProductDetailDto createProduct(AdminProductCreateRequest request, Long adminId, String ip);

  ProductDetailDto updateProduct(
      Long id, AdminProductUpdateRequest request, Long adminId, String ip);

  void deleteProduct(Long id, Long adminId, String ip);

  ProductImageDto uploadImage(Long productId, MultipartFile file, Long adminId, String ip);

  void reorderImages(Long productId, List<Long> imageIds, Long adminId, String ip);

  byte[] exportProductsCsv();

  CsvImportResultDto importProductsCsv(MultipartFile file, Long adminId, String ip);
}
