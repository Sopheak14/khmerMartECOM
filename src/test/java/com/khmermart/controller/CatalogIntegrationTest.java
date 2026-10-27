package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.Inventory;
import com.khmermart.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CatalogIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InventoryRepository inventoryRepository;

  /**
   * Other test classes buy Laptop Pro 15 (1001); put its seeded stock back (variants 1-4 =
   * 15/15/10/10 on hand, nothing reserved) so the availability assertions do not depend on order.
   */
  @BeforeEach
  void resetLaptopStock() {
    int[] seeded = {15, 15, 10, 10};
    for (Inventory inv : inventoryRepository.findByProductId(1001L)) {
      inv.setQuantityOnHand(seeded[(int) (inv.getVariantId() - 1)]);
      inv.setQuantityReserved(0);
      inventoryRepository.save(inv);
    }
  }

  @Test
  @DisplayName(
      "GET /api/settings/public and GET /api/home return complete store configs and aggregated feeds")
  void testSettingsAndHome() throws Exception {
    // 1. Settings Public
    ResponseEntity<String> settingsResp =
        restTemplate.getForEntity("/api/settings/public", String.class);
    assertThat(settingsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode settingsJson = objectMapper.readTree(settingsResp.getBody());
    assertThat(settingsJson.get("success").asBoolean()).isTrue();
    assertThat(settingsJson.get("data").get("storeName").asText()).isEqualTo("KhmerMart");
    assertThat(settingsJson.get("data").get("khrRate").asLong()).isEqualTo(4100);
    assertThat(settingsJson.get("data").has("seasonalBanner")).isTrue();
    assertThat(settingsJson.get("data").has("flashSale")).isTrue();

    // 2. Home Feed
    ResponseEntity<String> homeResp = restTemplate.getForEntity("/api/home", String.class);
    assertThat(homeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode homeJson = objectMapper.readTree(homeResp.getBody());
    assertThat(homeJson.get("success").asBoolean()).isTrue();
    JsonNode data = homeJson.get("data");
    assertThat(data.has("seasonalBanner")).isTrue();
    assertThat(data.has("flashSale")).isTrue();
    assertThat(data.get("flashSale").get("endsAt").asText()).isNotEmpty();
    assertThat(data.get("flashSale").get("products").size()).isEqualTo(4);
    assertThat(data.get("categories").isArray()).isTrue();
    assertThat(data.get("bestSellers").isArray()).isTrue();
    assertThat(data.get("newArrivals").isArray()).isTrue();
  }

  @Test
  @DisplayName(
      "GET /api/categories returns category tree with valid UTF-8 Khmer names and product counts")
  void testCategoryTree() throws Exception {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/categories", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isTrue();
    JsonNode categories = json.get("data");
    assertThat(categories.size()).isEqualTo(8);

    // Electronics category with children
    JsonNode electronics = categories.get(0); // Assuming sorted by sort_order
    assertThat(electronics.get("slug").asText()).isEqualTo("electronics");
    assertThat(electronics.get("nameKm").asText()).isEqualTo("គ្រឿងអេឡិចត្រូនិច");
    assertThat(electronics.get("children").size()).isGreaterThanOrEqualTo(2);
    assertThat(electronics.get("productCount").asLong()).isGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName("GET /api/products?page=1&limit=5 returns pagination meta and facets")
  void testProductListingWithFacets() throws Exception {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/products?page=1&limit=5", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isTrue();
    assertThat(json.get("data").size()).isLessThanOrEqualTo(5);

    // Check pagination meta
    JsonNode meta = json.get("meta");
    assertThat(meta.get("page").asInt()).isEqualTo(1);
    assertThat(meta.get("limit").asInt()).isEqualTo(5);
    assertThat(meta.get("total").asLong()).isGreaterThanOrEqualTo(10);

    // Check facets
    JsonNode facets = json.get("facets");
    assertThat(facets.has("brands")).isTrue();
    assertThat(facets.has("attributes")).isTrue();
    assertThat(facets.has("priceRange")).isTrue();
  }

  @Test
  @DisplayName(
      "GET /api/products/1001 returns Laptop Pro 15 with priceCents 99900, variants, variantOptions, ratingBreakdown, related")
  void testProduct1001Detail() throws Exception {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/products/1001", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isTrue();
    JsonNode p = json.get("data");

    assertThat(p.get("id").asLong()).isEqualTo(1001);
    assertThat(p.get("name").asText()).isEqualTo("Laptop Pro 15");
    assertThat(p.get("priceCents").asLong()).isEqualTo(99900);
    assertThat(p.get("nameKm").asText()).isEqualTo("កុំព្យូទ័រយួរដៃ Laptop Pro 15");

    // Variants
    JsonNode variants = p.get("variants");
    assertThat(variants.size()).isEqualTo(4);
    assertThat(p.get("available").asInt()).isEqualTo(50);
    assertThat(variants.get(0).get("salePriceCents").asLong()).isEqualTo(89900L);
    assertThat(variants.get(1).get("salePriceCents").asLong()).isEqualTo(89900L);
    assertThat(variants.get(2).has("salePriceCents")).isFalse();
    assertThat(variants.get(3).has("salePriceCents")).isFalse();
    assertThat(p.get("salePriceCents").asLong()).isEqualTo(89900L);

    // Variant Options: color, ram, storage
    JsonNode options = p.get("variantOptions");
    assertThat(options.has("color")).isTrue();
    assertThat(options.has("ram")).isTrue();
    assertThat(options.has("storage")).isTrue();

    // Rating breakdown & reviewCount (C10 computed rule)
    JsonNode breakdown = p.get("ratingBreakdown");
    assertThat(breakdown.has("1")).isTrue();
    assertThat(breakdown.has("5")).isTrue();
    int breakdownSum = 0;
    var it = breakdown.elements();
    while (it.hasNext()) {
      breakdownSum += it.next().asInt();
    }
    int reviewCount = p.get("reviewCount").asInt();
    assertThat(reviewCount).isEqualTo(breakdownSum);
    assertThat(reviewCount).isEqualTo(3);

    // Related
    assertThat(p.get("related").isArray()).isTrue();
  }

  @Test
  @DisplayName(
      "Filter test: GET /api/products?q=laptop&minPrice=500&maxPrice=1000&brand=Dell&attr[ram]=16GB returns only Dell 16GB laptops in range")
  void testProductFiltering() throws Exception {
    String url = "/api/products?q=laptop&minPrice=500&maxPrice=1000&brand=Dell&attr[ram]=16GB";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isTrue();
    JsonNode products = json.get("data");

    assertThat(products.size())
        .isEqualTo(2); // Dell Latitude 5520 ($750) and Dell Inspiron 15 ($850)
    for (JsonNode prod : products) {
      assertThat(prod.get("brand").asText()).isEqualTo("Dell");
      assertThat(prod.get("priceCents").asLong()).isBetween(50000L, 100000L);
    }
  }

  @Test
  @DisplayName("GET /api/products sorting: price_desc and popular")
  void testProductSorting() throws Exception {
    // Sort price_desc
    ResponseEntity<String> priceDescResp =
        restTemplate.getForEntity("/api/products?sort=price_desc&limit=3", String.class);
    JsonNode priceJson = objectMapper.readTree(priceDescResp.getBody());
    JsonNode priceItems = priceJson.get("data");
    assertThat(priceItems.size()).isGreaterThanOrEqualTo(2);
    assertThat(priceItems.get(0).get("priceCents").asLong())
        .isGreaterThanOrEqualTo(priceItems.get(1).get("priceCents").asLong());

    // Sort popular
    ResponseEntity<String> popularResp =
        restTemplate.getForEntity("/api/products?sort=popular&limit=3", String.class);
    JsonNode popularJson = objectMapper.readTree(popularResp.getBody());
    assertThat(popularJson.get("success").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("GET /api/search/suggest returns autocomplete for both English and Khmer queries")
  void testSearchSuggest() throws Exception {
    // English query
    ResponseEntity<String> enResp =
        restTemplate.getForEntity("/api/search/suggest?q=lap", String.class);
    assertThat(enResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode enJson = objectMapper.readTree(enResp.getBody());
    assertThat(enJson.get("data").get("products").size()).isGreaterThanOrEqualTo(1);

    // Khmer query
    ResponseEntity<String> kmResp =
        restTemplate.getForEntity("/api/search/suggest?q=កុំព្យូទ័រ", String.class);
    assertThat(kmResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode kmJson = objectMapper.readTree(kmResp.getBody());
    assertThat(kmJson.get("data").get("products").size()).isGreaterThanOrEqualTo(1);
    assertThat(kmJson.get("data").get("products").get(0).get("nameKm").asText())
        .contains("កុំព្យូទ័រ");
  }

  @Test
  @DisplayName("GET /api/products/does-not-exist returns 404 NOT_FOUND contract envelope")
  void testProductNotFound() throws Exception {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/products/does-not-exist", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("success").asBoolean()).isFalse();
    assertThat(json.get("error").get("code").asText()).isEqualTo("NOT_FOUND");
    assertThat(json.get("error").get("message").asText()).isEqualTo("Product not found");
  }

  @Test
  @DisplayName(
      "Product with no APPROVED reviews must have avgRating = 0 and reviewCount = 0 (e.g. Dell Latitude 5520, product 1002)")
  void testProductWithNoApprovedReviewsHasZeroRatingAndCount() throws Exception {
    // 1. GET /api/products/1002
    ResponseEntity<String> detailResp =
        restTemplate.getForEntity("/api/products/1002", String.class);
    assertThat(detailResp.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode detailJson = objectMapper.readTree(detailResp.getBody());
    assertThat(detailJson.get("success").asBoolean()).isTrue();
    JsonNode p = detailJson.get("data");

    assertThat(p.get("id").asLong()).isEqualTo(1002L);
    assertThat(p.get("reviewCount").asInt()).isEqualTo(0);
    assertThat(p.get("avgRating").asDouble()).isEqualTo(0.0);

    // Breakdown must be all 0s
    JsonNode breakdown = p.get("ratingBreakdown");
    for (int star = 1; star <= 5; star++) {
      assertThat(breakdown.get(String.valueOf(star)).asInt()).isEqualTo(0);
    }

    // 2. GET /api/products?q=Latitude card view must also show avgRating 0 and reviewCount 0
    ResponseEntity<String> listResp =
        restTemplate.getForEntity("/api/products?q=Latitude", String.class);
    assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode listJson = objectMapper.readTree(listResp.getBody());
    assertThat(listJson.get("data").isArray()).isTrue();
    assertThat(listJson.get("data").size()).isGreaterThanOrEqualTo(1);

    JsonNode card = listJson.get("data").get(0);
    assertThat(card.get("id").asLong()).isEqualTo(1002L);
    assertThat(card.get("reviewCount").asInt()).isEqualTo(0);
    assertThat(card.get("avgRating").asDouble()).isEqualTo(0.0);
  }
}
