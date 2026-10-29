package com.khmermart.seed;

import com.khmermart.model.entity.Coupon;
import com.khmermart.model.entity.Promotion;
import com.khmermart.model.entity.ShippingMethod;
import com.khmermart.model.enumerate.CouponType;
import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.enumerate.PromotionType;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.model.response.PromotionDiscountDto;
import com.khmermart.repository.AppUserRepository;
import com.khmermart.service.PricingItem;
import com.khmermart.service.PricingResult;
import com.khmermart.service.PricingService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!prod")
@Order(0)
public class DataSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final JdbcTemplate jdbcTemplate;
  private final com.khmermart.service.ReviewService reviewService;
  private final PricingService pricingService;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
      new com.fasterxml.jackson.databind.ObjectMapper();

  @Value("${app.seed.reset:false}")
  private boolean seedReset;

  public DataSeeder(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      JdbcTemplate jdbcTemplate,
      com.khmermart.service.ReviewService reviewService,
      PricingService pricingService) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.jdbcTemplate = jdbcTemplate;
    this.reviewService = reviewService;
    this.pricingService = pricingService;
  }

  @Override
  @Transactional
  public void run(String... args) {
    boolean resetRequested = seedReset;
    if (args != null) {
      for (String arg : args) {
        if ("--app.seed.reset=true".equalsIgnoreCase(arg)
            || "-Dapp.seed.reset=true".equalsIgnoreCase(arg)) {
          resetRequested = true;
          break;
        }
      }
    }

    if (resetRequested || appUserRepository.count() == 0) {
      log.info("Executing database reset & full seeding (resetRequested={})...", resetRequested);
      truncateAllTables();
      seedAll();
    } else {
      log.info(
          "Database already seeded with {} users. Skipping seeder.", appUserRepository.count());
    }
  }

  public void truncateAllTables() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
            reviews,
            tracking_events,
            shipments,
            bank_transfer_slips,
            refunds,
            payments,
            order_status_history,
            order_items,
            orders,
            coupon_redemptions,
            coupons,
            promotion_products,
            promotions,
            inventory_movements,
            inventory,
            product_images,
            product_variants,
            products,
            categories,
            addresses,
            cart_items,
            carts,
            wishlists,
            audit_logs,
            idempotency_records,
            notifications,
            push_subscriptions,
            refresh_tokens,
            password_reset_tokens,
            email_verification_tokens,
            shipping_methods,
            users
        CASCADE
        """);
  }

  public void seedAll() {
    seedUsersAndAddresses();
    seedSettings();
    seedShippingMethods();
    seedProvinces();
    seedCategories();
    seedProducts();
    seedPromotionsAndCoupons();
    seedOrdersAndReviews();
    List<Long> allProductIds =
        jdbcTemplate.queryForList("SELECT id FROM products ORDER BY id", Long.class);
    for (Long pid : allProductIds) {
      reviewService.recomputeProductRating(pid);
    }
    resetSequences();
    log.info(
        "Seeding complete: 3 seed customers, 8 root categories, 40 products, 32 orders, reviews recomputed.");
  }

  private void seedUsersAndAddresses() {
    // Exactly 5 seed users: admin, staff, 3 customers
    insertUser(
        1L, "admin@khmermart.local", "Admin@12345", "Admin User", "+85512000001", Roles.ADMIN);
    insertUser(
        2L, "staff@khmermart.local", "Staff@12345", "Staff User", "+85512000002", Roles.STAFF);
    insertUser(
        3L,
        "customer1@khmermart.local",
        "Customer@123",
        "Sopheap Customer",
        "+85512345678",
        Roles.CUSTOMER);
    insertUser(
        4L,
        "customer2@khmermart.local",
        "Customer@123",
        "Dara Khiev",
        "+85512888111",
        Roles.CUSTOMER);
    insertUser(
        5L,
        "customer3@khmermart.local",
        "Customer@123",
        "Bopha Pich",
        "+85512888222",
        Roles.CUSTOMER);

    // Seed addresses for customers
    insertAddress(
        1L,
        3L,
        "Home",
        "Sopheap Customer",
        "+85512345678",
        "Street 2004, Borey New World",
        "Sangkat Kakab",
        "Khan Pou Senchey",
        "Phnom Penh",
        "12000",
        true);
    insertAddress(
        2L,
        3L,
        "Office",
        "Sopheap Customer",
        "+85512345678",
        "Vattanac Capital Tower, Fl 18",
        "Preah Monivong Blvd",
        "Khan Doun Penh",
        "Phnom Penh",
        "12202",
        false);
    insertAddress(
        3L,
        4L,
        "Home",
        "Dara Khiev",
        "+85512888111",
        "National Road 6, Svay Dangkum",
        "Sangkat Svay Dangkum",
        "Krong Siem Reap",
        "Siem Reap",
        "17252",
        true);
    insertAddress(
        4L,
        5L,
        "Home",
        "Bopha Pich",
        "+85512888222",
        "Street 51, BKK1",
        "Sangkat Boeng Keng Kang 1",
        "Khan Chamkarmon",
        "Phnom Penh",
        "12302",
        true);
    insertAddress(
        5L,
        5L,
        "Siem Reap Villa",
        "Bopha Pich",
        "+85512888222",
        "Wat Bo Village, Salakamreuk",
        "Sangkat Salakamreuk",
        "Krong Siem Reap",
        "Siem Reap",
        "17254",
        false);
  }

  private void insertUser(
      Long id, String email, String password, String name, String phone, Roles role) {
    jdbcTemplate.update(
        """
        INSERT INTO users (id, email, password_hash, full_name, phone, role, is_active, email_verified_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, true, now(), now(), now())
        ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, password_hash = EXCLUDED.password_hash
        """,
        id,
        email.toLowerCase(),
        passwordEncoder.encode(password),
        name,
        phone,
        role.name());
  }

  private void insertAddress(
      Long id,
      Long userId,
      String label,
      String recipient,
      String phone,
      String line1,
      String line2,
      String district,
      String province,
      String postalCode,
      boolean isDefault) {
    jdbcTemplate.update(
        """
        INSERT INTO addresses (id, user_id, label, recipient_name, phone, line1, line2, district, province, postal_code, is_default)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        userId,
        label,
        recipient,
        phone,
        line1,
        line2,
        district,
        province,
        postalCode,
        isDefault);
  }

  private void seedSettings() {
    insertSetting("STORE_NAME", "KhmerMart");
    insertSetting("USD_KHR_RATE", "4100");
    insertSetting("DEFAULT_CURRENCY", "USD");
    insertSetting("FREE_SHIPPING_OVER_CENTS", "5000");
    insertSetting("KHQR_MODE", "MOCK");
  }

  private void insertSetting(String key, String value) {
    jdbcTemplate.update(
        "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value",
        key,
        value);
  }

  private void seedShippingMethods() {
    insertShippingMethod(
        1L, "STANDARD", "Standard Delivery", "សេវាដឹកជញ្ជូនធម្មតា", 150L, 5000L, 2, 4, false);
    insertShippingMethod(
        2L, "EXPRESS", "Express Delivery", "សេវាដឹកជញ្ជូនរហ័ស", 300L, null, 1, 2, false);
    insertShippingMethod(
        3L, "SAME_DAY", "Same-Day Delivery", "សេវាដឹកជញ្ជូនក្នុងថ្ងៃតែមួយ", 250L, null, 0, 1, true);
  }

  private void insertShippingMethod(
      Long id,
      String code,
      String name,
      String nameKm,
      Long feeCents,
      Long freeOverCents,
      int etaMin,
      int etaMax,
      boolean ppOnly) {
    jdbcTemplate.update(
        """
        INSERT INTO shipping_methods (id, code, name, name_km, fee_cents, free_over_cents, eta_days_min, eta_days_max, phnom_penh_only)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            code = EXCLUDED.code,
            name = EXCLUDED.name,
            name_km = EXCLUDED.name_km,
            fee_cents = EXCLUDED.fee_cents,
            free_over_cents = EXCLUDED.free_over_cents,
            eta_days_min = EXCLUDED.eta_days_min,
            eta_days_max = EXCLUDED.eta_days_max,
            phnom_penh_only = EXCLUDED.phnom_penh_only
        """,
        id,
        code,
        name,
        nameKm,
        feeCents,
        freeOverCents,
        etaMin,
        etaMax,
        ppOnly);
  }

  private void seedProvinces() {
    String[][] provinces = {
      {"PHNOM_PENH", "Phnom Penh", "រាជធានីភ្នំពេញ"},
      {"SIEM_REAP", "Siem Reap", "ខេត្តសៀមរាប"},
      {"BATTAMBANG", "Battambang", "ខេត្តបាត់ដំបង"},
      {"KAMPOT", "Kampot", "ខេត្តកំពត"},
      {"SIHANOUKVILLE", "Preah Sihanouk", "ខេត្តព្រះសីហនុ"},
      {"KAMPONG_CHAM", "Kampong Cham", "ខេត្តកំពង់ចាម"},
      {"KAMPONG_CHHNANG", "Kampong Chhnang", "ខេត្តកំពង់ឆ្នាំង"},
      {"KAMPONG_SPEU", "Kampong Speu", "ខេត្តកំពង់ស្ពឺ"},
      {"KAMPONG_THOM", "Kampong Thom", "ខេត្តកំពង់ធំ"},
      {"KANDAL", "Kandal", "ខេត្តកណ្តាល"},
      {"KOH_KONG", "Koh Kong", "ខេត្តកោះកុង"},
      {"KRATIE", "Kratie", "ខេត្តក្រចេះ"},
      {"MONDULKIRI", "Mondulkiri", "ខេត្តមណ្ឌលគិរី"},
      {"ODDAR_MEANCHEAY", "Oddar Meanchey", "ខេត្តឧត្តរមានជ័យ"},
      {"PAILIN", "Pailin", "ខេត្តប៉ៃលិន"},
      {"PREAH_VIHEAR", "Preah Vihear", "ខេត្តព្រះវិហារ"},
      {"PREY_VENG", "Prey Veng", "ខេត្តព្រៃវែង"},
      {"PURSAT", "Pursat", "ខេត្តពោធិ៍សាត់"},
      {"RATANAKIRI", "Ratanakiri", "ខេត្តរតនគិរី"},
      {"STUNG_TRENG", "Stung Treng", "ខេត្តស្ទឹងត្រែង"},
      {"SVAY_RIENG", "Svay Rieng", "ខេត្តស្វាយរៀង"},
      {"TAKEO", "Takeo", "ខេត្តតាកែវ"},
      {"TBOUNG_KHMUM", "Tboung Khmum", "ខេត្តត្បូងឃ្មុំ"},
      {"KEP", "Kep", "ខេត្តកែប"},
      {"BANTEAY_MEANCHEAY", "Banteay Meanchey", "ខេត្តបន្ទាយមានជ័យ"}
    };
    for (String[] p : provinces) {
      jdbcTemplate.update(
          "INSERT INTO provinces (code, name, name_km) VALUES (?, ?, ?) ON CONFLICT (code) DO NOTHING",
          p[0],
          p[1],
          p[2]);
    }
  }

  private void seedCategories() {
    // Exactly 8 ROOT categories (parent_id = null)
    int sortOrder = 1;
    for (SeedCatalog.Category c : SeedCatalog.CATEGORIES) {
      insertCategory(c.id(), c.name(), c.nameKm(), c.slug(), null, c.imageUrl(), sortOrder++);
    }
  }

  private void insertCategory(
      Long id,
      String name,
      String nameKm,
      String slug,
      Long parentId,
      String imageUrl,
      int sortOrder) {
    jdbcTemplate.update(
        """
        INSERT INTO categories (id, name, name_km, slug, parent_id, image_url, sort_order)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name,
            name_km = EXCLUDED.name_km,
            slug = EXCLUDED.slug,
            parent_id = EXCLUDED.parent_id,
            image_url = EXCLUDED.image_url,
            sort_order = EXCLUDED.sort_order
        """,
        id,
        name,
        nameKm,
        slug,
        parentId,
        imageUrl,
        sortOrder);
  }

  private void seedPromotionsAndCoupons() {
    Instant now = Instant.now();

    // 1. FLASH_SALE: hero 1001 Standard variants (per-variant), Kampot Black Pepper and Dried Mango
    // (product-wide). Live from seed time for 7 days so a demo right after a reset always shows a
    // running countdown
    jdbcTemplate.update(
        """
        INSERT INTO promotions (id, name, name_km, type, discount_percent, threshold_cents, starts_at, ends_at, banner_image, link, is_active)
        VALUES (1, ?, 'ការលក់បញ្ចុះតម្លៃពិសេស 24 ម៉ោង', 'FLASH_SALE', 15, 0, ?, ?, NULL, '/promotions/flash-sale', true)
        ON CONFLICT (id) DO NOTHING
        """,
        SeedCatalog.FLASH_NAME,
        Timestamp.from(now),
        Timestamp.from(now.plus(7, ChronoUnit.DAYS)));

    // Only the two Standard variants of 1001 are on sale; the Premium variants are not
    insertPromotionProduct(
        1L, SeedCatalog.HERO_ID, SeedCatalog.HERO_RED_STANDARD, SeedCatalog.HERO_FLASH_CENTS);
    insertPromotionProduct(
        1L, SeedCatalog.HERO_ID, SeedCatalog.HERO_BLUE_STANDARD, SeedCatalog.HERO_FLASH_CENTS);
    insertPromotionProduct(1L, SeedCatalog.PEPPER_ID, null, SeedCatalog.PEPPER_FLASH_CENTS);
    insertPromotionProduct(1L, SeedCatalog.MANGO_ID, null, SeedCatalog.MANGO_FLASH_CENTS);

    // 2. SEASONAL: Pchum Ben Festival Sale, 10% off Spices & Sauces (products 1006..1012)
    jdbcTemplate.update(
        """
        INSERT INTO promotions (id, name, name_km, type, discount_percent, threshold_cents, starts_at, ends_at, banner_image, link, is_active)
        VALUES (2, ?, ?, 'SEASONAL', ?, 0, ?, ?, ?, '/promotions/pchum-ben-sale', true)
        ON CONFLICT (id) DO NOTHING
        """,
        SeedCatalog.SEASONAL_NAME,
        SeedCatalog.SEASONAL_NAME_KM,
        SeedCatalog.SEASONAL_PERCENT,
        Timestamp.from(now.minus(5, ChronoUnit.DAYS)),
        Timestamp.from(now.plus(25, ChronoUnit.DAYS)),
        SeedCatalog.SEASONAL_BANNER);
    for (long pid = SeedCatalog.FIRST_SPICE_ID; pid <= SeedCatalog.LAST_SPICE_ID; pid++) {
      insertPromotionProduct(2L, pid, null);
    }

    // 3. BOGO: Buy 1 Get 1 Lemongrass Soap (no variants), same 7-day window as the flash sale
    jdbcTemplate.update(
        """
        INSERT INTO promotions (id, name, name_km, type, discount_percent, threshold_cents, starts_at, ends_at, banner_image, link, is_active)
        VALUES (3, ?, ?, 'BOGO', 50, 0, ?, ?, NULL, '/promotions/bogo', true)
        ON CONFLICT (id) DO NOTHING
        """,
        SeedCatalog.BOGO_NAME,
        SeedCatalog.BOGO_NAME_KM,
        Timestamp.from(now),
        Timestamp.from(now.plus(7, ChronoUnit.DAYS)));
    insertPromotionProduct(3L, SeedCatalog.BOGO_PRODUCT_ID, null);

    // Coupons
    insertCoupon(
        1L,
        SeedCatalog.COUPON_SAVE5,
        "FIXED",
        SeedCatalog.SAVE5_CENTS,
        "$5 off on orders over $35",
        SeedCatalog.SAVE5_MIN_ORDER_CENTS,
        null);
    insertCoupon(2L, "WELCOME10", "PERCENT", 10L, "10% off welcome coupon", null, 2000L);
    insertCoupon(3L, "FREESHIP", "FREE_SHIPPING", 0L, "Free standard shipping", 2000L, null);
    insertCoupon(
        4L,
        "EXPIRED",
        "FIXED",
        500L,
        "Expired coupon voucher",
        null,
        null,
        Timestamp.from(now.minus(10, ChronoUnit.DAYS)),
        Timestamp.from(now.minus(2, ChronoUnit.DAYS)));
  }

  private void insertPromotionProduct(Long promoId, Long prodId, Long salePrice) {
    insertPromotionProduct(promoId, prodId, null, salePrice);
  }

  private void insertPromotionProduct(Long promoId, Long prodId, Long variantId, Long salePrice) {
    jdbcTemplate.update(
        """
        INSERT INTO promotion_products (promotion_id, product_id, variant_id, sale_price_cents)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (promotion_id, product_id, variant_id) DO UPDATE SET sale_price_cents = EXCLUDED.sale_price_cents
        """,
        promoId,
        prodId,
        variantId,
        salePrice);
  }

  private void insertCoupon(
      Long id, String code, String type, Long value, String desc, Long minOrder, Long maxDiscount) {
    Instant now = Instant.now();
    insertCoupon(
        id,
        code,
        type,
        value,
        desc,
        minOrder,
        maxDiscount,
        Timestamp.from(now.minus(1, ChronoUnit.DAYS)),
        Timestamp.from(now.plus(90, ChronoUnit.DAYS)));
  }

  private void insertCoupon(
      Long id,
      String code,
      String type,
      Long value,
      String desc,
      Long minOrder,
      Long maxDiscount,
      Timestamp startsAt,
      Timestamp endsAt) {
    jdbcTemplate.update(
        """
        INSERT INTO coupons (id, code, type, value, description, min_order_cents, max_discount_cents, starts_at, ends_at, usage_limit, per_user_limit, used_count, is_active, version)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1000, 1, 0, true, 0)
        ON CONFLICT (id) DO UPDATE SET
            code = EXCLUDED.code,
            type = EXCLUDED.type,
            value = EXCLUDED.value,
            description = EXCLUDED.description,
            min_order_cents = EXCLUDED.min_order_cents,
            max_discount_cents = EXCLUDED.max_discount_cents,
            starts_at = EXCLUDED.starts_at,
            ends_at = EXCLUDED.ends_at,
            is_active = EXCLUDED.is_active
        """,
        id,
        code,
        type,
        value,
        desc,
        minOrder,
        maxDiscount,
        startsAt,
        endsAt);
  }

  private void seedProducts() {
    Instant now = Instant.now();
    for (SeedCatalog.Product p : SeedCatalog.PRODUCTS) {
      insertProduct(
          p.id(),
          p.sku(),
          p.name(),
          p.nameKm(),
          p.slug(),
          p.description(),
          p.brand(),
          p.priceCents(),
          null,
          p.categoryId(),
          0.0,
          0,
          p.soldCount(),
          now.minus(p.ageDays(), ChronoUnit.DAYS));
      // No verified product photos yet: a clean placeholder until real ones are uploaded in Admin
      insertProductImages(p.id(), productPlaceholder(p.name()));
      if (!p.hasVariants()) {
        insertProductStockNoVariant(p.id(), p.stock());
      }
    }
    for (SeedCatalog.Variant v : SeedCatalog.VARIANTS) {
      insertVariant(v.id(), v.productId(), v.sku(), v.attributesJson(), v.priceCents(), v.stock());
    }
  }

  private static String productPlaceholder(String name) {
    return "https://placehold.co/800x800/F3EBDD/5B4636/png?text="
        + URLEncoder.encode(name, StandardCharsets.UTF_8);
  }

  private void insertProduct(
      Long id,
      String sku,
      String name,
      String nameKm,
      String slug,
      String description,
      String brand,
      Long priceCents,
      Long compareAtCents,
      Long categoryId,
      double avgRating,
      int reviewCount,
      int soldCount,
      Instant createdAt) {

    jdbcTemplate.update(
        """
        INSERT INTO products (id, sku, name, name_km, slug, description, brand, price_cents, compare_at_cents, category_id, is_active, avg_rating, review_count, sold_count, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name,
            name_km = EXCLUDED.name_km,
            brand = EXCLUDED.brand,
            price_cents = EXCLUDED.price_cents,
            compare_at_cents = EXCLUDED.compare_at_cents,
            category_id = EXCLUDED.category_id,
            avg_rating = EXCLUDED.avg_rating,
            review_count = EXCLUDED.review_count,
            sold_count = EXCLUDED.sold_count,
            created_at = EXCLUDED.created_at
        """,
        id,
        sku,
        name,
        nameKm,
        slug,
        description,
        brand,
        priceCents,
        compareAtCents,
        categoryId,
        avgRating,
        reviewCount,
        soldCount,
        Timestamp.from(createdAt),
        Timestamp.from(createdAt));
  }

  private void insertProductImages(Long productId, String... urls) {
    for (int i = 0; i < urls.length; i++) {
      jdbcTemplate.update(
          """
          INSERT INTO product_images (product_id, url, sort_order)
          VALUES (?, ?, ?)
          ON CONFLICT DO NOTHING
          """,
          productId,
          urls[i],
          i);
    }
  }

  private void insertProductStockNoVariant(Long productId, int quantity) {
    jdbcTemplate.update(
        """
        INSERT INTO inventory (product_id, variant_id, quantity_on_hand, quantity_reserved, low_stock_threshold, low_stock_alerted, version, updated_at)
        VALUES (?, NULL, ?, 0, ?, false, 0, now())
        ON CONFLICT (product_id, COALESCE(variant_id, 0)) DO UPDATE SET quantity_on_hand = EXCLUDED.quantity_on_hand
        """,
        productId,
        quantity,
        SeedCatalog.LOW_STOCK_THRESHOLD);
  }

  private void insertVariant(
      Long id, Long productId, String sku, String attributesJson, Long priceCents, int quantity) {
    jdbcTemplate.update(
        """
        INSERT INTO product_variants (id, product_id, sku, attributes, price_cents, is_active)
        VALUES (?, ?, ?, ?::jsonb, ?, true)
        ON CONFLICT (id) DO UPDATE SET attributes = EXCLUDED.attributes, price_cents = EXCLUDED.price_cents
        """,
        id,
        productId,
        sku,
        attributesJson,
        priceCents);

    jdbcTemplate.update(
        """
        INSERT INTO inventory (product_id, variant_id, quantity_on_hand, quantity_reserved, low_stock_threshold, low_stock_alerted, version, updated_at)
        VALUES (?, ?, ?, 0, 5, false, 0, now())
        ON CONFLICT (product_id, COALESCE(variant_id, 0)) DO UPDATE SET quantity_on_hand = EXCLUDED.quantity_on_hand
        """,
        productId,
        id,
        quantity);
  }

  private record SeedProductLookup(
      long productId,
      String name,
      String sku,
      long priceCents,
      Long variantId,
      String variantSku,
      Long variantPriceCents,
      Long flashSalePriceCents) {}

  /** variantId is null for products without variants. */
  private record SeedOrderItemSpec(long productId, Long variantId, int quantity) {}

  private static SeedOrderItemSpec item(long productId, int quantity) {
    return new SeedOrderItemSpec(productId, null, quantity);
  }

  private static SeedOrderItemSpec variantItem(long productId, long variantId, int quantity) {
    return new SeedOrderItemSpec(productId, variantId, quantity);
  }

  private record OrderItemToInsert(
      long productId,
      Long variantId,
      String productName,
      String sku,
      long unitPriceCents,
      int quantity,
      long lineTotalCents) {}

  private record SeedReview(
      long productId, long userId, int rating, String title, String body, boolean pending) {}

  private void seedOrdersAndReviews() {
    Instant now = Instant.now();

    // 1. Fetch product details, every variant and the active flash-sale price of each from the DB
    Map<String, SeedProductLookup> catalog = new HashMap<>();
    jdbcTemplate.query(
        """
        SELECT p.id, p.name, p.sku, p.price_cents,
               pv.id AS v_id, pv.sku AS v_sku, pv.price_cents AS v_price,
               pp.sale_price_cents AS flash_price
        FROM products p
        LEFT JOIN product_variants pv ON pv.product_id = p.id
        LEFT JOIN promotion_products pp ON pp.product_id = p.id AND pp.promotion_id = 1
          AND (pp.variant_id IS NULL OR pp.variant_id = pv.id)
        """,
        rs -> {
          long pId = rs.getLong("id");
          String name = rs.getString("name");
          String sku = rs.getString("sku");
          long priceCents = rs.getLong("price_cents");
          Long vId = rs.getObject("v_id") != null ? rs.getLong("v_id") : null;
          String vSku = rs.getString("v_sku");
          Long vPrice = rs.getObject("v_price") != null ? rs.getLong("v_price") : null;
          Long flashPrice = rs.getObject("flash_price") != null ? rs.getLong("flash_price") : null;
          catalog.put(
              pId + ":" + (vId == null ? 0L : vId),
              new SeedProductLookup(pId, name, sku, priceCents, vId, vSku, vPrice, flashPrice));
        });

    // Configure standard shipping method and active promotion entities for PricingService
    ShippingMethod standardShipping = new ShippingMethod();
    standardShipping.setCode("STANDARD");
    standardShipping.setFeeCents(SeedCatalog.STANDARD_SHIPPING_CENTS);
    standardShipping.setFreeOverCents(SeedCatalog.FREE_SHIPPING_OVER_CENTS);

    List<Promotion> promotions = new ArrayList<>();
    Promotion promoFlash = new Promotion();
    promoFlash.setType(PromotionType.FLASH_SALE);
    promoFlash.setActive(true);
    promotions.add(promoFlash);

    Promotion promoSeasonal = new Promotion();
    promoSeasonal.setType(PromotionType.SEASONAL);
    promoSeasonal.setDiscountPercent(SeedCatalog.SEASONAL_PERCENT);
    promoSeasonal.setThresholdCents(0L);
    promoSeasonal.setActive(true);
    promotions.add(promoSeasonal);

    Promotion promoBogo = new Promotion();
    promoBogo.setType(PromotionType.BOGO);
    promoBogo.setActive(true);
    promotions.add(promoBogo);

    // 32 orders covering all 9 OrderStatus values across the last 30 days
    // Customers 3 (Sopheap), 4 (Dara), 5 (Bopha)
    OrderStatus[] statuses = {
      OrderStatus.DELIVERED, // 1
      OrderStatus.DELIVERED, // 2
      OrderStatus.DELIVERED, // 3
      OrderStatus.DELIVERED, // 4
      OrderStatus.DELIVERED, // 5
      OrderStatus.DELIVERED, // 6
      OrderStatus.DELIVERED, // 7
      OrderStatus.DELIVERED, // 8
      OrderStatus.DELIVERED, // 9
      OrderStatus.DELIVERED, // 10
      OrderStatus.DELIVERED, // 11
      OrderStatus.DELIVERED, // 12
      OrderStatus.SHIPPED, // 13
      OrderStatus.SHIPPED, // 14
      OrderStatus.SHIPPED, // 15
      OrderStatus.SHIPPED, // 16
      OrderStatus.OUT_FOR_DELIVERY, // 17
      OrderStatus.OUT_FOR_DELIVERY, // 18
      OrderStatus.OUT_FOR_DELIVERY, // 19
      OrderStatus.PROCESSING, // 20
      OrderStatus.PROCESSING, // 21
      OrderStatus.PROCESSING, // 22
      OrderStatus.CONFIRMED, // 23
      OrderStatus.CONFIRMED, // 24
      OrderStatus.CONFIRMED, // 25
      OrderStatus.PENDING_PAYMENT, // 26
      OrderStatus.PENDING_PAYMENT, // 27
      OrderStatus.PENDING_PAYMENT, // 28
      OrderStatus.CANCELLED, // 29
      OrderStatus.CANCELLED, // 30
      OrderStatus.PAYMENT_EXPIRED, // 31
      OrderStatus.REFUNDED // 32
    };

    long hero = SeedCatalog.HERO_ID;
    long rice = SeedCatalog.RICE_ID;
    List<List<SeedOrderItemSpec>> orderSpecs =
        List.of(
            List.of(
                variantItem(
                    hero,
                    SeedCatalog.HERO_RED_STANDARD,
                    1)), // 1: Hol Silk Sampot, Ruby Red Standard (flash)
            List.of(item(1006L, 2), item(1007L, 1)), // 2: Black Pepper 2x + Red Pepper
            List.of(item(1002L, 2), item(1003L, 1)), // 3: Cotton Krama 2x + Silk Krama Gift Box
            List.of(
                variantItem(rice, SeedCatalog.RICE_5KG_VARIANT, 1),
                item(1017L, 1)), // 4: Jasmine Rice 5 kg + Arabica
            List.of(
                item(1021L, 2),
                item(1022L, 3),
                item(1010L, 3)), // 5: Cashews, Dried Mango, Palm Sugar (SAVE5)
            List.of(item(1005L, 1)), // 6: Phamuong Silk Fabric
            List.of(
                item(1010L, 2),
                item(1011L, 2),
                item(1012L, 1)), // 7: Palm Sugar, Kroeung, Fish Sauce
            List.of(item(1031L, 1), item(1033L, 3)), // 8: Coconut Oil + Lemongrass Soap 3x
            List.of(item(1036L, 1), item(1037L, 1)), // 9: Angkor book + Folk Tales
            List.of(
                variantItem(rice, SeedCatalog.RICE_10KG_VARIANT, 1),
                item(1014L, 2)), // 10: Jasmine Rice 10 kg + Red Rice 2x (SAVE5)
            List.of(
                variantItem(
                    hero,
                    SeedCatalog.HERO_BLUE_PREMIUM,
                    1)), // 11: Hol Silk Sampot, Royal Blue Premium
            List.of(item(1026L, 1), item(1028L, 1)), // 12: Clay Pot + Rattan Basket
            List.of(item(1027L, 1)), // 13: Silver Box
            List.of(item(1018L, 2), item(1019L, 1)), // 14: Robusta 2x + Lotus Tea
            List.of(item(1008L, 1), item(1012L, 1)), // 15: Pepper Gift Set + Fish Sauce (SAVE5)
            List.of(item(1004L, 1), item(1002L, 1)), // 16: Table Runner + Cotton Krama
            List.of(item(1024L, 2), item(1023L, 2)), // 17: Num Akor 2 boxes + Banana Chips 2x
            List.of(item(1038L, 1), item(1040L, 1)), // 18: Dictionary + Home Cooking
            List.of(item(1015L, 2), item(1016L, 3)), // 19: Sticky Rice 2x + Num Banh Chok 3x
            List.of(
                variantItem(
                    hero,
                    SeedCatalog.HERO_BLUE_STANDARD,
                    1)), // 20: Hol Silk Sampot, Royal Blue Standard (SAVE5)
            List.of(item(1029L, 1), item(1030L, 3)), // 21: Apsara Replica + Palm Leaf Bookmark 3x
            List.of(item(1020L, 2), item(1009L, 1)), // 22: Lemongrass Tea 2x + Salt Flower
            List.of(item(1006L, 1), item(1022L, 2)), // 23: Black Pepper + Dried Mango 2x
            List.of(item(1034L, 1), item(1035L, 2)), // 24: Moringa Powder + Herbal Balm 2x
            List.of(item(1003L, 2)), // 25: Silk Krama Gift Box 2x (SAVE5)
            List.of(item(1017L, 1), item(1018L, 1)), // 26: Arabica + Robusta (PENDING)
            List.of(
                variantItem(
                    hero,
                    SeedCatalog.HERO_RED_STANDARD,
                    1)), // 27: Hol Silk Sampot, Ruby Red Standard (PENDING)
            List.of(item(1025L, 2)), // 28: Durian Candy 2x (PENDING)
            List.of(item(1005L, 1)), // 29: Phamuong Silk Fabric (CANCELLED)
            List.of(
                variantItem(
                    rice,
                    SeedCatalog.RICE_10KG_VARIANT,
                    2)), // 30: Jasmine Rice 10 kg 2x (CANCELLED, SAVE5)
            List.of(item(1039L, 2), item(1037L, 1)), // 31: Alphabet book 2x + Folk Tales (EXPIRED)
            List.of(item(1027L, 1)) // 32: Silver Box (REFUNDED)
            );

    long orderItemId = 1L;

    for (int i = 1; i <= statuses.length; i++) {
      OrderStatus status = statuses[i - 1];

      // Realistic date distribution across the last 30 days
      Instant placedAt;
      if (i <= 25) {
        placedAt =
            now.minus(28 - (i - 1), ChronoUnit.DAYS).minus((i * 17) % 60, ChronoUnit.MINUTES);
      } else if (i == 26) {
        placedAt = now.minus(2, ChronoUnit.DAYS).minus(45, ChronoUnit.MINUTES);
      } else if (i == 27) {
        placedAt = now.minus(1, ChronoUnit.DAYS).minus(30, ChronoUnit.MINUTES);
      } else if (i == 28) {
        placedAt = now.minus(2, ChronoUnit.HOURS);
      } else if (i == 29) {
        placedAt = now.minus(20, ChronoUnit.DAYS);
      } else if (i == 30) {
        placedAt = now.minus(14, ChronoUnit.DAYS);
      } else if (i == 31) {
        placedAt = now.minus(3, ChronoUnit.DAYS);
      } else { // 32
        placedAt = now.minus(16, ChronoUnit.DAYS);
      }

      String dateStr =
          LocalDate.ofInstant(placedAt, ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
      String orderNum = String.format("KM-%s-%06d", dateStr, i);
      Long userId = (long) (3 + (i % 3)); // Cycles among customers 3, 4, 5

      PaymentStatus paymentStatus = resolvePaymentStatus(status);
      String paymentMethod = (i % 4 == 0) ? "COD" : "KHQR";

      List<SeedOrderItemSpec> specs = orderSpecs.get(i - 1);
      List<PricingItem> pricingItems = new ArrayList<>();
      List<OrderItemToInsert> itemsToInsert = new ArrayList<>();

      for (SeedOrderItemSpec spec : specs) {
        SeedProductLookup prod =
            catalog.get(
                spec.productId() + ":" + (spec.variantId() == null ? 0L : spec.variantId()));
        if (prod == null) {
          throw new IllegalStateException("Seed order " + i + " references unknown " + spec);
        }

        Long varId = prod.variantId();
        // Use variant SKU if variant exists, or exact product SKU
        String sku =
            (prod.variantSku() != null && !prod.variantSku().isBlank())
                ? prod.variantSku()
                : prod.sku();
        long unitPrice =
            (prod.variantPriceCents() != null && prod.variantPriceCents() > 0)
                ? prod.variantPriceCents()
                : prod.priceCents();
        Long flashPrice = prod.flashSalePriceCents();
        // Same rule as checkout: linked products get the seasonal percent, others 0
        Integer seasonal = SeedCatalog.isSpice(prod.productId()) ? SeedCatalog.SEASONAL_PERCENT : 0;

        pricingItems.add(
            new PricingItem(
                prod.productId(),
                varId,
                unitPrice,
                spec.quantity(),
                flashPrice,
                prod.productId() == SeedCatalog.BOGO_PRODUCT_ID,
                seasonal));

        itemsToInsert.add(
            new OrderItemToInsert(
                prod.productId(),
                varId,
                prod.name(),
                sku,
                unitPrice,
                spec.quantity(),
                unitPrice * spec.quantity()));
      }

      Coupon coupon = null;
      if (i % 5 == 0) {
        coupon = new Coupon();
        coupon.setCode(SeedCatalog.COUPON_SAVE5);
        coupon.setType(CouponType.FIXED);
        coupon.setValue(SeedCatalog.SAVE5_CENTS);
        coupon.setMinOrderCents(SeedCatalog.SAVE5_MIN_ORDER_CENTS);
        coupon.setActive(true);
      }

      PricingResult pricing =
          pricingService.calculate(pricingItems, promotions, coupon, standardShipping);

      long subtotalCents = pricing.subtotalCents();
      long discountCents = pricing.discountCents();
      long shippingCents = pricing.shippingCents();
      long totalCents = pricing.totalCents();

      String discountBreakdownJson = null;
      if (discountCents > 0) {
        try {
          List<Map<String, Object>> promoList = new ArrayList<>();
          if (pricing.promotionDiscounts() != null) {
            for (PromotionDiscountDto p : pricing.promotionDiscounts()) {
              promoList.add(Map.of("label", p.label(), "amountCents", p.amountCents()));
            }
          }
          long promoSum =
              pricing.promotionDiscounts() != null
                  ? pricing.promotionDiscounts().stream()
                      .mapToLong(PromotionDiscountDto::amountCents)
                      .sum()
                  : 0L;
          long couponAmountCents = Math.max(0L, discountCents - promoSum);

          Map<String, Object> couponMap = null;
          if (coupon != null && couponAmountCents > 0) {
            couponMap = Map.of("code", coupon.getCode(), "amountCents", couponAmountCents);
          }

          Map<String, Object> breakdown = new HashMap<>();
          breakdown.put("promotions", promoList);
          if (couponMap != null) {
            breakdown.put("coupon", couponMap);
          }
          discountBreakdownJson = objectMapper.writeValueAsString(breakdown);
        } catch (Exception ignored) {
        }
      }

      // 1. Insert order
      jdbcTemplate.update(
          """
          INSERT INTO orders (id, order_number, user_id, status, payment_status, payment_method,
                              subtotal_cents, discount_cents, shipping_cents, total_cents,
                              currency, shipping_address, shipping_method_code, placed_at, confirmed_at, shipped_at, delivered_at, discount_breakdown)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'USD',
                  '{"recipientName": "Demo Customer", "phone": "+85512345678", "line1": "Street 2004", "province": "Phnom Penh"}'::jsonb,
                  'STANDARD', ?, ?, ?, ?, ?::jsonb)
          ON CONFLICT (id) DO NOTHING
          """,
          (long) i,
          orderNum,
          userId,
          status.name(),
          paymentStatus.name(),
          paymentMethod,
          subtotalCents,
          discountCents,
          shippingCents,
          totalCents,
          Timestamp.from(placedAt),
          (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.PAYMENT_EXPIRED)
              ? Timestamp.from(placedAt.plus(5, ChronoUnit.MINUTES))
              : null,
          (status == OrderStatus.SHIPPED
                  || status == OrderStatus.OUT_FOR_DELIVERY
                  || status == OrderStatus.DELIVERED)
              ? Timestamp.from(placedAt.plus(1, ChronoUnit.DAYS))
              : null,
          (status == OrderStatus.DELIVERED)
              ? Timestamp.from(placedAt.plus(2, ChronoUnit.DAYS))
              : null,
          discountBreakdownJson);

      // 2. Insert order items
      for (OrderItemToInsert it : itemsToInsert) {
        jdbcTemplate.update(
            """
            INSERT INTO order_items (id, order_id, product_id, variant_id, product_name_snapshot, sku_snapshot, unit_price_cents, quantity, line_total_cents)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """,
            orderItemId++,
            (long) i,
            it.productId(),
            it.variantId(),
            it.productName(),
            it.sku(),
            it.unitPriceCents(),
            it.quantity(),
            it.lineTotalCents());
      }

      // 3. Insert payment
      jdbcTemplate.update(
          """
          INSERT INTO payments (id, order_id, method, status, amount_cents, currency, amount_khr, bakong_hash, external_ref, paid_at, created_at)
          VALUES (?, ?, ?, ?, ?, 'USD', ? * 41 / 100, 'MOCK-HASH-' || ?, 'REF-' || ?, ?, ?)
          ON CONFLICT (id) DO NOTHING
          """,
          (long) i,
          (long) i,
          paymentMethod,
          paymentStatus.name(),
          totalCents,
          totalCents,
          orderNum,
          orderNum,
          paymentStatus == PaymentStatus.PAID
              ? Timestamp.from(placedAt.plus(5, ChronoUnit.MINUTES))
              : null,
          Timestamp.from(placedAt));

      // 4. Insert shipment and tracking events for shipped / delivered orders
      if (status == OrderStatus.SHIPPED
          || status == OrderStatus.OUT_FOR_DELIVERY
          || status == OrderStatus.DELIVERED) {
        String trkNum = "KM-TRK-" + String.format("%04d", i);
        jdbcTemplate.update(
            """
            INSERT INTO shipments (id, order_id, carrier, tracking_number, status, estimated_delivery)
            VALUES (?, ?, 'J&T Express', ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """,
            (long) i,
            (long) i,
            trkNum,
            status.name(),
            Timestamp.from(placedAt.plus(2, ChronoUnit.DAYS)));

        jdbcTemplate.update(
            """
            INSERT INTO tracking_events (id, shipment_id, status, location, note, created_at)
            VALUES (?, ?, 'PACKAGE_RECEIVED', 'Phnom Penh Central Hub', 'Package received from merchant', ?)
            ON CONFLICT (id) DO NOTHING
            """,
            (long) (i * 10 + 1),
            (long) i,
            Timestamp.from(placedAt.plus(1, ChronoUnit.DAYS)));

        if (status == OrderStatus.DELIVERED) {
          jdbcTemplate.update(
              """
              INSERT INTO tracking_events (id, shipment_id, status, location, note, created_at)
              VALUES (?, ?, 'DELIVERED', 'Customer Address', 'Package successfully handed to recipient', ?)
              ON CONFLICT (id) DO NOTHING
              """,
              (long) (i * 10 + 2),
              (long) i,
              Timestamp.from(placedAt.plus(2, ChronoUnit.DAYS)));
        }
      }
    }

    // Reviews: 3 on the hero product (avg 4.67), then 21 on products customers bought
    List<SeedReview> reviews =
        List.of(
            new SeedReview(
                1001,
                3,
                5,
                "Verified Purchase Review",
                "Beautiful hand-woven sampot. The Ruby Red colour is rich and the silk feels luxurious.",
                false),
            new SeedReview(
                1001,
                4,
                5,
                "Verified Purchase Review",
                "Perfect for my sister's wedding. The Premium grade is worth the upgrade.",
                false),
            new SeedReview(
                1001,
                5,
                4,
                "Verified Purchase Review",
                "Lovely Takeo craftsmanship and quick delivery to Phnom Penh.",
                false),
            new SeedReview(
                1002,
                3,
                5,
                "Everyday essential",
                "Soft cotton krama, I use it every day in the sun.",
                false),
            new SeedReview(
                1006,
                3,
                5,
                "Real Kampot pepper",
                "Strong aroma, a pinch is enough for a whole steak.",
                false),
            new SeedReview(
                1010,
                3,
                4,
                "Sweet and natural",
                "Good palm sugar for my curries, a little hard to scoop.",
                false),
            new SeedReview(
                1013,
                3,
                5,
                "Best jasmine rice",
                "Fragrant and soft, my family finished the 5 kg bag in two weeks.",
                false),
            new SeedReview(
                1017, 3, 4, "Smooth arabica", "Mild and fruity, great as a pour-over.", true),
            new SeedReview(
                1026,
                3,
                5,
                "Cooks rice beautifully",
                "The clay pot gives the rice a lovely flavour.",
                false),
            new SeedReview(
                1036,
                3,
                5,
                "Excellent photos",
                "Clear history of Angkor, perfect gift for visitors.",
                false),
            new SeedReview(
                1003,
                4,
                5,
                "Great gift",
                "Arrived in a nice box, my aunt loved the silk krama.",
                false),
            new SeedReview(
                1007,
                4,
                4,
                "Sweet heat",
                "Red pepper is milder and fruitier than the black one.",
                false),
            new SeedReview(
                1011,
                4,
                5,
                "Makes amok easy",
                "Fresh kroeung paste, saves an hour of pounding.",
                true),
            new SeedReview(
                1014,
                4,
                4,
                "Nutty and filling",
                "Good organic red rice, cooks in 35 minutes.",
                false),
            new SeedReview(
                1021,
                4,
                5,
                "Crunchy cashews",
                "Fresh and lightly salted, gone in two days.",
                false),
            new SeedReview(
                1028,
                4,
                4,
                "Sturdy basket",
                "Well woven, carries my market shopping easily.",
                false),
            new SeedReview(
                1037, 4, 5, "Lovely for kids", "Nice bilingual stories to read at bedtime.", false),
            new SeedReview(
                1005, 5, 4, "Smooth silk", "Even weave, tailor was happy to work with it.", false),
            new SeedReview(
                1009,
                5,
                5,
                "Delicate crystals",
                "Perfect on grilled fish and fresh tomatoes.",
                true),
            new SeedReview(
                1012,
                5,
                5,
                "Clean fish sauce",
                "Rich but not too salty, the best I have bought.",
                false),
            new SeedReview(
                1015,
                5,
                4,
                "Good sticky rice",
                "Steams well and holds together for mango sticky rice.",
                false),
            new SeedReview(
                1022,
                5,
                5,
                "Sweet and chewy",
                "Tastes like real mango, no added sugar needed.",
                false),
            new SeedReview(
                1038,
                5,
                5,
                "Handy dictionary",
                "Clear entries, helpful for my Khmer lessons.",
                false));

    int reviewId = 1;
    for (SeedReview r : reviews) {
      boolean replied = !r.pending() && reviewId % 3 == 0;
      jdbcTemplate.update(
          """
          INSERT INTO reviews (id, product_id, user_id, rating, title, body, status, admin_reply, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, now() - interval '5 days', now() - interval '5 days')
          ON CONFLICT (user_id, product_id) DO UPDATE SET
              rating = EXCLUDED.rating,
              body = EXCLUDED.body,
              status = EXCLUDED.status,
              admin_reply = EXCLUDED.admin_reply
          """,
          (long) (reviewId++),
          r.productId(),
          r.userId(),
          r.rating(),
          r.title(),
          r.body(),
          r.pending() ? "PENDING" : "APPROVED",
          replied ? "Thank you for shopping with KhmerMart! We appreciate your support." : null);
    }
  }

  private PaymentStatus resolvePaymentStatus(OrderStatus status) {
    return switch (status) {
      case DELIVERED, SHIPPED, OUT_FOR_DELIVERY, PROCESSING, CONFIRMED -> PaymentStatus.PAID;
      case CANCELLED -> PaymentStatus.FAILED;
      case PAYMENT_EXPIRED -> PaymentStatus.EXPIRED;
      case REFUNDED -> PaymentStatus.REFUNDED;
      case PENDING_PAYMENT -> PaymentStatus.PENDING;
    };
  }

  private void resetSequences() {
    String[] tables = {
      "users",
      "addresses",
      "categories",
      "products",
      "product_variants",
      "product_images",
      "inventory",
      "orders",
      "order_items",
      "payments",
      "shipments",
      "tracking_events",
      "reviews",
      "promotions",
      "promotion_products",
      "coupons"
    };

    for (String table : tables) {
      jdbcTemplate.queryForObject(
          String.format(
              "SELECT setval('%s_id_seq', GREATEST(2000, (SELECT COALESCE(MAX(id), 1) FROM %s)))",
              table, table),
          Long.class);
    }
  }
}
