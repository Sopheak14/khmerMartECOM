package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.common.response.PaginationResponse;
import com.khmermart.model.entity.Category;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.Product;
import com.khmermart.model.entity.ProductImage;
import com.khmermart.model.entity.ProductVariant;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.PromotionProduct;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.model.response.CategoryDto;
import com.khmermart.model.response.FacetsDto;
import com.khmermart.model.response.ImageDto;
import com.khmermart.model.response.ProductCardDto;
import com.khmermart.model.response.ProductDetailDto;
import com.khmermart.model.response.VariantDto;
import com.khmermart.repository.CategoryRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.ProductImageRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.ProductVariantRepository;
import com.khmermart.repository.PromotionProductRepository;
import com.khmermart.repository.PromotionRepository;
import com.khmermart.repository.ReviewRepository;
import com.khmermart.service.CategoryService;
import com.khmermart.service.ProductService;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

  private final ProductRepository productRepository;
  private final ProductVariantRepository productVariantRepository;
  private final ProductImageRepository productImageRepository;
  private final InventoryRepository inventoryRepository;
  private final CategoryRepository categoryRepository;
  private final PromotionRepository promotionRepository;
  private final PromotionProductRepository promotionProductRepository;
  private final ReviewRepository reviewRepository;
  private final CategoryService categoryService;

  public ProductServiceImpl(
      ProductRepository productRepository,
      ProductVariantRepository productVariantRepository,
      ProductImageRepository productImageRepository,
      InventoryRepository inventoryRepository,
      CategoryRepository categoryRepository,
      PromotionRepository promotionRepository,
      PromotionProductRepository promotionProductRepository,
      ReviewRepository reviewRepository,
      CategoryService categoryService) {
    this.productRepository = productRepository;
    this.productVariantRepository = productVariantRepository;
    this.productImageRepository = productImageRepository;
    this.inventoryRepository = inventoryRepository;
    this.categoryRepository = categoryRepository;
    this.promotionRepository = promotionRepository;
    this.promotionProductRepository = promotionProductRepository;
    this.reviewRepository = reviewRepository;
    this.categoryService = categoryService;
  }

  @Override
  public ProductListResult getProducts(
      String q,
      Long categoryId,
      String brand,
      Long minPrice,
      Long maxPrice,
      Boolean inStock,
      String sort,
      Map<String, String> attributes,
      int page,
      int limit) {

    int pageNum = Math.max(page, 1);
    int pageSize = limit > 0 ? limit : 20;

    Long minPriceCents = null;
    if (minPrice != null) {
      minPriceCents = minPrice < 10000 ? minPrice * 100 : minPrice;
    }
    Long maxPriceCents = null;
    if (maxPrice != null) {
      maxPriceCents = maxPrice < 10000 ? maxPrice * 100 : maxPrice;
    }

    // Resolve category hierarchy
    List<Long> targetCategoryIds = null;
    if (categoryId != null) {
      targetCategoryIds = new ArrayList<>();
      targetCategoryIds.add(categoryId);
      collectDescendantCategoryIds(categoryId, targetCategoryIds);
    }

    Specification<Product> spec =
        buildSpecification(q, targetCategoryIds, brand, minPriceCents, maxPriceCents, attributes);
    Sort sortObj = resolveSort(sort);
    PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, sortObj);

    Page<Product> productPage = productRepository.findAll(spec, pageRequest);
    List<Product> products = productPage.getContent();

    List<ProductCardDto> cardDtos = mapToCards(products);
    if (Boolean.TRUE.equals(inStock)) {
      cardDtos = cardDtos.stream().filter(ProductCardDto::inStock).toList();
    }

    PaginationResponse meta =
        new PaginationResponse(
            pageNum, pageSize, productPage.getTotalElements(), productPage.getTotalPages());

    FacetsDto facets = computeFacets(targetCategoryIds, q);

    return new ProductListResult(cardDtos, meta, facets);
  }

  @Override
  public ProductDetailDto getProductDetail(String idOrSlug) {
    Product product;
    if (idOrSlug.matches("^\\d+$")) {
      Long id = Long.parseLong(idOrSlug);
      product =
          productRepository
              .findById(id)
              .filter(Product::isActive)
              .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Product not found"));
    } else {
      product =
          productRepository
              .findBySlug(idOrSlug)
              .filter(Product::isActive)
              .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "Product not found"));
    }

    Long productId = product.getId();

    // 1. Images
    List<ProductImage> images =
        productImageRepository.findByProductIdOrderBySortOrderAsc(productId);
    List<ImageDto> imageDtos =
        images.stream()
            .map(img -> new ImageDto(img.getId(), img.getUrl(), img.getSortOrder()))
            .toList();
    String primaryImageUrl =
        !imageDtos.isEmpty() ? imageDtos.get(0).url() : "https://via.placeholder.com/600";

    // 2. Variants and Inventory
    List<ProductVariant> variants =
        productVariantRepository.findByProductIdAndActiveTrue(productId);
    List<Inventory> inventories = inventoryRepository.findByProductId(productId);
    Map<Long, Integer> variantAvailableMap = new HashMap<>();

    for (Inventory inv : inventories) {
      int avail = Math.max(0, inv.getQuantityOnHand() - inv.getQuantityReserved());
      if (inv.getVariantId() != null) {
        variantAvailableMap.put(inv.getVariantId(), avail);
      }
    }

    int totalAvailable;
    if (!variants.isEmpty()) {
      totalAvailable = variantAvailableMap.values().stream().mapToInt(Integer::intValue).sum();
    } else {
      totalAvailable =
          inventories.stream()
              .filter(inv -> inv.getVariantId() == null)
              .mapToInt(inv -> Math.max(0, inv.getQuantityOnHand() - inv.getQuantityReserved()))
              .sum();
    }

    List<VariantDto> variantDtos =
        variants.stream()
            .map(
                v ->
                    new VariantDto(
                        v.getId(),
                        v.getSku(),
                        v.getAttributes(),
                        v.getPriceCents() != null ? v.getPriceCents() : product.getPriceCents(),
                        variantAvailableMap.getOrDefault(v.getId(), 0)))
            .toList();

    // 3. Variant Options aggregation
    Map<String, List<String>> variantOptions = new LinkedHashMap<>();
    for (ProductVariant v : variants) {
      if (v.getAttributes() != null) {
        v.getAttributes()
            .forEach(
                (key, val) -> variantOptions.computeIfAbsent(key, k -> new ArrayList<>()).add(val));
      }
    }
    variantOptions.replaceAll((k, list) -> list.stream().distinct().toList());

    // 4. Rating Breakdown
    Map<String, Long> ratingBreakdown = new LinkedHashMap<>();
    for (int star = 5; star >= 1; star--) {
      ratingBreakdown.put(String.valueOf(star), 0L);
    }
    List<Object[]> reviewCounts = reviewRepository.countRatingsByProduct(productId);
    for (Object[] row : reviewCounts) {
      Integer rating = (Integer) row[0];
      Long count = (Long) row[1];
      if (rating != null) {
        ratingBreakdown.put(String.valueOf(rating), count);
      }
    }

    // 5. Flash sale check
    Instant now = Instant.now();
    Instant flashSaleEndsAt = null;
    Long salePriceCents = null;
    Optional<Promotion> flashPromo =
        promotionRepository.findFirstActiveByType(PromotionType.FLASH_SALE, now);
    if (flashPromo.isPresent()) {
      Optional<PromotionProduct> pp =
          promotionProductRepository.findById(
              new PromotionProduct.PromotionProductId(flashPromo.get().getId(), productId));
      if (pp.isPresent()) {
        flashSaleEndsAt = flashPromo.get().getEndsAt();
        salePriceCents = pp.get().getSalePriceCents();
      }
    }

    // 6. Category
    CategoryDto categoryDto = categoryService.getCategoryById(product.getCategoryId());

    // 7. Badges
    java.util.Set<Long> top8PopularIds =
        productRepository.findTop8ByActiveTrueOrderBySoldCountDesc().stream()
            .map(Product::getId)
            .collect(Collectors.toSet());

    java.util.Set<Long> bogoProductIds = new java.util.HashSet<>();
    Optional<Promotion> bogoPromo =
        promotionRepository.findFirstActiveByType(PromotionType.BOGO, now);
    if (bogoPromo.isPresent()) {
      List<PromotionProduct> bogoPps =
          promotionProductRepository.findByPromotionId(bogoPromo.get().getId());
      for (PromotionProduct pp : bogoPps) {
        bogoProductIds.add(pp.getProductId());
      }
    }

    List<String> badges = new ArrayList<>();
    if (top8PopularIds.contains(product.getId())) {
      badges.add("POPULAR");
    }
    if (Duration.between(product.getCreatedAt(), now).toDays() <= 14) {
      badges.add("NEW");
    }
    if (flashSaleEndsAt != null) {
      badges.add("FLASH_SALE");
    }
    if (totalAvailable <= 5) {
      badges.add("LOW_STOCK");
    }
    if (bogoProductIds.contains(product.getId())) {
      badges.add("BOGO");
    }

    // 8. Related products in same category
    List<Product> relatedProducts =
        productRepository
            .findTop10ByCategoryIdAndActiveTrueOrderBySoldCountDesc(product.getCategoryId())
            .stream()
            .filter(p -> !p.getId().equals(productId))
            .limit(4)
            .toList();
    List<ProductCardDto> relatedDtos = mapToCards(relatedProducts);

    return new ProductDetailDto(
        product.getId(),
        product.getSku(),
        product.getSlug(),
        product.getName(),
        product.getNameKm(),
        product.getBrand(),
        product.getPriceCents(),
        product.getCompareAtCents(),
        salePriceCents,
        primaryImageUrl,
        product.getAvgRating(),
        product.getReviewCount(),
        totalAvailable,
        totalAvailable > 0,
        badges,
        product.getDescription(),
        categoryDto,
        imageDtos,
        variantDtos,
        variantOptions,
        ratingBreakdown,
        flashSaleEndsAt,
        relatedDtos);
  }

  @Override
  public List<ProductCardDto> getBestSellers(int limit) {
    List<Product> products = productRepository.findTop10ByActiveTrueOrderBySoldCountDesc();
    return mapToCards(products.stream().limit(limit).toList());
  }

  @Override
  public List<ProductCardDto> getNewArrivals(int limit) {
    List<Product> products = productRepository.findTop10ByActiveTrueOrderByCreatedAtDesc();
    return mapToCards(products.stream().limit(limit).toList());
  }

  private Specification<Product> buildSpecification(
      String q,
      List<Long> categoryIds,
      String brand,
      Long minPriceCents,
      Long maxPriceCents,
      Map<String, String> attributes) {

    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isTrue(root.get("active")));

      if (categoryIds != null && !categoryIds.isEmpty()) {
        predicates.add(root.get("categoryId").in(categoryIds));
      }

      if (brand != null && !brand.isBlank()) {
        predicates.add(cb.equal(cb.lower(root.get("brand")), brand.trim().toLowerCase()));
      }

      if (minPriceCents != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("priceCents"), minPriceCents));
      }

      if (maxPriceCents != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("priceCents"), maxPriceCents));
      }

      if (q != null && !q.isBlank()) {
        String likePattern = "%" + q.trim().toLowerCase() + "%";
        Predicate nameMatch = cb.like(cb.lower(root.get("name")), likePattern);
        Predicate nameKmMatch = cb.like(cb.lower(cb.coalesce(root.get("nameKm"), "")), likePattern);
        Predicate brandMatch = cb.like(cb.lower(cb.coalesce(root.get("brand"), "")), likePattern);
        Predicate descMatch =
            cb.like(cb.lower(cb.coalesce(root.get("description"), "")), likePattern);
        Predicate skuMatch = cb.like(cb.lower(root.get("sku")), likePattern);
        predicates.add(cb.or(nameMatch, nameKmMatch, brandMatch, descMatch, skuMatch));
      }

      if (attributes != null && !attributes.isEmpty()) {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
          String attrKey = entry.getKey();
          String attrVal = entry.getValue();

          Subquery<Long> subquery = query.subquery(Long.class);
          Root<ProductVariant> variantRoot = subquery.from(ProductVariant.class);
          subquery.select(variantRoot.get("productId"));

          Predicate matchProd = cb.equal(variantRoot.get("productId"), root.get("id"));
          Predicate activeVar = cb.isTrue(variantRoot.get("active"));
          Predicate matchAttr =
              cb.equal(
                  cb.function(
                      "jsonb_extract_path_text",
                      String.class,
                      variantRoot.get("attributes"),
                      cb.literal(attrKey)),
                  attrVal);

          subquery.where(cb.and(matchProd, activeVar, matchAttr));
          predicates.add(cb.exists(subquery));
        }
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private Sort resolveSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return Sort.by(Sort.Direction.DESC, "createdAt");
    }
    return switch (sort.toLowerCase().trim()) {
      case "price_asc" -> Sort.by(Sort.Direction.ASC, "priceCents");
      case "price_desc" -> Sort.by(Sort.Direction.DESC, "priceCents");
      case "popular" -> Sort.by(Sort.Direction.DESC, "soldCount");
      case "rating" -> Sort.by(Sort.Direction.DESC, "avgRating");
      case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
      default -> Sort.by(Sort.Direction.DESC, "createdAt");
    };
  }

  @Override
  public List<ProductCardDto> getFlashSaleCards(List<Long> productIds) {
    if (productIds == null || productIds.isEmpty()) return Collections.emptyList();
    List<Product> products = productRepository.findAllById(productIds);
    Map<Long, Product> map = products.stream().collect(Collectors.toMap(Product::getId, p -> p));
    List<Product> ordered =
        productIds.stream().map(map::get).filter(java.util.Objects::nonNull).toList();
    return mapToCards(ordered);
  }

  private List<ProductCardDto> mapToCards(List<Product> products) {
    if (products.isEmpty()) return Collections.emptyList();

    List<Long> productIds = products.stream().map(Product::getId).toList();

    // Bulk fetch images
    List<ProductImage> allImages =
        productImageRepository.findByProductIdInOrderBySortOrderAsc(productIds);
    Map<Long, String> primaryImageMap = new HashMap<>();
    for (ProductImage img : allImages) {
      primaryImageMap.putIfAbsent(img.getProductId(), img.getUrl());
    }

    // Determine products with active variants
    List<ProductVariant> allVariants =
        productVariantRepository.findByProductIdInAndActiveTrue(productIds);
    java.util.Set<Long> productsWithVariants =
        allVariants.stream().map(ProductVariant::getProductId).collect(Collectors.toSet());

    // Bulk fetch inventory
    List<Inventory> allInventory = inventoryRepository.findByProductIdIn(productIds);
    Map<Long, Integer> inventoryMap = new HashMap<>();
    for (Inventory inv : allInventory) {
      int avail = Math.max(0, inv.getQuantityOnHand() - inv.getQuantityReserved());
      boolean hasVariants = productsWithVariants.contains(inv.getProductId());
      if (hasVariants) {
        if (inv.getVariantId() != null) {
          inventoryMap.merge(inv.getProductId(), avail, Integer::sum);
        }
      } else {
        if (inv.getVariantId() == null) {
          inventoryMap.merge(inv.getProductId(), avail, Integer::sum);
        }
      }
    }

    Instant now = Instant.now();
    java.util.Set<Long> top8PopularIds =
        productRepository.findTop8ByActiveTrueOrderBySoldCountDesc().stream()
            .map(Product::getId)
            .collect(Collectors.toSet());

    // Flash sale active promo products
    Map<Long, Long> flashSalePrices = new HashMap<>();
    Optional<Promotion> flashPromo =
        promotionRepository.findFirstActiveByType(PromotionType.FLASH_SALE, now);
    if (flashPromo.isPresent()) {
      List<PromotionProduct> pps =
          promotionProductRepository.findByPromotionId(flashPromo.get().getId());
      for (PromotionProduct pp : pps) {
        flashSalePrices.put(pp.getProductId(), pp.getSalePriceCents());
      }
    }

    // BOGO promo product IDs
    java.util.Set<Long> bogoProductIds = new java.util.HashSet<>();
    Optional<Promotion> bogoPromo =
        promotionRepository.findFirstActiveByType(PromotionType.BOGO, now);
    if (bogoPromo.isPresent()) {
      List<PromotionProduct> bogoPps =
          promotionProductRepository.findByPromotionId(bogoPromo.get().getId());
      for (PromotionProduct pp : bogoPps) {
        bogoProductIds.add(pp.getProductId());
      }
    }

    return products.stream()
        .map(
            p -> {
              int avail = inventoryMap.getOrDefault(p.getId(), 0);
              String img =
                  primaryImageMap.getOrDefault(p.getId(), "https://via.placeholder.com/300");
              List<String> badges = new ArrayList<>();
              if (top8PopularIds.contains(p.getId())) {
                badges.add("POPULAR");
              }
              if (Duration.between(p.getCreatedAt(), now).toDays() <= 14) {
                badges.add("NEW");
              }
              if (flashSalePrices.containsKey(p.getId())) {
                badges.add("FLASH_SALE");
              }
              if (avail <= 5) {
                badges.add("LOW_STOCK");
              }
              if (bogoProductIds.contains(p.getId())) {
                badges.add("BOGO");
              }

              Long salePriceCents = flashSalePrices.get(p.getId());

              return new ProductCardDto(
                  p.getId(),
                  p.getSku(),
                  p.getSlug(),
                  p.getName(),
                  p.getNameKm(),
                  p.getBrand(),
                  p.getPriceCents(),
                  p.getCompareAtCents(),
                  salePriceCents,
                  img,
                  p.getAvgRating(),
                  p.getReviewCount(),
                  avail,
                  avail > 0,
                  badges);
            })
        .toList();
  }

  private FacetsDto computeFacets(List<Long> categoryIds, String q) {
    List<Product> baseProducts =
        productRepository.findAll(
            (root, query, cb) -> {
              List<Predicate> p = new ArrayList<>();
              p.add(cb.isTrue(root.get("active")));
              if (categoryIds != null && !categoryIds.isEmpty()) {
                p.add(root.get("categoryId").in(categoryIds));
              }
              if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                p.add(
                    cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("nameKm"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("brand"), "")), pattern)));
              }
              return cb.and(p.toArray(new Predicate[0]));
            });

    // 1. Brands
    Map<String, Long> brandCounts =
        baseProducts.stream()
            .filter(p -> p.getBrand() != null && !p.getBrand().isBlank())
            .collect(Collectors.groupingBy(Product::getBrand, Collectors.counting()));
    List<FacetsDto.FacetValueDto> brandList =
        brandCounts.entrySet().stream()
            .map(e -> new FacetsDto.FacetValueDto(e.getKey(), e.getValue()))
            .sorted((a, b) -> Long.compare(b.count(), a.count()))
            .toList();

    // 2. Price Range
    Long minCents =
        baseProducts.stream().map(Product::getPriceCents).min(Long::compareTo).orElse(0L);
    Long maxCents =
        baseProducts.stream().map(Product::getPriceCents).max(Long::compareTo).orElse(0L);

    // 3. Variant Attributes
    List<Long> prodIds = baseProducts.stream().map(Product::getId).toList();
    List<ProductVariant> variants =
        prodIds.isEmpty()
            ? List.of()
            : productVariantRepository.findByProductIdInAndActiveTrue(prodIds);

    Map<String, Map<String, Long>> attrCounts = new LinkedHashMap<>();
    for (ProductVariant v : variants) {
      if (v.getAttributes() != null) {
        v.getAttributes()
            .forEach(
                (k, val) ->
                    attrCounts
                        .computeIfAbsent(k, key -> new HashMap<>())
                        .merge(val, 1L, Long::sum));
      }
    }

    List<FacetsDto.AttributeFacetDto> attributeFacets = new ArrayList<>();
    attrCounts.forEach(
        (attrName, valMap) -> {
          List<FacetsDto.FacetValueDto> vals =
              valMap.entrySet().stream()
                  .map(e -> new FacetsDto.FacetValueDto(e.getKey(), e.getValue()))
                  .sorted((a, b) -> Long.compare(b.count(), a.count()))
                  .toList();
          attributeFacets.add(new FacetsDto.AttributeFacetDto(attrName, vals));
        });

    return new FacetsDto(
        brandList, attributeFacets, new FacetsDto.PriceRangeDto(minCents, maxCents));
  }

  private void collectDescendantCategoryIds(Long parentId, List<Long> accumulator) {
    List<Category> children = categoryRepository.findByParentId(parentId);
    for (Category child : children) {
      accumulator.add(child.getId());
      collectDescendantCategoryIds(child.getId(), accumulator);
    }
  }
}
