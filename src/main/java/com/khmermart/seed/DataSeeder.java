package com.khmermart.seed;

import com.khmermart.model.enumerate.OrderStatus;
import com.khmermart.model.enumerate.PaymentStatus;
import com.khmermart.model.enumerate.Roles;
import com.khmermart.repository.AppUserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

  @Value("${app.seed.reset:false}")
  private boolean seedReset;

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
    resetSequences();
    log.info("Seeding complete: 5 seed users, 8 root categories, 40 products, 32 orders, reviews.");
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
        1L,
        "STANDARD_PP",
        "Standard Phnom Penh Delivery",
        "សេវាដឹកជញ្ជូនធម្មតា ភ្នំពេញ",
        150L,
        5000L,
        1,
        2,
        true);
    insertShippingMethod(
        2L,
        "EXPRESS_PP",
        "Same-Day Express Phnom Penh",
        "សេវាដឹកជញ្ជូនរហ័សក្នុងថ្ងៃ ភ្នំពេញ",
        300L,
        null,
        0,
        1,
        true);
    insertShippingMethod(
        3L,
        "PROVINCIAL",
        "Provincial Standard Delivery",
        "សេវាដឹកជញ្ជូនតាមបណ្តាខេត្ត",
        250L,
        7000L,
        2,
        4,
        false);
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
        ON CONFLICT (id) DO NOTHING
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
    // Exactly 8 ROOT Categories (parent_id = null)
    insertCategory(
        1L,
        "Electronics",
        "គ្រឿងអេឡិចត្រូនិច",
        "electronics",
        null,
        "https://images.unsplash.com/photo-1498049794561-7780e7231661?w=500",
        1);
    insertCategory(
        2L,
        "Phones",
        "ទូរសព្ទ",
        "phones",
        null,
        "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=500",
        2);
    insertCategory(
        3L,
        "Laptops",
        "កុំព្យូទ័រយួរដៃ",
        "laptops",
        null,
        "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=500",
        3);
    insertCategory(
        4L,
        "Fashion",
        "សម្លៀកបំពាក់",
        "fashion",
        null,
        "https://images.unsplash.com/photo-1445205170230-053b83016050?w=500",
        4);
    insertCategory(
        5L,
        "Home & Living",
        "គេហដ្ឋាន",
        "home-living",
        null,
        "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?w=500",
        5);
    insertCategory(
        6L,
        "Beauty",
        "សម្ផស្ស",
        "beauty",
        null,
        "https://images.unsplash.com/photo-1596462502278-27bfdc403348?w=500",
        6);
    insertCategory(
        7L,
        "Groceries",
        "គ្រឿងទេស",
        "groceries",
        null,
        "https://images.unsplash.com/photo-1542838132-92c53300491e?w=500",
        7);
    insertCategory(
        8L,
        "Books",
        "សៀវភៅ",
        "books",
        null,
        "https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=500",
        8);

    // Subcategories under Electronics (1)
    insertCategory(
        9L,
        "Audio & Accessories",
        "ឧបករណ៍បំពងសំឡេង និង កាស",
        "audio-accessories",
        1L,
        "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=500",
        1);
    insertCategory(
        10L,
        "Cameras & Drones",
        "ម៉ាស៊ីនថត និង ដ្រូន",
        "cameras-drones",
        1L,
        "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=500",
        2);

    // Subcategories under Phones (2)
    insertCategory(
        11L,
        "Smartphones",
        "ស្មាតហ្វូន",
        "smartphones",
        2L,
        "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=500",
        1);
    insertCategory(
        12L,
        "Phone Accessories",
        "គ្រឿងបន្លាស់ទូរសព្ទ",
        "phone-accessories",
        2L,
        "https://images.unsplash.com/photo-1584438784894-089d6a62b8fa?w=500",
        2);

    // Subcategories under Laptops (3)
    insertCategory(
        13L,
        "Business Laptops",
        "កុំព្យូទ័រយួរដៃការងារ",
        "business-laptops",
        3L,
        "https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=500",
        1);
    insertCategory(
        14L,
        "Gaming Laptops",
        "កុំព្យូទ័រយួរដៃហ្គេម",
        "gaming-laptops",
        3L,
        "https://images.unsplash.com/photo-1603302576837-37561b2e2302?w=500",
        2);
    insertCategory(
        15L,
        "Ultrabooks",
        "អ៊ុលត្រាប៊ុក",
        "ultrabooks",
        3L,
        "https://images.unsplash.com/photo-1541807084-5c52b6b3adef?w=500",
        3);

    // Subcategories under Fashion (4)
    insertCategory(
        16L,
        "Men's Clothing",
        "សម្លៀកបំពាក់បុរស",
        "mens-clothing",
        4L,
        "https://images.unsplash.com/photo-1617137984095-74e4e5e3613f?w=500",
        1);
    insertCategory(
        17L,
        "Women's Clothing",
        "សម្លៀកបំពាក់នារី",
        "womens-clothing",
        4L,
        "https://images.unsplash.com/photo-1534126511673-b6899657816a?w=500",
        2);

    // Subcategories under Home & Living (5)
    insertCategory(
        18L,
        "Kitchen & Dining",
        "ផ្ទះបាយ និង បន្ទប់ទទួលទានអាហារ",
        "kitchen-dining",
        5L,
        "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?w=500",
        1);
    insertCategory(
        19L,
        "Bedding & Decor",
        "ការតុបតែងផ្ទះ និង ពូក",
        "bedding-decor",
        5L,
        "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=500",
        2);

    // Subcategories under Beauty (6)
    insertCategory(
        20L,
        "Skincare",
        "ការថែរក្សាស្បែក",
        "skincare",
        6L,
        "https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?w=500",
        1);
    insertCategory(
        21L,
        "Fragrances & Body",
        "ទឹកអប់ និង ថែរក្សាខ្លួន",
        "fragrances",
        6L,
        "https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?w=500",
        2);

    // Subcategories under Groceries (7)
    insertCategory(
        22L,
        "Rice & Grains",
        "អង្ករ និង គ្រាប់ធញ្ញជាតិ",
        "rice-grains",
        7L,
        "https://images.unsplash.com/photo-1586201375761-83865001e31c?w=500",
        1);
    insertCategory(
        23L,
        "Snacks & Beverages",
        "អាហារសម្រន់ និង ភេសជ្ជៈ",
        "snacks-beverages",
        7L,
        "https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=500",
        2);

    // Subcategories under Books (8)
    insertCategory(
        24L,
        "Khmer Literature",
        "អក្សរសិល្ប៍ និង ប្រវត្តិសាស្ត្រខ្មែរ",
        "khmer-literature",
        8L,
        "https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=500",
        1);
    insertCategory(
        25L,
        "Business & Self-Help",
        "អភិវឌ្ឍខ្លួន និង អាជីវកម្ម",
        "business-books",
        8L,
        "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=500",
        2);
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

    // 1. FLASH_SALE: 4 products (1001, 1010, 1015, 1031) ending in 3 days
    jdbcTemplate.update(
        """
        INSERT INTO promotions (id, name, name_km, type, discount_percent, threshold_cents, starts_at, ends_at, banner_image, link, is_active)
        VALUES (1, 'Flash Sale 24H', 'ការលក់បញ្ចុះតម្លៃពិសេស 24 ម៉ោង', 'FLASH_SALE', 15, 0, ?, ?, NULL, '/promotions/flash-sale', true)
        ON CONFLICT (id) DO NOTHING
        """,
        Timestamp.from(now.minus(1, ChronoUnit.DAYS)),
        Timestamp.from(now.plus(3, ChronoUnit.DAYS)));

    // 4 products in flash sale
    insertPromotionProduct(1L, 1001L, 89900L); // Laptop Pro 15
    insertPromotionProduct(1L, 1010L, 91900L); // iPhone 15 Pro
    insertPromotionProduct(1L, 1015L, 29900L); // Sony WH-1000XM5
    insertPromotionProduct(1L, 1031L, 990L); // Angkor Jasmine Rice 5kg

    // 2. SEASONAL: Khmer New Year Mega Sale
    jdbcTemplate.update(
        """
        INSERT INTO promotions (id, name, name_km, type, discount_percent, threshold_cents, starts_at, ends_at, banner_image, link, is_active)
        VALUES (2, 'Khmer New Year Mega Sale', 'ការបញ្ចុះតម្លៃពិធីបុណ្យចូលឆ្នាំថ្មីប្រពៃណីជាតិ', 'SEASONAL', 20, 5000, ?, ?, 'https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?w=1200', '/promotions/kny-sale', true)
        ON CONFLICT (id) DO NOTHING
        """,
        Timestamp.from(now.minus(5, ChronoUnit.DAYS)),
        Timestamp.from(now.plus(25, ChronoUnit.DAYS)));

    // 3. BOGO: Buy 1 Get 1 Free on product 1022 (Women's Floral Chiffon Blouse)
    jdbcTemplate.update(
        """
        INSERT INTO promotions (id, name, name_km, type, discount_percent, threshold_cents, starts_at, ends_at, banner_image, link, is_active)
        VALUES (3, 'Fashion Buy 1 Get 1 Free', 'ទិញ ១ ថែម ១ សម្រាប់ម៉ូដសម្លៀកបំពាក់', 'BOGO', 50, 0, ?, ?, NULL, '/promotions/bogo', true)
        ON CONFLICT (id) DO NOTHING
        """,
        Timestamp.from(now.minus(2, ChronoUnit.DAYS)),
        Timestamp.from(now.plus(12, ChronoUnit.DAYS)));
    insertPromotionProduct(3L, 1022L, 2400L);

    // Coupons
    insertCoupon(1L, "SAVE5", "FIXED", 500L, "$5 off on orders over $35", 3500L, null);
    insertCoupon(2L, "WELCOME10", "PERCENT", 10L, "10% off welcome coupon", null, 2000L);
    insertCoupon(3L, "FREESHIP", "FIXED", 250L, "Free standard shipping", 2000L, null);
  }

  private void insertPromotionProduct(Long promoId, Long prodId, Long salePrice) {
    jdbcTemplate.update(
        """
        INSERT INTO promotion_products (promotion_id, product_id, sale_price_cents)
        VALUES (?, ?, ?)
        ON CONFLICT (promotion_id, product_id) DO UPDATE SET sale_price_cents = EXCLUDED.sale_price_cents
        """,
        promoId,
        prodId,
        salePrice);
  }

  private void insertCoupon(
      Long id, String code, String type, Long value, String desc, Long minOrder, Long maxDiscount) {
    jdbcTemplate.update(
        """
        INSERT INTO coupons (id, code, type, value, description, min_order_cents, max_discount_cents, starts_at, ends_at, usage_limit, per_user_limit, used_count, is_active, version)
        VALUES (?, ?, ?, ?, ?, ?, ?, now() - interval '1 day', now() + interval '90 days', 1000, 1, 15, true, 0)
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        code,
        type,
        value,
        desc,
        minOrder,
        maxDiscount);
  }

  private void seedProducts() {
    Instant now = Instant.now();

    // ==========================================
    // Category 3: Laptops -> Subcategories 13, 14, 15
    // ==========================================
    // 1001: Laptop Pro 15 (Business Laptops 13) - 50 total units across 4 variants (15+15+10+10)
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
        13L,
        4.8,
        48,
        70,
        now.minus(20, ChronoUnit.DAYS));
    insertProductImages(
        1001L,
        "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800",
        "https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?w=800");
    insertVariant(
        1L,
        1001L,
        "LP15-GRY-16-512",
        "{\"ram\": \"16GB\", \"color\": \"Space Gray\", \"storage\": \"512GB\"}",
        99900L,
        15);
    insertVariant(
        2L,
        1001L,
        "LP15-SLV-16-512",
        "{\"ram\": \"16GB\", \"color\": \"Silver\", \"storage\": \"512GB\"}",
        99900L,
        15);
    insertVariant(
        3L,
        1001L,
        "LP15-GRY-32-1TB",
        "{\"ram\": \"32GB\", \"color\": \"Space Gray\", \"storage\": \"1TB\"}",
        129900L,
        10);
    insertVariant(
        4L,
        1001L,
        "LP15-SLV-32-1TB",
        "{\"ram\": \"32GB\", \"color\": \"Silver\", \"storage\": \"1TB\"}",
        129900L,
        10);

    // 1002: Dell Latitude 5520 (Dell, 16GB RAM, $750 in $500-$1000 range, NEW)
    insertProduct(
        1002L,
        "DELL-LAT-5520",
        "Dell Latitude 5520 Laptop",
        "កុំព្យូទ័រយួរដៃ Dell Latitude 5520",
        "dell-latitude-5520",
        "Business-class laptop with 11th Gen Intel Core i7, reliable security features, and express charge support.",
        "Dell",
        75000L,
        89900L,
        13L,
        4.6,
        18,
        45,
        now.minus(5, ChronoUnit.DAYS));
    insertProductImages(
        1002L,
        "https://images.unsplash.com/photo-1593642632823-8f785ba67e45?w=800",
        "https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=800");
    insertVariant(
        5L,
        1002L,
        "DELL-LAT-16-512",
        "{\"ram\": \"16GB\", \"color\": \"Silver\", \"storage\": \"512GB\"}",
        75000L,
        25);

    // 1003: Dell Inspiron 15 (Dell, 16GB RAM, $850 in $500-$1000 range, NEW)
    insertProduct(
        1003L,
        "DELL-INSP-15",
        "Dell Inspiron 15 Laptop",
        "កុំព្យូទ័រយួរដៃ Dell Inspiron 15",
        "dell-inspiron-15",
        "Stylish laptop built for everyday multitasking with FHD anti-glare narrow border display and fast SSD storage.",
        "Dell",
        85000L,
        95000L,
        13L,
        4.5,
        24,
        55,
        now.minus(8, ChronoUnit.DAYS));
    insertProductImages(
        1003L,
        "https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=800",
        "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800");
    insertVariant(
        6L,
        1003L,
        "DELL-INSP-16-512",
        "{\"ram\": \"16GB\", \"color\": \"Black\", \"storage\": \"512GB\"}",
        85000L,
        20);

    // 1004: Dell XPS 15 OLED (Dell, 16GB RAM, $1599 - above $1000 non-matching, NEW)
    insertProduct(
        1004L,
        "DELL-XPS-15",
        "Dell XPS 15 OLED Laptop",
        "កុំព្យូទ័រយួរដៃ Dell XPS 15 OLED",
        "dell-xps-15",
        "High-performance creator laptop with stunning 3.5K OLED touch display and NVIDIA GeForce RTX graphics.",
        "Dell",
        159900L,
        179900L,
        13L,
        4.9,
        35,
        65,
        now.minus(10, ChronoUnit.DAYS));
    insertProductImages(
        1004L,
        "https://images.unsplash.com/photo-1593642702821-c8da6771f0c6?w=800",
        "https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?w=800");
    insertVariant(
        7L,
        1004L,
        "DELL-XPS-16-1TB",
        "{\"ram\": \"16GB\", \"color\": \"Platinum Silver\", \"storage\": \"1TB\"}",
        159900L,
        15);

    // 1005: Dell Vostro 14 (Dell, 8GB RAM non-matching, $599, stock 3 <= 5 -> LOW_STOCK)
    insertProduct(
        1005L,
        "DELL-VOSTRO-14",
        "Dell Vostro 14 Essential Laptop",
        "កុំព្យូទ័រយួរដៃ Dell Vostro 14",
        "dell-vostro-14",
        "Compact 14-inch essential workstation built for small business productivity and easy commute.",
        "Dell",
        59900L,
        69900L,
        13L,
        4.3,
        12,
        15,
        now.minus(25, ChronoUnit.DAYS));
    insertProductImages(
        1005L,
        "https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=800",
        "https://images.unsplash.com/photo-1593642632823-8f785ba67e45?w=800");
    insertVariant(
        8L,
        1005L,
        "DELL-VOST-8-256",
        "{\"ram\": \"8GB\", \"color\": \"Carbon Gray\", \"storage\": \"256GB\"}",
        59900L,
        3);

    // 1006: Asus ROG Strix G16 (Gaming Laptops 14)
    insertProduct(
        1006L,
        "ASUS-ROG-STRIX",
        "Asus ROG Strix G16 Gaming Laptop",
        "កុំព្យូទ័រយួរដៃហ្គេម Asus ROG Strix G16",
        "asus-rog-strix-g16",
        "Next-gen gaming rig equipped with high refresh rate Nebula display, liquid metal cooling, and RTX graphics.",
        "Asus",
        139900L,
        159900L,
        14L,
        4.8,
        28,
        28,
        now.minus(18, ChronoUnit.DAYS));
    insertProductImages(
        1006L,
        "https://images.unsplash.com/photo-1603302576837-37561b2e2302?w=800",
        "https://images.unsplash.com/photo-1541807084-5c52b6b3adef?w=800");
    insertProductStockNoVariant(1006L, 12);

    // 1007: MSI Katana 15 (Gaming Laptops 14, stock 0)
    insertProduct(
        1007L,
        "MSI-KATANA-15",
        "MSI Katana 15 Gaming Laptop",
        "កុំព្យូទ័រយួរដៃហ្គេម MSI Katana 15",
        "msi-katana-15",
        "Sharpen your game with Intel Core i7 and dedicated discrete graphics in a sleek warrior-inspired chassis.",
        "MSI",
        109900L,
        129900L,
        14L,
        4.4,
        15,
        30,
        now.minus(35, ChronoUnit.DAYS));
    insertProductImages(
        1007L,
        "https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=800",
        "https://images.unsplash.com/photo-1603302576837-37561b2e2302?w=800");
    insertProductStockNoVariant(1007L, 0); // Out of stock

    // 1008: Asus ZenBook 14 (Ultrabooks 15, NEW)
    insertProduct(
        1008L,
        "ASUS-ZENBOOK-14",
        "Asus ZenBook 14 Laptop",
        "កុំព្យូទ័រយួរដៃ Asus ZenBook 14",
        "asus-zenbook-14",
        "Ultraportable laptop with military-grade durability, OLED nano-edge screen, and lightweight aluminum chassis.",
        "Asus",
        89900L,
        99900L,
        15L,
        4.7,
        22,
        38,
        now.minus(4, ChronoUnit.DAYS));
    insertProductImages(
        1008L,
        "https://images.unsplash.com/photo-1541807084-5c52b6b3adef?w=800",
        "https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=800");
    insertVariant(
        9L,
        1008L,
        "ZEN14-16-512",
        "{\"ram\": \"16GB\", \"color\": \"Pine Gray\", \"storage\": \"512GB\"}",
        89900L,
        18);

    // 1009: Apple MacBook Air M3 (Ultrabooks 15)
    insertProduct(
        1009L,
        "MACBOOK-AIR-M3",
        "Apple MacBook Air 13 M3",
        "កុំព្យូទ័រយួរដៃ Apple MacBook Air 13 M3",
        "macbook-air-m3",
        "Supercharged by the M3 chip with incredible efficiency, up to 18 hours of battery life, and fanless silent design.",
        "Apple",
        109900L,
        119900L,
        15L,
        4.9,
        45,
        80,
        now.minus(22, ChronoUnit.DAYS));
    insertProductImages(
        1009L,
        "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800",
        "https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?w=800");
    insertProductStockNoVariant(1009L, 22);

    // ==========================================
    // Category 2: Phones -> Subcategories 11, 12
    // ==========================================
    // 1010: iPhone 15 Pro (Smartphones 11, POPULAR, Flash Sale #2)
    insertProduct(
        1010L,
        "APPLE-IPHONE-15PRO",
        "iPhone 15 Pro Titanium",
        "ទូរស័ព្ទ iPhone 15 Pro",
        "iphone-15-pro",
        "Forged in titanium with aerospace-grade strength, A17 Pro chip, customizable action button, and 48MP pro camera.",
        "Apple",
        99900L,
        109900L,
        11L,
        4.9,
        65,
        120,
        now.minus(40, ChronoUnit.DAYS));
    insertProductImages(
        1010L,
        "https://images.unsplash.com/photo-1510557880182-3d4d3cba35a5?w=800",
        "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=800");
    insertVariant(
        10L,
        1010L,
        "IP15P-NAT-128",
        "{\"color\": \"Natural Titanium\", \"storage\": \"128GB\"}",
        99900L,
        20);
    insertVariant(
        11L,
        1010L,
        "IP15P-BLK-256",
        "{\"color\": \"Black Titanium\", \"storage\": \"256GB\"}",
        109900L,
        15);

    // 1011: Samsung Galaxy S24 Ultra (Smartphones 11, POPULAR)
    insertProduct(
        1011L,
        "SAMSUNG-S24-ULTRA",
        "Samsung Galaxy S24 Ultra",
        "ទូរស័ព្ទ Samsung Galaxy S24 Ultra",
        "samsung-s24-ultra",
        "Galaxy AI is here with circle to search, live translation, embedded S Pen, and 200MP camera system.",
        "Samsung",
        119900L,
        129900L,
        11L,
        4.8,
        42,
        110,
        now.minus(30, ChronoUnit.DAYS));
    insertProductImages(
        1011L,
        "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?w=800",
        "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=800");
    insertVariant(
        12L,
        1011L,
        "S24U-GRY-256",
        "{\"color\": \"Titanium Gray\", \"storage\": \"256GB\"}",
        119900L,
        25);

    // 1012: Xiaomi 14 Pro (Smartphones 11)
    insertProduct(
        1012L,
        "XIAOMI-14-PRO",
        "Xiaomi 14 Pro Leica",
        "ទូរស័ព្ទ Xiaomi 14 Pro",
        "xiaomi-14-pro",
        "Co-engineered with Leica optical lens, Snapdragon 8 Gen 3, and ultra-fast 120W HyperCharge.",
        "Xiaomi",
        79900L,
        89900L,
        11L,
        4.6,
        26,
        60,
        now.minus(45, ChronoUnit.DAYS));
    insertProductImages(
        1012L,
        "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=800",
        "https://images.unsplash.com/photo-1584438784894-089d6a62b8fa?w=800");
    insertProductStockNoVariant(1012L, 15);

    // 1013: Anker PowerCore 20000 (Phone Accessories 12, POPULAR)
    insertProduct(
        1013L,
        "ANKER-POWERBANK-20K",
        "Anker PowerCore 20000mAh Power Bank",
        "ថ្មជំនួយសាកទូរស័ព្ទ Anker 20000mAh",
        "anker-powercore-20000",
        "Ultra-high capacity power bank with PowerIQ technology for rapid device charging on the go.",
        "Anker",
        4500L,
        5500L,
        12L,
        4.8,
        38,
        95,
        now.minus(25, ChronoUnit.DAYS));
    insertProductImages(
        1013L,
        "https://images.unsplash.com/photo-1584438784894-089d6a62b8fa?w=800",
        "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800");
    insertProductStockNoVariant(1013L, 40);

    // 1014: Apple MagSafe Wireless Charger (Phone Accessories 12, stock 4 <= 5 -> LOW_STOCK)
    insertProduct(
        1014L,
        "APPLE-MAGSAFE-CHARGER",
        "Apple MagSafe Wireless Charger",
        "ឆ្នាំងសាកឥតខ្សែ Apple MagSafe",
        "apple-magsafe-charger",
        "Perfectly aligned magnets attach seamlessly to your iPhone for faster wireless charging up to 15W.",
        "Apple",
        3900L,
        4500L,
        12L,
        4.6,
        22,
        50,
        now.minus(35, ChronoUnit.DAYS));
    insertProductImages(
        1014L,
        "https://images.unsplash.com/photo-1584438784894-089d6a62b8fa?w=800",
        "https://images.unsplash.com/photo-1510557880182-3d4d3cba35a5?w=800");
    insertProductStockNoVariant(1014L, 4);

    // ==========================================
    // Category 1: Electronics -> Subcategories 9, 10
    // ==========================================
    // 1015: Sony WH-1000XM5 (Audio 9, NEW, Flash Sale #3)
    insertProduct(
        1015L,
        "SONY-WH1000XM5",
        "Sony WH-1000XM5 Wireless Headphones",
        "កាសបំពងសំឡេង Sony WH-1000XM5",
        "sony-wh1000xm5",
        "Industry-leading noise canceling with two processors and eight microphones for unprecedented sound purity.",
        "Sony",
        34900L,
        39900L,
        9L,
        4.7,
        38,
        85,
        now.minus(12, ChronoUnit.DAYS));
    insertProductImages(
        1015L,
        "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800",
        "https://images.unsplash.com/photo-1484704849700-f032a568e944?w=800");
    insertProductStockNoVariant(1015L, 20);

    // 1016: JBL Flip 6 (Audio 9)
    insertProduct(
        1016L,
        "JBL-FLIP-6",
        "JBL Flip 6 Portable Bluetooth Speaker",
        "ធុងបាសប៊្លូធូស JBL Flip 6",
        "jbl-flip-6-speaker",
        "Bold JBL Original Pro Sound with exceptional clarity, 2-way speaker system, and IP67 waterproof rating.",
        "JBL",
        11900L,
        13900L,
        9L,
        4.6,
        29,
        75,
        now.minus(28, ChronoUnit.DAYS));
    insertProductImages(
        1016L,
        "https://images.unsplash.com/photo-1545454675-3531b543be5d?w=800",
        "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800");
    insertProductStockNoVariant(1016L, 30);

    // 1017: Sony Alpha 7 IV (Cameras 10)
    insertProduct(
        1017L,
        "SONY-ALPHA-A7IV",
        "Sony Alpha 7 IV Full-frame Camera",
        "ម៉ាស៊ីនថត Sony Alpha 7 IV",
        "sony-alpha-a7-iv",
        "33MP full-frame Exmor R sensor with advanced BIONZ XR processing engine and 4K 60p movie recording.",
        "Sony",
        249900L,
        269900L,
        10L,
        4.9,
        18,
        18,
        now.minus(50, ChronoUnit.DAYS));
    insertProductImages(
        1017L,
        "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=800",
        "https://images.unsplash.com/photo-1502920917128-1aa500764cbd?w=800");
    insertProductStockNoVariant(1017L, 8);

    // 1018: DJI Mini 4 Pro (Cameras 10, NEW)
    insertProduct(
        1018L,
        "DJI-MINI-4-PRO",
        "DJI Mini 4 Pro Drone Fly More Combo",
        "ដ្រូន DJI Mini 4 Pro Combo",
        "dji-mini-4-pro",
        "Under 249g lightweight camera drone with omnidirectional obstacle sensing, 4K/60fps HDR video, and 20km transmission.",
        "DJI",
        95900L,
        105900L,
        10L,
        4.8,
        15,
        22,
        now.minus(9, ChronoUnit.DAYS));
    insertProductImages(
        1018L,
        "https://images.unsplash.com/photo-1508614589041-895b88991e3e?w=800",
        "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=800");
    insertProductStockNoVariant(1018L, 10);

    // ==========================================
    // Category 4: Fashion -> Subcategories 16, 17
    // ==========================================
    // 1019: Men's Casual Linen Shirt (Men's Clothing 16, POPULAR)
    insertProduct(
        1019L,
        "KM-LINEN-SHIRT",
        "Men's Casual Linen Shirt",
        "អាវសាច់ក្រណាត់លីណេនបុរស",
        "mens-linen-shirt",
        "Breathable pure linen shirt tailored for tropical comfort and stylish weekend outings in Cambodia.",
        "KhmerMart",
        2900L,
        3500L,
        16L,
        4.4,
        19,
        90,
        now.minus(40, ChronoUnit.DAYS));
    insertProductImages(
        1019L,
        "https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800",
        "https://images.unsplash.com/photo-1617137984095-74e4e5e3613f?w=800");
    insertVariant(13L, 1019L, "SHIRT-WHT-L", "{\"color\": \"White\", \"size\": \"L\"}", 2900L, 45);

    // 1020: Men's Slim Fit Chino Pants (Men's Clothing 16, stock 0)
    insertProduct(
        1020L,
        "KM-CHINO-PANTS",
        "Men's Slim Fit Chino Pants",
        "ខោខូវប៊យខោជើងវែងបុរស",
        "mens-slim-chino-pants",
        "Stretch-cotton chinos providing modern slim silhouette and exceptional flexibility from office to dinner.",
        "KhmerMart",
        3200L,
        3900L,
        16L,
        4.3,
        14,
        35,
        now.minus(30, ChronoUnit.DAYS));
    insertProductImages(
        1020L,
        "https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=800",
        "https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800");
    insertProductStockNoVariant(1020L, 0); // Out of stock

    // 1021: Cambodian Silk Wrap Dress (Women's Clothing 17)
    insertProduct(
        1021L,
        "KM-SILK-DRESS",
        "Handwoven Cambodian Silk Wrap Dress",
        "រ៉ូបសូត្រខ្មែរត្បាញដៃប្រណិត",
        "cambodian-silk-wrap-dress",
        "Authentic Cambodian handwoven raw silk dress created by artisan weavers in Takeo province.",
        "KhmerSilk",
        5900L,
        6900L,
        17L,
        4.7,
        18,
        42,
        now.minus(20, ChronoUnit.DAYS));
    insertProductImages(
        1021L,
        "https://images.unsplash.com/photo-1534126511673-b6899657816a?w=800",
        "https://images.unsplash.com/photo-1445205170230-053b83016050?w=800");
    insertProductStockNoVariant(1021L, 16);

    // 1022: Women's Floral Chiffon Blouse (Women's Clothing 17, BOGO product)
    insertProduct(
        1022L,
        "KM-FLORAL-BLOUSE",
        "Women's Floral Chiffon Blouse",
        "អាវផ្កាសាច់ក្រណាត់ស៊ីផុននារី",
        "women-floral-chiffon-blouse",
        "Delicate floral print chiffon blouse featuring ruffled neckline and lightweight airy drape. Part of Buy 1 Get 1 promotion.",
        "KhmerMart",
        2400L,
        2900L,
        17L,
        4.5,
        20,
        38,
        now.minus(15, ChronoUnit.DAYS));
    insertProductImages(
        1022L,
        "https://images.unsplash.com/photo-1551803091-e20673f15770?w=800",
        "https://images.unsplash.com/photo-1534126511673-b6899657816a?w=800");
    insertProductStockNoVariant(1022L, 35);

    // ==========================================
    // Category 5: Home & Living -> Subcategories 18, 19
    // ==========================================
    // 1023: Philips Essential Airfryer 4.1L (Kitchen 18, NEW, POPULAR)
    insertProduct(
        1023L,
        "PHILIPS-AIR-FRYER",
        "Philips Essential Airfryer 4.1L",
        "ឆ្នាំងបំពងខ្យល់ Philips 4.1L",
        "philips-airfryer-4l",
        "Rapid Air technology produces deliciously crispy food with up to 90 percent less fat.",
        "Philips",
        8900L,
        10900L,
        18L,
        4.8,
        52,
        130,
        now.minus(7, ChronoUnit.DAYS));
    insertProductImages(
        1023L,
        "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?w=800",
        "https://images.unsplash.com/photo-1584269600464-37b1b58a9fe7?w=800");
    insertProductStockNoVariant(1023L, 22);

    // 1024: LocknLock Cookware Set (Kitchen 18)
    insertProduct(
        1024L,
        "LOCKNLOCK-POT-SET",
        "LocknLock Ceramic Non-Stick Cookware Set 3pcs",
        "ឈុតឆ្នាំងខ្ទះ LocknLock ៣មុខ",
        "locknlock-cookware-set",
        "Durable ceramic non-stick coating with induction bottom suitable for all stovetops and healthy low-oil cooking.",
        "LocknLock",
        6500L,
        7900L,
        18L,
        4.6,
        21,
        25,
        now.minus(25, ChronoUnit.DAYS));
    insertProductImages(
        1024L,
        "https://images.unsplash.com/photo-1584269600464-37b1b58a9fe7?w=800",
        "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?w=800");
    insertProductStockNoVariant(1024L, 14);

    // 1025: Artisanal Khmer Cotton Blanket Throw (Decor 19)
    insertProduct(
        1025L,
        "KM-KROMAR-THROWS",
        "Artisanal Khmer Cotton Blanket Throw",
        "ភួយក្រណាត់កប្បាសប្រពៃណីខ្មែរ",
        "artisanal-khmer-cotton-throw",
        "Cozy handloom checkered cotton blanket throw inspired by heritage Cambodian kroma patterns.",
        "KhmerCraft",
        1800L,
        2200L,
        19L,
        4.5,
        17,
        32,
        now.minus(33, ChronoUnit.DAYS));
    insertProductImages(
        1025L,
        "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=800",
        "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?w=800");
    insertProductStockNoVariant(1025L, 28);

    // 1026: Ultrasonic Aromatherapy Diffuser 500ml (Decor 19, stock 2 <= 5 -> LOW_STOCK)
    insertProduct(
        1026L,
        "AROMA-DIFFUSER-500ML",
        "Ultrasonic Aromatherapy Diffuser 500ml",
        "ម៉ាស៊ីនបាញ់ក្លិនក្រអូប 500ml",
        "ultrasonic-essential-oil-diffuser",
        "Quiet ultrasonic cool mist humidifier and fragrance diffuser with 7 ambient LED mood lighting colors.",
        "HomeSpa",
        2800L,
        3500L,
        19L,
        4.4,
        25,
        40,
        now.minus(29, ChronoUnit.DAYS));
    insertProductImages(
        1026L,
        "https://images.unsplash.com/photo-1608571423902-eed4a5ad8108?w=800",
        "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=800");
    insertProductStockNoVariant(1026L, 2);

    // ==========================================
    // Category 6: Beauty -> Subcategories 20, 21
    // ==========================================
    // 1027: COSRX Advanced Snail 96 Essence (Skincare 20, POPULAR)
    insertProduct(
        1027L,
        "COSRX-SNAIL-MUCIN",
        "COSRX Advanced Snail 96 Mucin Power Essence",
        "សេរ៉ូមខ្យង COSRX Snail 96 Mucin",
        "cosrx-advanced-snail-96-essence",
        "Formulated with 96% snail secretion filtrate to deeply hydrate, repair damaged barrier, and boost elasticity.",
        "COSRX",
        1900L,
        2400L,
        20L,
        4.8,
        40,
        88,
        now.minus(24, ChronoUnit.DAYS));
    insertProductImages(
        1027L,
        "https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?w=800",
        "https://images.unsplash.com/photo-1596462502278-27bfdc403348?w=800");
    insertProductStockNoVariant(1027L, 50);

    // 1028: Anua Heartleaf 77% Soothing Toner (Skincare 20)
    insertProduct(
        1028L,
        "ANUA-HEARTLEAF-TONER",
        "Anua Heartleaf 77% Soothing Toner 250ml",
        "ទឹកតប់ស្បែក Anua Heartleaf 77%",
        "anua-heartleaf-77-soothing-toner",
        "Gentle calming toner enriched with 77% heartleaf extract to soothe troubled skin and maintain moisture-oil balance.",
        "Anua",
        2100L,
        2600L,
        20L,
        4.7,
        32,
        65,
        now.minus(16, ChronoUnit.DAYS));
    insertProductImages(
        1028L,
        "https://images.unsplash.com/photo-1596462502278-27bfdc403348?w=800",
        "https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?w=800");
    insertProductStockNoVariant(1028L, 35);

    // 1029: Chanel Chance Eau Tendre 100ml (Fragrances 21)
    insertProduct(
        1029L,
        "CHANEL-CHANCE-100ML",
        "Chanel Chance Eau Tendre Eau de Parfum 100ml",
        "ទឹកអប់ Chanel Chance Eau Tendre 100ml",
        "chanel-chance-eau-tendre-100ml",
        "Delicate fruity-floral fragrance revealing an intensified feminine whirlwind of tenderness.",
        "Chanel",
        16500L,
        18500L,
        21L,
        4.9,
        24,
        26,
        now.minus(48, ChronoUnit.DAYS));
    insertProductImages(
        1029L,
        "https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?w=800",
        "https://images.unsplash.com/photo-1541643600914-78b084683601?w=800");
    insertProductStockNoVariant(1029L, 10);

    // 1030: L'Oreal Paris Revitalift Serum (Fragrances & Body 21)
    insertProduct(
        1030L,
        "LOREAL-REVITALIFT-SERUM",
        "L'Oreal Paris Revitalift 1.5% Hyaluronic Acid Serum",
        "សេរ៉ូម L'Oreal Paris Revitalift Hyaluronic Acid",
        "loreal-revitalift-hyaluronic-acid-serum",
        "Dermatologist validated formula with concentrated macro and micro hyaluronic acid for visibly plumped skin.",
        "L'Oreal",
        2600L,
        3200L,
        21L,
        4.6,
        29,
        72,
        now.minus(38, ChronoUnit.DAYS));
    insertProductImages(
        1030L,
        "https://images.unsplash.com/photo-1541643600914-78b084683601?w=800",
        "https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?w=800");
    insertProductStockNoVariant(1030L, 40);

    // ==========================================
    // Category 7: Groceries -> Subcategories 22, 23
    // ==========================================
    // 1031: Angkor Jasmine Rice 5kg (Rice 22, POPULAR #1, Flash Sale #4)
    insertProduct(
        1031L,
        "ANGKOR-RICE-5KG",
        "Angkor Premium Phka Rumduol Jasmine Rice 5kg",
        "អង្ករផ្ការំដួលប្រណិតអង្គរ ៥គីឡូក្រាម",
        "angkor-jasmine-rice-5kg",
        "Award-winning authentic Cambodian fragrant Jasmine rice, harvest-fresh and naturally aromatic.",
        "Angkor Harvest",
        1200L,
        1500L,
        22L,
        4.9,
        88,
        320,
        now.minus(55, ChronoUnit.DAYS));
    insertProductImages(
        1031L,
        "https://images.unsplash.com/photo-1586201375761-83865001e31c?w=800",
        "https://images.unsplash.com/photo-1542838132-92c53300491e?w=800");
    insertVariant(14L, 1031L, "RICE-5KG", "{\"weight\": \"5kg\"}", 1200L, 150);

    // 1032: Organic Khmer Jasmine Brown Rice 2kg (Rice 22)
    insertProduct(
        1032L,
        "KHMER-BROWN-RICE-2KG",
        "Organic Khmer Jasmine Brown Rice 2kg",
        "អង្ករសម្រូបផ្ការំដួលសរីរាង្គ ២គីឡូក្រាម",
        "organic-khmer-brown-rice-2kg",
        "Nutritious whole grain brown jasmine rice high in fiber and minerals, grown without chemical pesticides in Battambang.",
        "Angkor Harvest",
        650L,
        800L,
        22L,
        4.6,
        22,
        45,
        now.minus(35, ChronoUnit.DAYS));
    insertProductImages(
        1032L,
        "https://images.unsplash.com/photo-1586201375761-83865001e31c?w=800",
        "https://images.unsplash.com/photo-1542838132-92c53300491e?w=800");
    insertProductStockNoVariant(1032L, 60);

    // 1033: Mondulkiri Highland Coffee Beans 500g (Snacks 23)
    insertProduct(
        1033L,
        "MONDULKIRI-COFFEE-500G",
        "Mondulkiri Highland Roasted Arabica Coffee Beans 500g",
        "កាហ្វេគ្រាប់អារ៉ាប៊ីកាមណ្ឌលគិរី ៥០០ក្រាម",
        "mondulkiri-roasted-coffee-beans-500g",
        "100% pure high-altitude roasted Arabica beans cultivated in the cool volcanic highlands of Mondulkiri province.",
        "Mondulkiri Roast",
        950L,
        1200L,
        23L,
        4.8,
        36,
        78,
        now.minus(19, ChronoUnit.DAYS));
    insertProductImages(
        1033L,
        "https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=800",
        "https://images.unsplash.com/photo-1586201375761-83865001e31c?w=800");
    insertProductStockNoVariant(1033L, 45);

    // 1034: Kampot Black Pepper GI 100g (Snacks 23, stock 5 <= 5 -> LOW_STOCK, POPULAR)
    insertProduct(
        1034L,
        "KAMPOT-PEPPER-BLACK-100G",
        "Authentic Kampot Black Pepper GI Certified 100g",
        "ម្រេចខ្មៅកំពតពិតៗ GI ១០០ក្រាម",
        "kampot-black-pepper-gi-100g",
        "World-renowned Geographical Indication Kampot black peppercorns offering complex floral aroma and lingering heat.",
        "Kampot Jewels",
        550L,
        700L,
        23L,
        4.9,
        45,
        92,
        now.minus(42, ChronoUnit.DAYS));
    insertProductImages(
        1034L,
        "https://images.unsplash.com/photo-1509358271058-acd22cc93898?w=800",
        "https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=800");
    insertProductStockNoVariant(1034L, 5);

    // 1035: Cambodian Dried Mango Slices 200g (Snacks 23)
    insertProduct(
        1035L,
        "DRIED-MANGO-CAMBODIA-200G",
        "Natural Cambodian Dried Mango Slices 200g",
        "ដំណាប់ស្វាយកែវរមៀតធម្មជាតិ ២០០ក្រាម",
        "natural-cambodian-dried-mango-slices",
        "Sweet and tangy dried Keo Romeat mango slices with zero added sugar or artificial preservatives.",
        "Angkor Treats",
        380L,
        480L,
        23L,
        4.7,
        34,
        82,
        now.minus(27, ChronoUnit.DAYS));
    insertProductImages(
        1035L,
        "https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=800",
        "https://images.unsplash.com/photo-1509358271058-acd22cc93898?w=800");
    insertProductStockNoVariant(1035L, 80);

    // ==========================================
    // Category 8: Books -> Subcategories 24, 25
    // ==========================================
    // 1036: Angkor and the Khmer Civilization (Khmer Literature 24)
    insertProduct(
        1036L,
        "BOOK-ANGKOR-EMPIRE",
        "Angkor and the Khmer Civilization",
        "សៀវភៅ អង្គរ និង អរិយធម៌ខ្មែរ",
        "angkor-and-the-khmer-civilization-book",
        "Comprehensive illustrated guide to the history, architecture, and religious symbolism of the Angkorian empire.",
        "River Books",
        2800L,
        3200L,
        24L,
        4.9,
        25,
        34,
        now.minus(45, ChronoUnit.DAYS));
    insertProductImages(
        1036L,
        "https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=800",
        "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=800");
    insertProductStockNoVariant(1036L, 25);

    // 1037: Treasury of Cambodian Folk Tales (Khmer Literature 24)
    insertProduct(
        1037L,
        "BOOK-KHMER-TALES",
        "Treasury of Cambodian Folk Tales (Bilingual)",
        "សៀវភៅ រឿងព្រេងនិទានខ្មែរបុរាណ",
        "classic-cambodian-folk-tales",
        "Rich illustrated bilingual compilation of classic Cambodian folktales passed down through generations of oral history.",
        "Phnom Penh Publishing",
        1500L,
        1800L,
        24L,
        4.8,
        19,
        28,
        now.minus(32, ChronoUnit.DAYS));
    insertProductImages(
        1037L,
        "https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=800",
        "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=800");
    insertProductStockNoVariant(1037L, 30);

    // 1038: Atomic Habits Khmer Edition (Business 25, NEW)
    insertProduct(
        1038L,
        "BOOK-ATOMIC-HABITS-KM",
        "Atomic Habits (Khmer Translation Edition)",
        "សៀវភៅ ទម្លាប់ផ្លាស់ប្តូរជីវិត (Atomic Habits)",
        "atomic-habits-khmer-edition",
        "The definitive guide to breaking bad behaviors and adopting good habits with tiny, proven lifestyle tweaks.",
        "Mindset Books",
        1400L,
        1600L,
        25L,
        4.9,
        42,
        85,
        now.minus(11, ChronoUnit.DAYS));
    insertProductImages(
        1038L,
        "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=800",
        "https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=800");
    insertProductStockNoVariant(1038L, 50);

    // 1039: The Psychology of Money Khmer Edition (Business 25)
    insertProduct(
        1039L,
        "BOOK-PSYCHOLOGY-MONEY-KM",
        "The Psychology of Money (Khmer Translation Edition)",
        "សៀវភៅ ចិត្តវិទ្យានៃលុយ (Psychology of Money)",
        "the-psychology-of-money-khmer-edition",
        "Timeless lessons on wealth, greed, and happiness exploring how people make sense of financial freedom.",
        "Wealth Media",
        1400L,
        1600L,
        25L,
        4.8,
        30,
        70,
        now.minus(21, ChronoUnit.DAYS));
    insertProductImages(
        1039L,
        "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=800",
        "https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=800");
    insertProductStockNoVariant(1039L, 45);

    // 1040: Start With Why Khmer Edition (Business 25, stock 0)
    insertProduct(
        1040L,
        "BOOK-START-WITH-WHY-KM",
        "Start With Why (Khmer Translation Edition)",
        "សៀវភៅ ចាប់ផ្តើមដោយសំណួរថា ហេតុអ្វី",
        "start-with-why-khmer-edition",
        "Discover how great leaders inspire everyone to take action by uncovering their core organizational purpose.",
        "Leader Books",
        1350L,
        1550L,
        25L,
        4.7,
        18,
        15,
        now.minus(52, ChronoUnit.DAYS));
    insertProductImages(
        1040L,
        "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=800",
        "https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=800");
    insertProductStockNoVariant(1040L, 0); // Out of stock
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
        VALUES (?, NULL, ?, 0, 5, false, 0, now())
        ON CONFLICT (product_id, COALESCE(variant_id, 0)) DO UPDATE SET quantity_on_hand = EXCLUDED.quantity_on_hand
        """,
        productId,
        quantity);
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

  private void seedOrdersAndReviews() {
    Instant now = Instant.now();

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

    for (int i = 1; i <= statuses.length; i++) {
      OrderStatus status = statuses[i - 1];
      String orderNum = "KM-ORD-202610-" + String.format("%03d", i);
      Long userId = (long) (3 + (i % 3)); // Cycles among customers 3, 4, 5
      Instant placedAt =
          now.minus(30 - (i % 28), ChronoUnit.DAYS).minus(i * 37, ChronoUnit.MINUTES);

      PaymentStatus paymentStatus = resolvePaymentStatus(status);
      String paymentMethod = (i % 4 == 0) ? "COD" : "KHQR";
      Long productId = 1001L + (i % 40);
      Long subtotalCents = 2500L * ((i % 5) + 1);
      Long shippingCents = 150L;
      Long totalCents = subtotalCents + shippingCents;

      // 1. Insert order
      jdbcTemplate.update(
          """
          INSERT INTO orders (id, order_number, user_id, status, payment_status, payment_method,
                              subtotal_cents, discount_cents, shipping_cents, total_cents,
                              currency, shipping_address, shipping_method_code, placed_at, confirmed_at, shipped_at, delivered_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 'USD',
                  '{"recipientName": "Demo Customer", "phone": "+85512345678", "line1": "Street 2004", "province": "Phnom Penh"}'::jsonb,
                  'STANDARD_PP', ?, ?, ?, ?)
          ON CONFLICT (id) DO NOTHING
          """,
          (long) i,
          orderNum,
          userId,
          status.name(),
          paymentStatus.name(),
          paymentMethod,
          subtotalCents,
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
              : null);

      // 2. Insert order items
      jdbcTemplate.update(
          """
          INSERT INTO order_items (id, order_id, product_id, variant_id, product_name_snapshot, sku_snapshot, unit_price_cents, quantity, line_total_cents)
          VALUES (?, ?, ?, NULL, 'KhmerMart Quality Item', 'ITEM-SKU-' || ?, ?, 1, ?)
          ON CONFLICT (id) DO NOTHING
          """,
          (long) i,
          (long) i,
          productId,
          productId,
          subtotalCents,
          subtotalCents);

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

    // Seed realistic reviews by the 3 seed customers on delivered items
    // Using distinct user_id & product_id pairs to strictly honor UNIQUE(user_id, product_id)
    int reviewId = 1;
    Long[] customerIds = {3L, 4L, 5L};
    for (int cIdx = 0; cIdx < customerIds.length; cIdx++) {
      Long uid = customerIds[cIdx];
      // Each customer reviews 8 different products
      for (int pIdx = 1; pIdx <= 8; pIdx++) {
        Long prodId = 1000L + (long) (cIdx * 8 + pIdx);
        int rating = 4 + ((reviewId % 2)); // 4 or 5 stars
        String reviewStatus = (reviewId % 4 == 0) ? "PENDING" : "APPROVED";
        String adminReply =
            (reviewId % 3 == 0)
                ? "Thank you for shopping with KhmerMart! We appreciate your support."
                : null;

        jdbcTemplate.update(
            """
            INSERT INTO reviews (id, product_id, user_id, rating, title, body, status, admin_reply, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'Exceptional Experience', 'Authentic product with prompt delivery in Cambodia. Exceeded expectations!', ?, ?, now() - interval '5 days', now() - interval '5 days')
            ON CONFLICT (user_id, product_id) DO NOTHING
            """,
            (long) (reviewId++),
            prodId,
            uid,
            rating,
            reviewStatus,
            adminReply);
      }
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
