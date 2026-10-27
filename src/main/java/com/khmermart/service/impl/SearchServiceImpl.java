package com.khmermart.service.impl;

import com.khmermart.model.entity.Category;
import com.khmermart.model.entity.Product;
import com.khmermart.model.entity.ProductImage;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.PromotionProduct;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.model.response.SearchSuggestDto;
import com.khmermart.repository.CategoryRepository;
import com.khmermart.repository.ProductImageRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.repository.ProductVariantRepository;
import com.khmermart.repository.PromotionProductRepository;
import com.khmermart.repository.PromotionRepository;
import com.khmermart.service.SearchService;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {

  private final ProductRepository productRepository;
  private final ProductVariantRepository productVariantRepository;
  private final ProductImageRepository productImageRepository;
  private final CategoryRepository categoryRepository;
  private final PromotionRepository promotionRepository;
  private final PromotionProductRepository promotionProductRepository;

  public SearchServiceImpl(
      ProductRepository productRepository,
      ProductVariantRepository productVariantRepository,
      ProductImageRepository productImageRepository,
      CategoryRepository categoryRepository,
      PromotionRepository promotionRepository,
      PromotionProductRepository promotionProductRepository) {
    this.productRepository = productRepository;
    this.productVariantRepository = productVariantRepository;
    this.productImageRepository = productImageRepository;
    this.categoryRepository = categoryRepository;
    this.promotionRepository = promotionRepository;
    this.promotionProductRepository = promotionProductRepository;
  }

  @Override
  public SearchSuggestDto suggest(String q) {
    if (q == null || q.isBlank()) {
      return new SearchSuggestDto(Collections.emptyList(), Collections.emptyList());
    }

    String pattern = "%" + q.trim().toLowerCase() + "%";

    // 1. Search matching products (limit 6)
    List<Product> products =
        productRepository
            .findAll(
                (root, query, cb) -> {
                  List<Predicate> p = new ArrayList<>();
                  p.add(cb.isTrue(root.get("active")));
                  p.add(
                      cb.or(
                          cb.like(cb.lower(root.get("name")), pattern),
                          cb.like(cb.lower(cb.coalesce(root.get("nameKm"), "")), pattern),
                          cb.like(cb.lower(cb.coalesce(root.get("brand"), "")), pattern),
                          cb.like(cb.lower(root.get("sku")), pattern)));
                  return cb.and(p.toArray(new Predicate[0]));
                },
                PageRequest.of(0, 6))
            .getContent();

    List<Long> prodIds = products.stream().map(Product::getId).toList();
    List<ProductImage> images =
        prodIds.isEmpty()
            ? Collections.emptyList()
            : productImageRepository.findByProductIdInOrderBySortOrderAsc(prodIds);
    Map<Long, String> imgMap = new HashMap<>();
    for (ProductImage img : images) {
      imgMap.putIfAbsent(img.getProductId(), img.getUrl());
    }

    // Resolve active flash sale prices if applicable
    Instant now = Instant.now();
    Map<Long, Long> flashSalePrices = new HashMap<>();
    List<Promotion> flashPromos = promotionRepository.findActiveByType(PromotionType.FLASH_SALE, now);
    if (!flashPromos.isEmpty() && !prodIds.isEmpty()) {
      java.util.Set<Long> productIdsWithVariants =
          productVariantRepository.findByProductIdInAndActiveTrue(prodIds).stream()
              .map(com.khmermart.model.entity.ProductVariant::getProductId)
              .collect(java.util.stream.Collectors.toSet());
      List<PromotionProduct> promoProducts =
          flashPromos.stream()
              .flatMap(fp -> promotionProductRepository.findByPromotionId(fp.getId()).stream())
              .toList();
      for (PromotionProduct pp : promoProducts) {
        if (!prodIds.contains(pp.getProductId()) || pp.getSalePriceCents() == null) continue;
        boolean hasVariants = productIdsWithVariants.contains(pp.getProductId());
        if (hasVariants && pp.getVariantId() != null) {
          flashSalePrices.merge(pp.getProductId(), pp.getSalePriceCents(), Math::min);
        } else if (!hasVariants && pp.getVariantId() == null) {
          flashSalePrices.merge(pp.getProductId(), pp.getSalePriceCents(), Math::min);
        }
      }
    }

    List<SearchSuggestDto.ProductSuggestItem> productItems =
        products.stream()
            .map(
                p ->
                    new SearchSuggestDto.ProductSuggestItem(
                        p.getSlug(),
                        p.getName(),
                        p.getNameKm(),
                        imgMap.getOrDefault(p.getId(), "https://via.placeholder.com/100"),
                        flashSalePrices.getOrDefault(p.getId(), p.getPriceCents())))
            .toList();

    // 2. Search matching categories (limit 4)
    List<Category> allCategories = categoryRepository.findAll();
    List<SearchSuggestDto.CategorySuggestItem> categoryItems =
        allCategories.stream()
            .filter(
                c ->
                    (c.getName() != null
                            && c.getName().toLowerCase().contains(q.trim().toLowerCase()))
                        || (c.getNameKm() != null
                            && c.getNameKm().toLowerCase().contains(q.trim().toLowerCase())))
            .limit(4)
            .map(
                c ->
                    new SearchSuggestDto.CategorySuggestItem(
                        c.getSlug(), c.getName(), c.getNameKm()))
            .toList();

    return new SearchSuggestDto(productItems, categoryItems);
  }
}
