package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.config.AppProperty;
import com.khmermart.model.entity.Category;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.Product;
import com.khmermart.model.entity.ProductImage;
import com.khmermart.model.entity.ProductVariant;
import com.khmermart.model.request.AdminProductCreateRequest;
import com.khmermart.model.request.AdminProductCreateRequest.AdminVariantRequest;
import com.khmermart.model.request.AdminProductUpdateRequest;
import com.khmermart.model.response.CsvImportResultDto;
import com.khmermart.model.response.CsvImportResultDto.CsvRowError;
import com.khmermart.model.response.ProductCardDto;
import com.khmermart.model.response.ProductDetailDto;
import com.khmermart.model.response.ProductImageDto;
import com.khmermart.repository.CategoryRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.OrderItemRepository;
import com.khmermart.repository.ProductImageRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.ProductVariantRepository;
import com.khmermart.service.AdminProductService;
import com.khmermart.service.AuditLogService;
import com.khmermart.service.ProductService;
import java.math.BigDecimal;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminProductServiceImpl implements AdminProductService {

  private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

  private final ProductRepository productRepository;
  private final ProductVariantRepository variantRepository;
  private final ProductImageRepository imageRepository;
  private final InventoryRepository inventoryRepository;
  private final OrderItemRepository orderItemRepository;
  private final CategoryRepository categoryRepository;
  private final ProductService productService;
  private final AuditLogService auditLogService;
  private final AppProperty appProperty;

  public AdminProductServiceImpl(
      ProductRepository productRepository,
      ProductVariantRepository variantRepository,
      ProductImageRepository imageRepository,
      InventoryRepository inventoryRepository,
      OrderItemRepository orderItemRepository,
      CategoryRepository categoryRepository,
      ProductService productService,
      AuditLogService auditLogService,
      AppProperty appProperty) {
    this.productRepository = productRepository;
    this.variantRepository = variantRepository;
    this.imageRepository = imageRepository;
    this.inventoryRepository = inventoryRepository;
    this.orderItemRepository = orderItemRepository;
    this.categoryRepository = categoryRepository;
    this.productService = productService;
    this.auditLogService = auditLogService;
    this.appProperty = appProperty;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ProductCardDto> getProducts(
      String query, Long categoryId, Boolean active, Pageable pageable) {
    Specification<Product> spec = Specification.where(null);

    if (query != null && !query.isBlank()) {
      String term = "%" + query.trim().toLowerCase() + "%";
      spec =
          spec.and(
              (root, q, cb) ->
                  cb.or(
                      cb.like(cb.lower(root.get("name")), term),
                      cb.like(cb.lower(root.get("sku")), term)));
    }
    if (categoryId != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("categoryId"), categoryId));
    }
    if (active != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("active"), active));
    }

    return productRepository
        .findAll(spec, pageable)
        .map(
            p -> {
              String imgUrl =
                  imageRepository.findByProductIdOrderBySortOrderAsc(p.getId()).stream()
                      .findFirst()
                      .map(ProductImage::getUrl)
                      .orElse(null);
              return new ProductCardDto(
                  p.getId(),
                  p.getSku(),
                  p.getSlug(),
                  p.getName(),
                  p.getNameKm(),
                  p.getBrand(),
                  p.getPriceCents(),
                  p.getCompareAtCents(),
                  null,
                  imgUrl,
                  p.getReviewCount() == 0 ? BigDecimal.ZERO : p.getAvgRating(),
                  p.getReviewCount(),
                  0,
                  p.isActive(),
                  List.of());
            });
  }

  @Override
  @Transactional(readOnly = true)
  public ProductDetailDto getProduct(Long id) {
    return productService.getProductDetail(String.valueOf(id));
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"products", "home", "search"},
      allEntries = true)
  public ProductDetailDto createProduct(
      AdminProductCreateRequest request, Long adminId, String ip) {
    String sku =
        (request.sku() != null && !request.sku().isBlank())
            ? request.sku().trim().toUpperCase(Locale.ROOT)
            : "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

    if (productRepository.existsBySku(sku)) {
      throw new CustomException(
          ErrorCode.CONFLICT, "Product with SKU '" + sku + "' already exists");
    }

    String slug =
        (request.slug() != null && !request.slug().isBlank())
            ? request.slug().trim().toLowerCase(Locale.ROOT)
            : request.name().trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");

    if (productRepository.existsBySlug(slug)) {
      slug = slug + "-" + UUID.randomUUID().toString().substring(0, 4);
    }

    Product product =
        Product.builder()
            .name(request.name().trim())
            .nameKm(request.nameKm())
            .sku(sku)
            .slug(slug)
            .description(request.description())
            .brand(request.brand())
            .priceCents(request.priceCents())
            .compareAtCents(request.compareAtCents())
            .categoryId(request.categoryId())
            .active(request.active() != null ? request.active() : true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    Product saved = productRepository.save(product);

    if (request.variants() != null && !request.variants().isEmpty()) {
      for (AdminVariantRequest vReq : request.variants()) {
        String vSku =
            (vReq.sku() != null && !vReq.sku().isBlank())
                ? vReq.sku().trim().toUpperCase(Locale.ROOT)
                : saved.getSku() + "-" + UUID.randomUUID().toString().substring(0, 4);

        ProductVariant variant =
            ProductVariant.builder()
                .productId(saved.getId())
                .sku(vSku)
                .attributes(vReq.attributes() != null ? vReq.attributes() : Map.of())
                .priceCents(vReq.priceCents() != null ? vReq.priceCents() : saved.getPriceCents())
                .active(true)
                .build();
        ProductVariant savedVariant = variantRepository.save(variant);

        int stock = vReq.stock() != null ? vReq.stock() : 0;
        inventoryRepository.save(
            Inventory.builder()
                .productId(saved.getId())
                .variantId(savedVariant.getId())
                .quantityOnHand(stock)
                .quantityReserved(0)
                .lowStockThreshold(5)
                .lowStockAlerted(false)
                .build());
      }
    } else {
      int stock = request.initialStock() != null ? request.initialStock() : 0;
      inventoryRepository.save(
          Inventory.builder()
              .productId(saved.getId())
              .variantId(null)
              .quantityOnHand(stock)
              .quantityReserved(0)
              .lowStockThreshold(5)
              .lowStockAlerted(false)
              .build());
    }

    auditLogService.log(
        adminId, "CREATE_PRODUCT", "PRODUCT", String.valueOf(saved.getId()), null, saved, ip);

    return productService.getProductDetail(String.valueOf(saved.getId()));
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"products", "home", "search"},
      allEntries = true)
  public ProductDetailDto updateProduct(
      Long id, AdminProductUpdateRequest request, Long adminId, String ip) {
    Product product =
        productRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Product not found"));

    Map<String, Object> beforeState =
        Map.of(
            "name", product.getName(),
            "priceCents", product.getPriceCents(),
            "active", product.isActive());

    if (request.name() != null && !request.name().isBlank()) {
      product.setName(request.name().trim());
    }
    if (request.nameKm() != null) {
      product.setNameKm(request.nameKm().trim());
    }
    if (request.sku() != null && !request.sku().isBlank()) {
      String newSku = request.sku().trim().toUpperCase(Locale.ROOT);
      if (!newSku.equals(product.getSku()) && productRepository.existsBySku(newSku)) {
        throw new CustomException(ErrorCode.CONFLICT, "Product with SKU already exists");
      }
      product.setSku(newSku);
    }
    if (request.description() != null) {
      product.setDescription(request.description());
    }
    if (request.brand() != null) {
      product.setBrand(request.brand());
    }
    if (request.priceCents() != null) {
      product.setPriceCents(request.priceCents());
    }
    if (request.compareAtCents() != null) {
      product.setCompareAtCents(request.compareAtCents());
    }
    if (request.categoryId() != null) {
      product.setCategoryId(request.categoryId());
    }
    if (request.active() != null) {
      product.setActive(request.active());
    }

    product.setUpdatedAt(Instant.now());
    Product updated = productRepository.save(product);

    auditLogService.log(
        adminId,
        "UPDATE_PRODUCT",
        "PRODUCT",
        String.valueOf(updated.getId()),
        beforeState,
        updated,
        ip);

    return productService.getProductDetail(String.valueOf(updated.getId()));
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"products", "home", "search"},
      allEntries = true)
  public void deleteProduct(Long id, Long adminId, String ip) {
    Product product =
        productRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Product not found"));

    boolean hasOrders = orderItemRepository.existsByProductId(id);
    if (hasOrders) {
      // Soft delete
      product.setActive(false);
      product.setUpdatedAt(Instant.now());
      productRepository.save(product);

      auditLogService.log(
          adminId,
          "SOFT_DELETE_PRODUCT",
          "PRODUCT",
          String.valueOf(id),
          Map.of("id", id, "name", product.getName(), "active", true),
          Map.of("id", id, "active", false, "reason", "Product has associated orders"),
          ip);
    } else {
      // Hard delete
      imageRepository.deleteByProductId(id);
      productRepository.delete(product);

      auditLogService.log(
          adminId,
          "HARD_DELETE_PRODUCT",
          "PRODUCT",
          String.valueOf(id),
          Map.of("id", id, "name", product.getName()),
          Map.of("deleted", true),
          ip);
    }
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"products", "home", "search"},
      allEntries = true)
  public ProductImageDto uploadImage(Long productId, MultipartFile file, Long adminId, String ip) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Product not found"));

    if (file.isEmpty()) {
      throw new CustomException(ErrorCode.VALIDATION_ERROR, "Uploaded file is empty");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR, "File size exceeds the 5 MB maximum limit");
    }

    String extension = detectImageFormatAndValidate(file);

    try {
      String uploadDir = appProperty.uploadDir() != null ? appProperty.uploadDir() : "uploads";
      Path targetDir = Paths.get(uploadDir, "products");
      Files.createDirectories(targetDir);

      String filename = UUID.randomUUID() + "." + extension;
      Path targetPath = targetDir.resolve(filename);

      try (InputStream is = file.getInputStream()) {
        Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
      }

      int nextOrder = imageRepository.findByProductIdOrderBySortOrderAsc(productId).size() + 1;
      String relativeUrl = "/uploads/products/" + filename;

      ProductImage image =
          ProductImage.builder().productId(productId).url(relativeUrl).sortOrder(nextOrder).build();
      ProductImage savedImage = imageRepository.save(image);

      auditLogService.log(
          adminId,
          "UPLOAD_IMAGE",
          "PRODUCT_IMAGE",
          String.valueOf(savedImage.getId()),
          null,
          Map.of("productId", productId, "url", relativeUrl),
          ip);

      return new ProductImageDto(
          savedImage.getId(),
          savedImage.getProductId(),
          savedImage.getUrl(),
          savedImage.getSortOrder());
    } catch (CustomException ce) {
      throw ce;
    } catch (Exception e) {
      throw new CustomException(
          ErrorCode.INTERNAL_ERROR, "Failed to store image: " + e.getMessage());
    }
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"products", "home", "search"},
      allEntries = true)
  public void reorderImages(Long productId, List<Long> imageIds, Long adminId, String ip) {
    List<ProductImage> images = imageRepository.findByProductIdOrderBySortOrderAsc(productId);
    for (int i = 0; i < imageIds.size(); i++) {
      Long imgId = imageIds.get(i);
      for (ProductImage img : images) {
        if (img.getId().equals(imgId)) {
          img.setSortOrder(i + 1);
          imageRepository.save(img);
          break;
        }
      }
    }

    auditLogService.log(
        adminId,
        "REORDER_IMAGES",
        "PRODUCT_IMAGE",
        String.valueOf(productId),
        null,
        Map.of("productId", productId, "order", imageIds),
        ip);
  }

  @Override
  @Transactional(readOnly = true)
  public byte[] exportProductsCsv() {
    List<Product> products = productRepository.findAll();
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        CSVPrinter printer =
            new CSVPrinter(
                writer,
                CSVFormat.DEFAULT
                    .builder()
                    .setHeader(
                        "ID",
                        "SKU",
                        "Name",
                        "NameKm",
                        "Slug",
                        "Brand",
                        "PriceUSD",
                        "CompareAtUSD",
                        "Category",
                        "Active")
                    .build())) {

      for (Product p : products) {
        String categoryName = "";
        if (p.getCategoryId() != null) {
          categoryName =
              categoryRepository.findById(p.getCategoryId()).map(Category::getName).orElse("");
        }

        String priceUsd = String.format(Locale.US, "%.2f", p.getPriceCents() / 100.0);
        String compareUsd =
            p.getCompareAtCents() != null
                ? String.format(Locale.US, "%.2f", p.getCompareAtCents() / 100.0)
                : "";

        printer.printRecord(
            p.getId(),
            p.getSku(),
            p.getName(),
            p.getNameKm() != null ? p.getNameKm() : "",
            p.getSlug(),
            p.getBrand() != null ? p.getBrand() : "",
            priceUsd,
            compareUsd,
            categoryName,
            p.isActive());
      }
      printer.flush();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new CustomException(
          ErrorCode.INTERNAL_ERROR, "Failed to generate products CSV: " + e.getMessage());
    }
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"products", "home", "search"},
      allEntries = true)
  public CsvImportResultDto importProductsCsv(MultipartFile file, Long adminId, String ip) {
    if (file.isEmpty()) {
      throw new CustomException(ErrorCode.VALIDATION_ERROR, "Uploaded CSV file is empty");
    }

    int imported = 0;
    List<CsvRowError> errors = new ArrayList<>();

    try (BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        CSVParser parser =
            new CSVParser(
                reader,
                CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build())) {

      int rowNumber = 1;
      for (CSVRecord record : parser) {
        rowNumber++;
        String name = record.isSet("Name") ? record.get("Name") : null;
        String sku = record.isSet("SKU") ? record.get("SKU") : null;
        String priceStr = record.isSet("PriceUSD") ? record.get("PriceUSD") : null;

        if (name == null || name.isBlank()) {
          errors.add(new CsvRowError(rowNumber, "Missing required product Name"));
          continue;
        }
        if (sku == null || sku.isBlank()) {
          errors.add(new CsvRowError(rowNumber, "Missing required product SKU"));
          continue;
        }
        if (priceStr == null || priceStr.isBlank()) {
          errors.add(new CsvRowError(rowNumber, "Missing required PriceUSD"));
          continue;
        }

        long priceCents;
        try {
          double price = Double.parseDouble(priceStr);
          if (price < 0) {
            errors.add(new CsvRowError(rowNumber, "PriceUSD cannot be negative"));
            continue;
          }
          priceCents = Math.round(price * 100);
        } catch (NumberFormatException nfe) {
          errors.add(new CsvRowError(rowNumber, "Invalid PriceUSD format: " + priceStr));
          continue;
        }

        String nameKm = record.isSet("NameKm") ? record.get("NameKm") : null;
        String brand = record.isSet("Brand") ? record.get("Brand") : null;

        // Create or update by SKU
        Product product =
            productRepository
                .findBySku(sku.trim().toUpperCase(Locale.ROOT))
                .orElseGet(
                    () -> {
                      String slug =
                          name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                              + "-"
                              + UUID.randomUUID().toString().substring(0, 4);
                      return Product.builder()
                          .sku(sku.trim().toUpperCase(Locale.ROOT))
                          .slug(slug)
                          .build();
                    });

        product.setName(name.trim());
        if (nameKm != null && !nameKm.isBlank()) {
          product.setNameKm(nameKm.trim());
        }
        if (brand != null && !brand.isBlank()) {
          product.setBrand(brand.trim());
        }
        product.setPriceCents(priceCents);
        product.setActive(true);
        product.setUpdatedAt(Instant.now());
        Product saved = productRepository.save(product);

        // Ensure inventory exists
        if (inventoryRepository.findByProductIdAndVariantIdIsNull(saved.getId()).isEmpty()) {
          inventoryRepository.save(
              Inventory.builder()
                  .productId(saved.getId())
                  .variantId(null)
                  .quantityOnHand(10)
                  .quantityReserved(0)
                  .lowStockThreshold(5)
                  .lowStockAlerted(false)
                  .build());
        }

        imported++;
      }

      auditLogService.log(
          adminId,
          "IMPORT_PRODUCTS_CSV",
          "PRODUCT",
          "BATCH",
          null,
          Map.of("importedCount", imported, "errorCount", errors.size()),
          ip);

      return new CsvImportResultDto(imported, errors);
    } catch (Exception e) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR, "Failed to parse CSV file: " + e.getMessage());
    }
  }

  private String detectImageFormatAndValidate(MultipartFile file) {
    try {
      byte[] header = new byte[12];
      int read = file.getInputStream().read(header);
      if (read < 3) {
        throw new CustomException(
            ErrorCode.VALIDATION_ERROR, "Invalid image file. Allowed formats: JPEG, PNG, WEBP");
      }

      // Check DOS/Windows MZ executable header
      if (header[0] == 'M' && header[1] == 'Z') {
        throw new CustomException(
            ErrorCode.VALIDATION_ERROR,
            "Executable files are forbidden. Allowed formats: JPEG, PNG, WEBP");
      }

      // Check JPEG: FF D8 FF
      if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
        return "jpg";
      }

      // Check PNG: 89 50 4E 47 0D 0A 1A 0A
      if (read >= 8
          && (header[0] & 0xFF) == 0x89
          && header[1] == 0x50
          && header[2] == 0x4E
          && header[3] == 0x47
          && header[4] == 0x0D
          && header[5] == 0x0A
          && header[6] == 0x1A
          && header[7] == 0x0A) {
        return "png";
      }

      // Check WEBP: RIFF....WEBP
      if (read >= 12
          && header[0] == 'R'
          && header[1] == 'I'
          && header[2] == 'F'
          && header[3] == 'F'
          && header[8] == 'W'
          && header[9] == 'E'
          && header[10] == 'B'
          && header[11] == 'P') {
        return "webp";
      }

      throw new CustomException(
          ErrorCode.VALIDATION_ERROR, "Invalid image format. Allowed formats: JPEG, PNG, WEBP");
    } catch (CustomException ce) {
      throw ce;
    } catch (Exception e) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR, "Failed to verify image format: " + e.getMessage());
    }
  }
}
