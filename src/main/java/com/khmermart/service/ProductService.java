package com.khmermart.service;

import com.khmermart.common.response.PaginationResponse;
import com.khmermart.model.response.FacetsDto;
import com.khmermart.model.response.ProductCardDto;
import com.khmermart.model.response.ProductDetailDto;
import java.util.List;
import java.util.Map;

public interface ProductService {

  record ProductListResult(
      List<ProductCardDto> products, PaginationResponse meta, FacetsDto facets) {}

  ProductListResult getProducts(
      String q,
      Long categoryId,
      String brand,
      Long minPrice,
      Long maxPrice,
      Boolean inStock,
      String sort,
      Map<String, String> attributes,
      int page,
      int limit);

  ProductDetailDto getProductDetail(String idOrSlug);

  List<ProductCardDto> getBestSellers(int limit);

  List<ProductCardDto> getNewArrivals(int limit);
}
