package com.khmermart.controller;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.response.ProductCardDto;
import com.khmermart.model.response.ProductDetailDto;
import com.khmermart.service.ProductService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@Tag(name = "Product", description = "Product catalog and detail APIs")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @GetMapping
  @Operation(summary = "Search and filter product catalog with facets")
  public SuccessResponse<List<ProductCardDto>> getProducts(
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "categoryId", required = false) Long categoryId,
      @RequestParam(name = "brand", required = false) String brand,
      @RequestParam(name = "minPrice", required = false) Long minPrice,
      @RequestParam(name = "maxPrice", required = false) Long maxPrice,
      @RequestParam(name = "inStock", required = false) Boolean inStock,
      @RequestParam(name = "sort", required = false) String sort,
      @RequestParam(name = "page", defaultValue = "1") int page,
      @RequestParam(name = "limit", defaultValue = "20") int limit,
      @RequestParam Map<String, String> allParams) {

    Map<String, String> attributes = new HashMap<>();
    allParams.forEach(
        (key, value) -> {
          if (key.startsWith("attr[") && key.endsWith("]")) {
            String attrKey = key.substring(5, key.length() - 1);
            attributes.put(attrKey, value);
          }
        });

    ProductService.ProductListResult result =
        productService.getProducts(
            q, categoryId, brand, minPrice, maxPrice, inStock, sort, attributes, page, limit);

    return ApiResponseUtil.success(result.products(), result.meta(), result.facets());
  }

  @GetMapping("/{idOrSlug}")
  @Operation(summary = "Get full product detail by ID or Slug")
  public SuccessResponse<ProductDetailDto> getProductDetail(@PathVariable String idOrSlug) {
    return ApiResponseUtil.success(productService.getProductDetail(idOrSlug));
  }
}
