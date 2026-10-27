package com.khmermart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khmermart.AbstractIntegrationTest;
import com.khmermart.model.entity.Address;
import com.khmermart.model.entity.AppUser;
import com.khmermart.model.entity.Cart;
import com.khmermart.model.entity.CartItem;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.InventoryMovement;
import com.khmermart.model.entity.Product;
import com.khmermart.model.enumerate.PaymentMethod;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.model.request.CheckoutRequest;
import com.khmermart.repository.AddressRepository;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.repository.CartItemRepository;
import com.khmermart.repository.CartRepository;
import com.khmermart.repository.InventoryMovementRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.security.JwtService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

class ConcurrencyCheckoutIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ProductRepository productRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private InventoryMovementRepository movementRepository;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private AddressRepository addressRepository;
  @Autowired private CartRepository cartRepository;
  @Autowired private CartItemRepository cartItemRepository;
  @Autowired private JwtService jwtService;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName(
      "20 parallel checkouts against stock of 5: exactly 5 succeed, 15 fail with 409 OUT_OF_STOCK, quantity_reserved = 5")
  void testParallelCheckoutsAgainstStockOfFive() throws Exception {
    // 1. Create a dedicated product with stockOnHand = 5, stockReserved = 0
    Product product =
        Product.builder()
            .sku("CONCUR-5-PROD")
            .name("Limited Edition Keyboard")
            .nameKm("ក្តារចុចកម្រិតកំណត់")
            .slug("limited-edition-keyboard-" + UUID.randomUUID())
            .priceCents(5000L)
            .active(true)
            .categoryId(1L)
            .build();
    product = productRepository.save(product);

    Inventory inv =
        Inventory.builder()
            .productId(product.getId())
            .variantId(null)
            .quantityOnHand(5)
            .quantityReserved(0)
            // threshold 4: every thread's initial read of 5 crosses it, so each one hits the
            // low-stock path while the other checkouts are reserving (exposes lost updates)
            .lowStockThreshold(4)
            .build();
    inv = inventoryRepository.save(inv);

    // 2. Prepare 20 distinct customers with active cart containing 1 unit of this product
    int threadCount = 20;
    List<String> jwtTokens = new ArrayList<>();
    List<Long> addressIds = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      String email =
          "concur_user_"
              + i
              + "_"
              + UUID.randomUUID().toString().substring(0, 8)
              + "@khmermart.local";
      AppUser user =
          AppUser.builder()
              .email(email)
              .passwordHash(passwordEncoder.encode("Test@12345"))
              .fullName("Concurrent Customer " + i)
              .phone("+85512999" + String.format("%03d", i))
              .role(Roles.CUSTOMER)
              .active(true)
              .emailVerifiedAt(Instant.now())
              .build();
      user = appUserRepository.save(user);

      Address address =
          Address.builder()
              .userId(user.getId())
              .label("Home")
              .recipientName(user.getFullName())
              .phone(user.getPhone())
              .line1("Street " + i)
              .province("Phnom Penh")
              .district("Khan Daun Penh")
              .isDefault(true)
              .build();
      address = addressRepository.save(address);
      addressIds.add(address.getId());

      Cart cart = Cart.builder().userId(user.getId()).updatedAt(Instant.now()).build();
      cart = cartRepository.save(cart);

      CartItem item =
          CartItem.builder()
              .cartId(cart.getId())
              .productId(product.getId())
              .quantity(1)
              .unitPriceCentsSnapshot(product.getPriceCents())
              .build();
      cartItemRepository.save(item);

      String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
      jwtTokens.add(token);
    }

    // 3. Fire 20 parallel requests simultaneously
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch doneGate = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger outOfStockCount = new AtomicInteger(0);
    List<HttpStatus> statusCodes = Collections.synchronizedList(new ArrayList<>());
    List<String> errorCodes = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < threadCount; i++) {
      final int idx = i;
      executor.submit(
          () -> {
            try {
              startGate.await(); // Synchronize release
              HttpHeaders headers = new HttpHeaders();
              headers.setBearerAuth(jwtTokens.get(idx));
              headers.add("Idempotency-Key", UUID.randomUUID().toString());

              CheckoutRequest req =
                  new CheckoutRequest(
                      addressIds.get(idx), "STANDARD", PaymentMethod.COD, "Parallel test");

              ResponseEntity<String> res =
                  restTemplate.exchange(
                      "/api/checkout",
                      HttpMethod.POST,
                      new HttpEntity<>(req, headers),
                      String.class);

              statusCodes.add((HttpStatus) res.getStatusCode());
              if (res.getStatusCode() == HttpStatus.CONFLICT) {
                errorCodes.add(
                    objectMapper.readTree(res.getBody()).path("error").path("code").asText());
              }
              if (res.getStatusCode() == HttpStatus.OK) {
                successCount.incrementAndGet();
              } else if (res.getStatusCode() == HttpStatus.CONFLICT) {
                outOfStockCount.incrementAndGet();
              }
            } catch (Exception ex) {
              ex.printStackTrace();
            } finally {
              doneGate.countDown();
            }
          });
    }

    startGate.countDown(); // Start all threads together
    doneGate.await(); // Wait for all to finish
    executor.shutdown();

    // 4. Assert concurrency outcomes
    assertThat(doneGate.getCount()).isZero();
    assertThat(statusCodes).hasSize(threadCount);
    assertThat(statusCodes).allMatch(c -> c == HttpStatus.OK || c == HttpStatus.CONFLICT);
    assertThat(successCount.get()).isEqualTo(5);
    assertThat(outOfStockCount.get()).isEqualTo(15);
    assertThat(errorCodes).hasSize(15).allMatch("OUT_OF_STOCK"::equals);

    // 5. Verify database inventory state
    Inventory finalInv = inventoryRepository.findById(inv.getId()).orElseThrow();
    assertThat(finalInv.getQuantityOnHand()).isEqualTo(5);
    assertThat(finalInv.getQuantityReserved()).isEqualTo(5);
    assertThat(finalInv.getQuantityOnHand() - finalInv.getQuantityReserved()).isZero();

    // Verify inventory movements
    List<InventoryMovement> movements =
        movementRepository.findAll().stream()
            .filter(m -> m.getInventoryId().equals(finalInv.getId()))
            .toList();
    assertThat(movements).hasSize(5);
    assertThat(movements).allMatch(m -> m.getType().name().equals("RESERVE"));
    assertThat(movements.stream().mapToInt(InventoryMovement::getQuantity).sum()).isEqualTo(5);
  }
}
