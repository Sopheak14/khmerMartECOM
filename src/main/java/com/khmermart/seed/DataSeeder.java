package com.khmermart.seed;

import com.khmermart.model.entity.AppUser;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.repository.AppUserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!prod")
public class DataSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final JdbcTemplate jdbcTemplate;

  public DataSeeder(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      JdbcTemplate jdbcTemplate) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public void run(String... args) {
    seedUsers();
    seedSettings();
    seedCategories();
    seedPromotions();
    seedProducts();
    seedOrders();
    jdbcTemplate.queryForObject(
        "SELECT setval('users_id_seq', GREATEST(300, (SELECT COALESCE(MAX(id), 1) FROM users)))",
        Long.class);
  }

  private void seedUsers() {
    seedUserIfMissing(
        "admin@khmermart.local", "Admin@12345", "Admin User", "+85512000001", Roles.ADMIN);
    seedUserIfMissing(
        "staff@khmermart.local", "Staff@12345", "Staff User", "+85512000002", Roles.STAFF);
    seedUserIfMissing(
        "customer1@khmermart.local",
        "Customer@123",
        "Sopheap Customer",
        "+85512345678",
        Roles.CUSTOMER);
    seedUserIfMissing(
        "customer2@khmermart.local", "Customer@123", "Dara Khiev", "+85512888111", Roles.CUSTOMER);
    seedUserIfMissing(
        "customer3@khmermart.local", "Customer@123", "Bopha Pich", "+85512888222", Roles.CUSTOMER);
  }

  private void seedUserIfMissing(
      String email, String rawPassword, String fullName, String phone, Roles role) {
    if (!appUserRepository.existsByEmail(email.toLowerCase())) {
      AppUser user =
          AppUser.builder()
              .email(email.toLowerCase())
              .passwordHash(passwordEncoder.encode(rawPassword))
              .fullName(fullName)
              .phone(phone)
              .role(role)
              .active(true)
              .emailVerifiedAt(Instant.now())
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();
      appUserRepository.save(user);
      log.info("Seeded demo account: {}", email);
    }
  }

  private void seedSettings() {
    insertSettingIfMissing("STORE_NAME", "KhmerMart");
    insertSettingIfMissing("USD_KHR_RATE", "4100");
    insertSettingIfMissing("DEFAULT_CURRENCY", "USD");
    insertSettingIfMissing("FREE_SHIPPING_OVER_CENTS", "5000");
    insertSettingIfMissing("KHQR_MODE", "MOCK");
  }

  private void insertSettingIfMissing(String key, String value) {
    jdbcTemplate.update(
        "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT (key) DO NOTHING", key, value);
  }

  private void seedCategories() {
    // Parent Categories
    insertCategory(
        1L,
        "Electronics",
        "គ្រឿងអេឡិចត្រូនិក",
        "electronics",
        null,
        "https://images.unsplash.com/photo-1498049794561-7780e7231661?w=500",
        1);
    insertCategory(
        5L,
        "Fashion",
        "ម៉ូដ និង សំលៀកបំពាក់",
        "fashion",
        null,
        "https://images.unsplash.com/photo-1445205170230-053b83016050?w=500",
        2);
    insertCategory(
        8L,
        "Home & Kitchen",
        "គេហដ្ឋាន និង ផ្ទះបាយ",
        "home-kitchen",
        null,
        "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?w=500",
        3);
    insertCategory(
        9L,
        "Food & Grocery",
        "ម្ហូបអាហារ និង គ្រឿងទេស",
        "food-grocery",
        null,
        "https://images.unsplash.com/photo-1542838132-92c53300491e?w=500",
        4);

    // Subcategories under Electronics (parent_id = 1)
    insertCategory(
        2L,
        "Laptops & Computers",
        "កុំព្យូទ័រ និង យួរដៃ",
        "laptops",
        1L,
        "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=500",
        1);
    insertCategory(
        3L,
        "Smartphones & Tablets",
        "ទូរស័ព្ទ និង ថេប្លេត",
        "smartphones",
        1L,
        "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=500",
        2);
    insertCategory(
        4L,
        "Audio & Accessories",
        "ឧបករណ៍បំពងសំឡេង និង កាស",
        "audio",
        1L,
        "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=500",
        3);

    // Subcategories under Fashion (parent_id = 5)
    insertCategory(
        6L,
        "Men's Fashion",
        "ម៉ូដបុរស",
        "mens-fashion",
        5L,
        "https://images.unsplash.com/photo-1617137984095-74e4e5e3613f?w=500",
        1);
    insertCategory(
        7L,
        "Women's Fashion",
        "ម៉ូដនារី",
        "womens-fashion",
        5L,
        "https://images.unsplash.com/photo-1534126511673-b6899657816a?w=500",
        2);

    jdbcTemplate.queryForObject(
        "SELECT setval('categories_id_seq', GREATEST(20, (SELECT COALESCE(MAX(id), 1) FROM categories)))",
        Long.class);
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
            image_url = EXCLUDED.image_url
        """,
        id,
        name,
        nameKm,
        slug,
        parentId,
        imageUrl,
        sortOrder);
  }

  private void seedPromotions() {
    Instant now = Instant.now();
    Instant bannerStart = now.minus(5, ChronoUnit.DAYS);
    Instant bannerEnd = now.plus(30, ChronoUnit.DAYS);

    // 1. Seasonal Promotion
    jdbcTemplate.update(
        """
        INSERT INTO promotions (id, name, name_km, type, discount_percent, starts_at, ends_at, banner_image, link, is_active)
        VALUES (1, 'Khmer New Year Mega Sale', 'ការបញ្ចុះតម្លៃពិធីបុណ្យចូលឆ្នាំថ្មីប្រពៃណីជាតិ', 'SEASONAL', 25, ?, ?,
                'https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?w=1200', '/promotions/kny-sale', true)
        ON CONFLICT (id) DO UPDATE SET ends_at = EXCLUDED.ends_at, is_active = true
        """,
        Timestamp.from(bannerStart),
        Timestamp.from(bannerEnd));

    // 2. Flash Sale Promotion
    Instant flashStart = now.minus(1, ChronoUnit.DAYS);
    Instant flashEnd = now.plus(7, ChronoUnit.DAYS);
    jdbcTemplate.update(
        """
        INSERT INTO promotions (id, name, name_km, type, discount_percent, starts_at, ends_at, banner_image, link, is_active)
        VALUES (2, 'Flash Sale 24H', 'ការលក់បញ្ចុះតម្លៃពិសេស ២៤ ម៉ោង', 'FLASH_SALE', 20, ?, ?,
                'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=1200', '/flash-sale', true)
        ON CONFLICT (id) DO UPDATE SET ends_at = EXCLUDED.ends_at, is_active = true
        """,
        Timestamp.from(flashStart),
        Timestamp.from(flashEnd));

    jdbcTemplate.queryForObject(
        "SELECT setval('promotions_id_seq', GREATEST(10, (SELECT COALESCE(MAX(id), 1) FROM promotions)))",
        Long.class);
  }

  private void seedProducts() {
    Instant now = Instant.now();

    // 1. Crucial Product 1001: Laptop Pro 15
    insertProduct(
        1001L,
        "PROD-1001",
        "Laptop Pro 15",
        "កុំព្យូទ័រយួរដៃ Laptop Pro 15",
        "laptop-pro-15",
        "Laptop Pro 15 delivers extreme performance with cutting-edge 15.6 inch retina display, all-day battery life, and powerful multi-core processing for engineers and creators.",
        "KhmerTech",
        99900L,
        119900L,
        2L, // Laptops category
        4.8,
        48,
        152,
        now.minus(20, ChronoUnit.DAYS));

    // Images for 1001
    insertProductImage(
        1001L, "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800", 0);
    insertProductImage(
        1001L, "https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?w=800", 1);

    // Variants for 1001 (variantOptions: color {Space Gray, Silver}, ram {16GB, 32GB}, storage
    // {512GB, 1TB})
    insertVariant(
        1001L,
        "LP15-GRY-16-512",
        "{\"color\": \"Space Gray\", \"ram\": \"16GB\", \"storage\": \"512GB\"}",
        99900L,
        10);
    insertVariant(
        1001L,
        "LP15-SLV-16-512",
        "{\"color\": \"Silver\", \"ram\": \"16GB\", \"storage\": \"512GB\"}",
        99900L,
        8);
    insertVariant(
        1001L,
        "LP15-GRY-32-1TB",
        "{\"color\": \"Space Gray\", \"ram\": \"32GB\", \"storage\": \"1TB\"}",
        129900L,
        5);
    insertVariant(
        1001L,
        "LP15-SLV-32-1TB",
        "{\"color\": \"Silver\", \"ram\": \"32GB\", \"storage\": \"1TB\"}",
        129900L,
        4);

    // Reviews for 1001 (rating breakdown: 1:1, 2:2, 3:5, 4:15, 5:25 -> total 48)
    seedReviewsForProduct(1001L, 1, 2, 5, 15, 25);

    // Flash sale product link for 1001
    jdbcTemplate.update(
        "INSERT INTO promotion_products (promotion_id, product_id, sale_price_cents) VALUES (2, 1001, 89900) ON CONFLICT DO NOTHING");

    // 2. Dell Matching Laptop: Dell Latitude 5520 (price $750 = 75000 cents, brand: Dell, ram:
    // 16GB)
    insertProduct(
        1002L,
        "DELL-LAT-5520",
        "Dell Latitude 5520 Laptop",
        "កុំព្យូទ័រយួរដៃ Dell Latitude 5520",
        "dell-latitude-5520",
        "Reliable Dell Latitude business laptop with 11th Gen Intel Core i7, 16GB high speed memory, and enterprise grade security features.",
        "Dell",
        75000L,
        89900L,
        2L,
        4.6,
        18,
        45,
        now.minus(10, ChronoUnit.DAYS));
    insertProductImage(
        1002L, "https://images.unsplash.com/photo-1593642632823-8f785ba67e45?w=800", 0);
    insertVariant(
        1002L,
        "DELL-LAT-16GB",
        "{\"color\": \"Carbon Gray\", \"ram\": \"16GB\", \"storage\": \"512GB\"}",
        75000L,
        12);

    // 3. Dell Matching Laptop 2: Dell Inspiron 15 (price $850 = 85000 cents, brand: Dell, ram:
    // 16GB)
    insertProduct(
        1003L,
        "DELL-INSP-15",
        "Dell Inspiron 15 Laptop",
        "កុំព្យូទ័រយួរដៃ Dell Inspiron 15",
        "dell-inspiron-15",
        "Everyday laptop with Dell ComfortView display, responsive performance, and sleek modern finish.",
        "Dell",
        85000L,
        95000L,
        2L,
        4.5,
        24,
        80,
        now.minus(5, ChronoUnit.DAYS));
    insertProductImage(
        1003L, "https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=800", 0);
    insertVariant(
        1003L,
        "DELL-INSP-16GB",
        "{\"color\": \"Silver\", \"ram\": \"16GB\", \"storage\": \"1TB\"}",
        85000L,
        15);

    // 4. Dell Non-matching Laptop 1 (Price OUT of range: $1599 = 159900 cents > $1000)
    insertProduct(
        1004L,
        "DELL-XPS-15",
        "Dell XPS 15 OLED Laptop",
        "កុំព្យូទ័រយួរដៃ Dell XPS 15 OLED",
        "dell-xps-15",
        "Stunning 3.5K OLED touchscreen display paired with high-performance GeForce graphics for demanding workflows.",
        "Dell",
        159900L,
        179900L,
        2L,
        4.9,
        35,
        60,
        now.minus(3, ChronoUnit.DAYS));
    insertProductImage(
        1004L, "https://images.unsplash.com/photo-1593642702821-c8da6771f0c6?w=800", 0);
    insertVariant(
        1004L,
        "DELL-XPS-16GB",
        "{\"color\": \"Platinum Silver\", \"ram\": \"16GB\", \"storage\": \"1TB\"}",
        159900L,
        8);

    // 5. Dell Non-matching Laptop 2 (RAM OUT of range: 8GB, not 16GB)
    insertProduct(
        1005L,
        "DELL-VOSTRO-14",
        "Dell Vostro 14 Essential Laptop",
        "កុំព្យូទ័រយួរដៃ Dell Vostro 14",
        "dell-vostro-14",
        "Essential small business laptop equipped with fast SSD and durable military-tested chassis.",
        "Dell",
        59900L,
        69900L,
        2L,
        4.3,
        12,
        30,
        now.minus(15, ChronoUnit.DAYS));
    insertProductImage(
        1005L, "https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=800", 0);
    insertVariant(
        1005L,
        "DELL-VOSTRO-8GB",
        "{\"color\": \"Black\", \"ram\": \"8GB\", \"storage\": \"256GB\"}",
        59900L,
        20);

    // 6. Asus Non-matching Laptop (Brand OUT of range: Asus, not Dell)
    insertProduct(
        1006L,
        "ASUS-ZENBOOK-14",
        "Asus ZenBook 14 Laptop",
        "កុំព្យូទ័រយួរដៃ Asus ZenBook 14",
        "asus-zenbook-14",
        "Ultra-lightweight aluminum chassis with brilliant NanoEdge display and all-day battery.",
        "Asus",
        89900L,
        99900L,
        2L,
        4.7,
        22,
        50,
        now.minus(8, ChronoUnit.DAYS));
    insertProductImage(1006L, "https://images.unsplash.com/photo-1541807084-5c52b6b3adef?w=800", 0);
    insertVariant(
        1006L,
        "ASUS-ZEN-16GB",
        "{\"color\": \"Pine Gray\", \"ram\": \"16GB\", \"storage\": \"512GB\"}",
        89900L,
        14);

    // 7. Additional catalog items across categories
    // Smartphones
    insertProduct(
        1007L,
        "APPLE-IPHONE-15PRO",
        "iPhone 15 Pro Titanium",
        "ទូរស័ព្ទ iPhone 15 Pro",
        "iphone-15-pro",
        "Aerospace-grade titanium design, A17 Pro chip, customizable Action button, and 48MP camera system.",
        "Apple",
        99900L,
        109900L,
        3L,
        4.9,
        65,
        210,
        now.minus(25, ChronoUnit.DAYS));
    insertProductImage(
        1007L, "https://images.unsplash.com/photo-1510557880182-3d4d3cba35a5?w=800", 0);
    insertVariant(
        1007L,
        "IP15P-256-NAT",
        "{\"color\": \"Natural Titanium\", \"storage\": \"256GB\"}",
        99900L,
        25);

    insertProduct(
        1008L,
        "SAMSUNG-S24-ULTRA",
        "Samsung Galaxy S24 Ultra",
        "ទូរស័ព្ទ Samsung Galaxy S24 Ultra",
        "samsung-s24-ultra",
        "Galaxy AI is here. Epic camera zoom, titanium frame, flat 6.8 inch display, and built-in S Pen.",
        "Samsung",
        119900L,
        129900L,
        3L,
        4.8,
        42,
        140,
        now.minus(12, ChronoUnit.DAYS));
    insertProductImage(
        1008L, "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?w=800", 0);
    insertVariant(
        1008L,
        "S24U-512-GRY",
        "{\"color\": \"Titanium Gray\", \"storage\": \"512GB\"}",
        119900L,
        18);

    // Audio
    insertProduct(
        1009L,
        "SONY-WH1000XM5",
        "Sony WH-1000XM5 Wireless Headphones",
        "កាសបំពងសំឡេង Sony WH-1000XM5",
        "sony-wh1000xm5",
        "Industry-leading noise canceling with two processors and 8 microphones. Magnificent sound quality.",
        "Sony",
        34900L,
        39900L,
        4L,
        4.7,
        38,
        95,
        now.minus(4, ChronoUnit.DAYS));
    insertProductImage(
        1009L, "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800", 0);
    insertVariant(1009L, "SONY-XM5-BLK", "{\"color\": \"Black\"}", 34900L, 30);

    // Fashion
    insertProduct(
        1010L,
        "KM-LINEN-SHIRT",
        "Men's Casual Linen Shirt",
        "អាវសាច់ក្រណាត់លីណេនបុរស",
        "mens-linen-shirt",
        "Breathable pure linen shirt tailored for tropical comfort and stylish weekend outings in Cambodia.",
        "KhmerMart",
        2900L,
        3500L,
        6L,
        4.4,
        19,
        130,
        now.minus(18, ChronoUnit.DAYS));
    insertProductImage(
        1010L, "https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800", 0);
    insertVariant(1010L, "SHIRT-WHT-L", "{\"color\": \"White\", \"size\": \"L\"}", 2900L, 50);

    // Home
    insertProduct(
        1011L,
        "PHILIPS-AIR-FRYER",
        "Philips Essential Airfryer 4.1L",
        "ឆ្នាំងបំពងខ្យល់ Philips 4.1L",
        "philips-airfryer-4l",
        "Rapid Air technology produces deliciously crispy food with up to 90 percent less fat.",
        "Philips",
        8900L,
        10900L,
        8L,
        4.8,
        52,
        185,
        now.minus(2, ChronoUnit.DAYS));
    insertProductImage(1011L, "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?w=800", 0);
    insertVariant(1011L, "AF-BLK-4L", "{\"color\": \"Black\", \"capacity\": \"4.1L\"}", 8900L, 22);

    // Grocery
    insertProduct(
        1012L,
        "ANGKOR-RICE-5KG",
        "Angkor Premium Phka Rumduol Jasmine Rice 5kg",
        "អង្ករផ្ការំដួលប្រណិតអង្គរ ៥គីឡូក្រាម",
        "angkor-jasmine-rice-5kg",
        "Award-winning authentic Cambodian fragrant Jasmine rice, harvest-fresh and naturally aromatic.",
        "Angkor Harvest",
        1200L,
        1500L,
        9L,
        4.9,
        88,
        320,
        now.minus(30, ChronoUnit.DAYS));
    insertProductImage(
        1012L, "https://images.unsplash.com/photo-1586201375761-83865001e31c?w=800", 0);
    insertVariant(1012L, "RICE-5KG", "{\"weight\": \"5kg\"}", 1200L, 100);

    jdbcTemplate.queryForObject(
        "SELECT setval('products_id_seq', GREATEST(1050, (SELECT COALESCE(MAX(id), 1) FROM products)))",
        Long.class);
    jdbcTemplate.queryForObject(
        "SELECT setval('product_variants_id_seq', GREATEST(1050, (SELECT COALESCE(MAX(id), 1) FROM product_variants)))",
        Long.class);
    jdbcTemplate.queryForObject(
        "SELECT setval('product_images_id_seq', GREATEST(1050, (SELECT COALESCE(MAX(id), 1) FROM product_images)))",
        Long.class);
    jdbcTemplate.queryForObject(
        "SELECT setval('inventory_id_seq', GREATEST(1050, (SELECT COALESCE(MAX(id), 1) FROM inventory)))",
        Long.class);
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
            sold_count = EXCLUDED.sold_count
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

    // Product-level inventory
    jdbcTemplate.update(
        """
        INSERT INTO inventory (product_id, variant_id, quantity_on_hand, quantity_reserved, low_stock_threshold, low_stock_alerted, version, updated_at)
        VALUES (?, NULL, 50, 0, 5, false, 0, now())
        ON CONFLICT (product_id, COALESCE(variant_id, 0)) DO UPDATE SET quantity_on_hand = EXCLUDED.quantity_on_hand
        """,
        id);
  }

  private void insertProductImage(Long productId, String url, int sortOrder) {
    jdbcTemplate.update(
        """
        INSERT INTO product_images (product_id, url, sort_order)
        VALUES (?, ?, ?)
        ON CONFLICT DO NOTHING
        """,
        productId,
        url,
        sortOrder);
  }

  private void insertVariant(
      Long productId, String sku, String attributesJson, Long priceCents, int quantity) {
    jdbcTemplate.update(
        """
        INSERT INTO product_variants (product_id, sku, attributes, price_cents, is_active)
        VALUES (?, ?, ?::jsonb, ?, true)
        ON CONFLICT (sku) DO UPDATE SET attributes = EXCLUDED.attributes, price_cents = EXCLUDED.price_cents
        """,
        productId,
        sku,
        attributesJson,
        priceCents);

    Long variantId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM product_variants WHERE sku = ?", Long.class, sku);

    if (variantId != null) {
      jdbcTemplate.update(
          """
          INSERT INTO inventory (product_id, variant_id, quantity_on_hand, quantity_reserved, low_stock_threshold, low_stock_alerted, version, updated_at)
          VALUES (?, ?, ?, 0, 5, false, 0, now())
          ON CONFLICT (product_id, COALESCE(variant_id, 0)) DO UPDATE SET quantity_on_hand = EXCLUDED.quantity_on_hand
          """,
          productId,
          variantId,
          quantity);
    }
  }

  private void seedReviewsForProduct(
      Long productId, int star1, int star2, int star3, int star4, int star5) {
    // Reviews table has UNIQUE(user_id, product_id)
    // To have multiple reviews with distinct ratings, we use distinct reviewer IDs starting at 200
    int userIdx = 200;
    for (int i = 0; i < star1; i++) insertReview(productId, (long) (userIdx++), 1);
    for (int i = 0; i < star2; i++) insertReview(productId, (long) (userIdx++), 2);
    for (int i = 0; i < star3; i++) insertReview(productId, (long) (userIdx++), 3);
    for (int i = 0; i < star4; i++) insertReview(productId, (long) (userIdx++), 4);
    for (int i = 0; i < star5; i++) insertReview(productId, (long) (userIdx++), 5);
  }

  private void insertReview(Long productId, Long userId, int rating) {
    // Make sure user exists if foreign key requires it
    jdbcTemplate.update(
        """
        INSERT INTO users (id, email, password_hash, full_name, phone, role, is_active, created_at, updated_at)
        VALUES (?, 'reviewer' || ? || '@khmermart.local', 'dummy', 'Reviewer ' || ?, '+85512000000', 'CUSTOMER', true, now(), now())
        ON CONFLICT (id) DO NOTHING
        """,
        userId,
        userId,
        userId);

    jdbcTemplate.update(
        """
        INSERT INTO reviews (product_id, user_id, rating, title, body, status, created_at, updated_at)
        VALUES (?, ?, ?, 'Review Title', 'Excellent quality and fast shipping in Cambodia!', 'APPROVED', now(), now())
        ON CONFLICT (user_id, product_id) DO NOTHING
        """,
        productId,
        userId,
        rating);
  }

  private void seedOrders() {
    for (int i = 1; i <= 5; i++) {
      String orderNum = "KM-ORD-20261023-" + String.format("%04d", i);
      jdbcTemplate.update(
          """
          INSERT INTO orders (order_number, user_id, status, payment_status, payment_method,
                              subtotal_cents, discount_cents, shipping_cents, total_cents,
                              currency, shipping_address, shipping_method_code, placed_at)
          VALUES (?, 3, 'DELIVERED', 'PAID', 'KHQR', 99900, 0, 200, 100100, 'USD',
                  '{"recipientName": "Sopheap", "phone": "+85512345678", "line1": "St 2004", "province": "Phnom Penh"}'::jsonb,
                  'STANDARD_PP', now() - INTERVAL '5 days')
          ON CONFLICT (order_number) DO NOTHING
          """,
          orderNum);
    }
    jdbcTemplate.queryForObject(
        "SELECT setval('orders_id_seq', GREATEST(10, (SELECT COALESCE(MAX(id), 1) FROM orders)))",
        Long.class);
  }
}
