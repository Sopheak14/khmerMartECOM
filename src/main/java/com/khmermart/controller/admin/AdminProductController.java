package com.khmermart.controller.admin;

import com.khmermart.aspect.Audited;
import com.khmermart.common.response.PaginationResponse;
import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.request.AdminProductCreateRequest;
import com.khmermart.model.request.AdminProductUpdateRequest;
import com.khmermart.model.request.ReorderImagesRequest;
import com.khmermart.model.response.CsvImportResultDto;
import com.khmermart.model.response.ProductCardDto;
import com.khmermart.model.response.ProductDetailDto;
import com.khmermart.model.response.ProductImageDto;
import com.khmermart.security.CurrentUser;
import com.khmermart.security.UserPrincipal;
import com.khmermart.service.AdminProductService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/products")
@Tag(
    name = "Admin Products",
    description = "Product catalog CRUD, image uploads, CSV import/export")
public class AdminProductController {

  private final AdminProductService adminProductService;

  public AdminProductController(AdminProductService adminProductService) {
    this.adminProductService = adminProductService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "Search and filter products with pagination")
  public SuccessResponse<List<ProductCardDto>> getProducts(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long category,
      @RequestParam(required = false) Boolean active,
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    Page<ProductCardDto> page = adminProductService.getProducts(q, category, active, pageable);
    PaginationResponse meta =
        new PaginationResponse(
            page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    return ApiResponseUtil.success(page.getContent(), meta);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "Get full product detail by ID")
  public SuccessResponse<ProductDetailDto> getProduct(@PathVariable Long id) {
    ProductDetailDto dto = adminProductService.getProduct(id);
    return ApiResponseUtil.success(dto);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "CREATE_PRODUCT", entity = "PRODUCT")
  @Operation(summary = "Create product with variants, Khmer name and inventory")
  public SuccessResponse<ProductDetailDto> createProduct(
      @Valid @RequestBody AdminProductCreateRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    ProductDetailDto dto = adminProductService.createProduct(request, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "UPDATE_PRODUCT", entity = "PRODUCT")
  @Operation(summary = "Update product details")
  public SuccessResponse<ProductDetailDto> updateProduct(
      @PathVariable Long id,
      @Valid @RequestBody AdminProductUpdateRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    ProductDetailDto dto = adminProductService.updateProduct(id, request, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "DELETE_PRODUCT", entity = "PRODUCT")
  @Operation(summary = "Delete product (soft delete if it has orders, else hard delete)")
  public SuccessResponse<Map<String, Object>> deleteProduct(
      @PathVariable Long id,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    adminProductService.deleteProduct(id, adminId, ip);
    return ApiResponseUtil.success(Map.of());
  }

  @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "UPLOAD_IMAGE", entity = "PRODUCT_IMAGE")
  @Operation(summary = "Upload product image (JPG, PNG, WEBP <= 5MB with magic bytes check)")
  public SuccessResponse<ProductImageDto> uploadImage(
      @PathVariable Long id,
      @RequestPart("file") MultipartFile file,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    ProductImageDto dto = adminProductService.uploadImage(id, file, adminId, ip);
    return ApiResponseUtil.success(dto);
  }

  @PatchMapping("/{id}/images/order")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "REORDER_IMAGES", entity = "PRODUCT_IMAGE")
  @Operation(summary = "Reorder product images")
  public SuccessResponse<Map<String, Object>> reorderImages(
      @PathVariable Long id,
      @Valid @RequestBody ReorderImagesRequest request,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    adminProductService.reorderImages(id, request.ids(), adminId, ip);
    return ApiResponseUtil.success(Map.of());
  }

  @GetMapping(value = "/export.csv", produces = "text/csv")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Operation(summary = "Export all products as CSV")
  public ResponseEntity<byte[]> exportProductsCsv() {
    byte[] data = adminProductService.exportProductsCsv();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"products.csv\"")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(data);
  }

  @PostMapping(
      value = {"/import", "/import.csv"},
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @Audited(action = "IMPORT_PRODUCTS", entity = "PRODUCT")
  @Operation(summary = "Import products from CSV with per-row validation reporting")
  public SuccessResponse<CsvImportResultDto> importProducts(
      @RequestPart("file") MultipartFile file,
      @CurrentUser UserPrincipal principal,
      HttpServletRequest servletRequest) {
    String ip = resolveIp(servletRequest);
    Long adminId = principal != null ? principal.id() : null;
    CsvImportResultDto result = adminProductService.importProductsCsv(file, adminId, ip);
    return ApiResponseUtil.success(result);
  }

  private String resolveIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
