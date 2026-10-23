package com.khmermart.service.impl;

import com.khmermart.model.entity.Category;
import com.khmermart.model.entity.Product;
import com.khmermart.model.entity.ProductImage;
import com.khmermart.model.response.SearchSuggestDto;
import com.khmermart.repository.CategoryRepository;
import com.khmermart.repository.ProductImageRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.service.SearchService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {

  private final ProductRepository productRepository;
  private final ProductImageRepository productImageRepository;
  private final CategoryRepository categoryRepository;

  public SearchServiceImpl(
      ProductRepository productRepository,
      ProductImageRepository productImageRepository,
      CategoryRepository categoryRepository) {
    this.productRepository = productRepository;
    this.productImageRepository = productImageRepository;
    this.categoryRepository = categoryRepository;
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

    List<SearchSuggestDto.ProductSuggestItem> productItems =
        products.stream()
            .map(
                p ->
                    new SearchSuggestDto.ProductSuggestItem(
                        p.getSlug(),
                        p.getName(),
                        p.getNameKm(),
                        imgMap.getOrDefault(p.getId(), "https://via.placeholder.com/100"),
                        p.getPriceCents()))
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
