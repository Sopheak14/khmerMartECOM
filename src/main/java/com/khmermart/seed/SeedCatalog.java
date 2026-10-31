package com.khmermart.seed;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Khmer-only demo catalog: single source of truth for {@link DataSeeder}, the integration tests
 * and the docs. All money is in USD cents. Product descriptions hold the English text followed by
 * the Khmer text (the schema has a single description column).
 */
public final class SeedCatalog {

  private SeedCatalog() {}

  // ---- categories (8 roots, ids 1..8) ----
  public static final long CAT_SILK = 1L;
  public static final long CAT_SPICES = 2L;
  public static final long CAT_RICE = 3L;
  public static final long CAT_COFFEE_TEA = 4L;
  public static final long CAT_SNACKS = 5L;
  public static final long CAT_HANDICRAFTS = 6L;
  public static final long CAT_BEAUTY = 7L;
  public static final long CAT_BOOKS = 8L;

  public record Category(
      long id, String name, String nameKm, String slug, String imageUrl, boolean realImage) {}

  private static final String PH = "https://placehold.co/500x500/F3EBDD/5B4636/png?text=";

  private static String placeholder(String label) {
    return PH + label.replace(' ', '+').replace("&", "%26");
  }

  public static final List<Category> CATEGORIES =
      List.of(
          new Category(
              CAT_SILK,
              "Silk & Textiles",
              "សូត្រ និងក្រណាត់",
              "silk-textiles",
              placeholder("Silk & Textiles"),
              false),
          new Category(
              CAT_SPICES,
              "Spices & Sauces",
              "គ្រឿងទេស និងទឹកជ្រលក់",
              "spices-sauces",
              "https://images.unsplash.com/photo-1509358271058-acd22cc93898?w=500",
              true),
          new Category(
              CAT_RICE,
              "Rice & Grains",
              "អង្ករ និងធញ្ញជាតិ",
              "rice-grains",
              "https://images.unsplash.com/photo-1586201375761-83865001e31c?w=500",
              true),
          new Category(
              CAT_COFFEE_TEA,
              "Coffee & Tea",
              "កាហ្វេ និងតែ",
              "coffee-tea",
              placeholder("Coffee & Tea"),
              false),
          new Category(
              CAT_SNACKS,
              "Snacks & Dried Fruit",
              "អាហារសម្រន់ និងផ្លែឈើស្ងួត",
              "snacks",
              placeholder("Snacks & Dried Fruit"),
              false),
          new Category(
              CAT_HANDICRAFTS,
              "Handicrafts",
              "សិប្បកម្ម",
              "handicrafts",
              placeholder("Handicrafts"),
              false),
          new Category(
              CAT_BEAUTY,
              "Beauty & Wellness",
              "សម្រស់ និងសុខភាព",
              "beauty-wellness",
              placeholder("Beauty & Wellness"),
              false),
          new Category(
              CAT_BOOKS,
              "Books & Culture",
              "សៀវភៅ និងវប្បធម៌",
              "books-culture",
              "https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=500",
              true));

  // ---- hero product 1001 (Takeo Hol Silk Sampot): 4 variants = color x grade ----
  public static final long HERO_ID = 1001L;
  public static final long HERO_STANDARD_CENTS = 14900L;
  public static final long HERO_PREMIUM_CENTS = 19900L;
  public static final long HERO_FLASH_CENTS = 12900L;
  public static final int HERO_VARIANT_STOCK = 50;

  /** Approved seed reviews on the hero product: ratings 5, 5, 4 (average 4.7). */
  public static final int HERO_REVIEW_COUNT = 3;

  /**
   * Variant ids: 1 Ruby Red/Standard, 2 Ruby Red/Premium, 3 Royal Blue/Standard, 4 Royal
   * Blue/Premium.
   */
  public static final long HERO_RED_STANDARD = 1L;

  public static final long HERO_RED_PREMIUM = 2L;
  public static final long HERO_BLUE_STANDARD = 3L;
  public static final long HERO_BLUE_PREMIUM = 4L;

  // ---- 1013 Phka Rumduol Jasmine Rice: variants by weight ----
  public static final long RICE_ID = 1013L;
  public static final long RICE_5KG_CENTS = 1200L;
  public static final long RICE_10KG_CENTS = 2200L;
  public static final long RICE_5KG_VARIANT = 5L;
  public static final long RICE_10KG_VARIANT = 6L;

  public record Variant(
      long id, long productId, String sku, String attributesJson, long priceCents, int stock) {}

  public static final List<Variant> VARIANTS =
      List.of(
          new Variant(
              HERO_RED_STANDARD,
              HERO_ID,
              "HOL-SAMPOT-RED-STD",
              "{\"color\": \"Ruby Red\", \"grade\": \"Standard\"}",
              HERO_STANDARD_CENTS,
              HERO_VARIANT_STOCK),
          new Variant(
              HERO_RED_PREMIUM,
              HERO_ID,
              "HOL-SAMPOT-RED-PRM",
              "{\"color\": \"Ruby Red\", \"grade\": \"Premium\"}",
              HERO_PREMIUM_CENTS,
              HERO_VARIANT_STOCK),
          new Variant(
              HERO_BLUE_STANDARD,
              HERO_ID,
              "HOL-SAMPOT-BLU-STD",
              "{\"color\": \"Royal Blue\", \"grade\": \"Standard\"}",
              HERO_STANDARD_CENTS,
              HERO_VARIANT_STOCK),
          new Variant(
              HERO_BLUE_PREMIUM,
              HERO_ID,
              "HOL-SAMPOT-BLU-PRM",
              "{\"color\": \"Royal Blue\", \"grade\": \"Premium\"}",
              HERO_PREMIUM_CENTS,
              HERO_VARIANT_STOCK),
          new Variant(5L, 1013L, "RICE-PRD-5KG", "{\"weight\": \"5 kg\"}", RICE_5KG_CENTS, 150),
          new Variant(6L, 1013L, "RICE-PRD-10KG", "{\"weight\": \"10 kg\"}", RICE_10KG_CENTS, 150));

  // ---- flash sale (promotion 1, 7 days from seed time) ----
  public static final String FLASH_NAME = "Flash Sale 24H";
  public static final long PEPPER_ID = 1006L;
  public static final long PEPPER_CENTS = 950L;
  public static final long PEPPER_FLASH_CENTS = 790L;
  public static final long MANGO_ID = 1022L;
  public static final long MANGO_CENTS = 400L;
  public static final long MANGO_FLASH_CENTS = 320L;

  /** Distinct products on the flash sale (hero via its Standard variants, pepper, mango). */
  public static final List<Long> FLASH_PRODUCT_IDS = List.of(HERO_ID, PEPPER_ID, MANGO_ID);

  // ---- seasonal banner (promotion 2): 10% off Spices & Sauces ----
  public static final String SEASONAL_NAME = "Pchum Ben Festival Sale";
  public static final String SEASONAL_NAME_KM = "ពិធីបុណ្យភ្ជុំបិណ្ឌ";
  public static final int SEASONAL_PERCENT = 10;
  public static final String SEASONAL_BANNER =
      "https://placehold.co/1200x400/F3EBDD/5B4636/png?text=Pchum+Ben+Festival+Sale";
  public static final long FIRST_SPICE_ID = 1006L;
  public static final long LAST_SPICE_ID = 1012L;

  // ---- coupons / shipping / rate (kept from the previous seed) ----
  public static final String COUPON_SAVE5 = "SAVE5";
  public static final long SAVE5_CENTS = 500L;
  public static final long SAVE5_MIN_ORDER_CENTS = 3500L;
  public static final long USD_KHR_RATE = 4100L;
  public static final long FREE_SHIPPING_OVER_CENTS = 5000L;
  public static final long STANDARD_SHIPPING_CENTS = 150L;

  /** Demo order: hero Standard, flash sale and SAVE5 -> 14900 - 2000 - 500. */
  public static final long DEMO_ORDER_FLASH_DISCOUNT_CENTS = HERO_STANDARD_CENTS - HERO_FLASH_CENTS;

  public static final long DEMO_ORDER_TOTAL_CENTS =
      HERO_FLASH_CENTS - SAVE5_CENTS; // free standard shipping: subtotal after promos >= 5000
  public static final long DEMO_ORDER_TOTAL_KHR = DEMO_ORDER_TOTAL_CENTS * USD_KHR_RATE / 100;

  // ---- low stock (dashboard "Low stock" table / inventory highlight); threshold 5 for every row
  // ----
  public static final long LOW_STOCK_RUNNER_ID = 1004L;
  public static final long LOW_STOCK_SILVER_BOX_ID = 1027L;
  public static final int LOW_STOCK_1004 = 4;
  public static final int LOW_STOCK_1027 = 3;
  public static final int LOW_STOCK_THRESHOLD = 5;

  // ---- BOGO (promotion 3, 7 days from seed time) on Lemongrass Soap, no variants ----
  public static final String BOGO_NAME = "Buy 1 Get 1 Lemongrass Soap";
  public static final String BOGO_NAME_KM = "ទិញ ១ ថែម ១ សាប៊ូស្លឹកគ្រៃ";
  public static final long BOGO_PRODUCT_ID = 1033L;
  public static final long BOGO_PRODUCT_CENTS = 350L;

  // ---- products ----
  public record Product(
      long id,
      String sku,
      String name,
      String nameKm,
      String brand,
      long priceCents,
      long categoryId,
      int stock,
      int soldCount,
      int ageDays,
      String descEn,
      String descKm) {

    public String slug() {
      return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    public String description() {
      return descEn + "\n\n" + descKm;
    }

    public boolean hasVariants() {
      return id == HERO_ID || id == RICE_ID;
    }
  }

  private static Product p(
      long id,
      String sku,
      String name,
      String nameKm,
      String brand,
      long priceCents,
      long categoryId,
      int stock,
      int soldCount,
      int ageDays,
      String descEn,
      String descKm) {
    return new Product(
        id,
        sku,
        name,
        nameKm,
        brand,
        priceCents,
        categoryId,
        stock,
        soldCount,
        ageDays,
        descEn,
        descKm);
  }

  /**
   * Exactly 38 products, ids 1001..1040 in order with gaps at 1011 and 1024 (perishables removed,
   * ids never renumbered). Variant products carry the cheapest price.
   */
  public static final List<Product> PRODUCTS =
      List.of(
          // ---- Silk & Textiles ----
          p(
              1001,
              "SLK-1001",
              "Takeo Hol Silk Sampot",
              "សំពត់ហូលសូត្រ តាកែវ",
              "Takeo Weavers",
              HERO_STANDARD_CENTS,
              CAT_SILK,
              0,
              120,
              40,
              "Hand-woven hol silk sampot from Takeo, worn at weddings and ceremonies.",
              "សំពត់ហូលសូត្រត្បាញដៃពីខេត្តតាកែវ សម្រាប់ពាក់ក្នុងពិធីមង្គលការ និងពិធីបុណ្យ។"),
          p(
              1002,
              "SLK-1002",
              "Cotton Krama, Red Check",
              "ក្រមាកប្បាស ក្រឡាក្រហម",
              "Kampong Cham Weavers",
              600,
              CAT_SILK,
              200,
              310,
              55,
              "Everyday checked cotton krama from Kampong Cham, used as a scarf, sling or towel.",
              "ក្រមាកប្បាសក្រឡាពីខេត្តកំពង់ចាម ប្រើជាក្រមាកាត់ក ក្រវាត់ ឬកន្សែង។"),
          p(
              1003,
              "SLK-1003",
              "Silk Krama Gift Box",
              "ក្រមាសូត្រ ប្រអប់កាដូ",
              "Takeo Weavers",
              2800,
              CAT_SILK,
              60,
              85,
              30,
              "Silk krama from Takeo presented in a gift box, a thoughtful Cambodian souvenir.",
              "ក្រមាសូត្រពីខេត្តតាកែវ ក្នុងប្រអប់កាដូ សមស្របជាអនុស្សាវរីយ៍ខ្មែរ។"),
          p(
              1004,
              "SLK-1004",
              "Hol Silk Table Runner",
              "កម្រាលតុសូត្រហូល",
              "Takeo Weavers",
              4500,
              CAT_SILK,
              LOW_STOCK_1004,
              22,
              25,
              "Table runner woven in traditional hol silk patterns, for dining tables.",
              "កម្រាលតុត្បាញពីសូត្រហូលតាមលំនាំប្រពៃណី សម្រាប់តុបតែងតុបាយ។"),
          p(
              1005,
              "SLK-1005",
              "Phamuong Silk Fabric 2 m",
              "ក្រណាត់សូត្រផាមួង ២ ម៉ែត្រ",
              "Kandal Silk House",
              6500,
              CAT_SILK,
              25,
              18,
              20,
              "Two metres of plain Phamuong silk from Kandal, for tailoring blouses and skirts.",
              "ក្រណាត់សូត្រផាមួងប្រវែង ២ ម៉ែត្រ ពីខេត្តកណ្ដាល សម្រាប់កាត់ដេរអាវ និងសំពត់។"),
          // ---- Spices & Sauces ----
          p(
              1006,
              "SPC-1006",
              "Kampot Black Pepper 100 g",
              "ម្រេចខ្មៅកំពត ១០០ ក្រាម",
              "Kampot Farms",
              PEPPER_CENTS,
              CAT_SPICES,
              150,
              240,
              45,
              "Sun-dried black peppercorns grown in Kampot, with a bold aroma for grills and stir-fries.",
              "ម្រេចខ្មៅសម្ងួតថ្ងៃដាំនៅខេត្តកំពត ក្លិនឈ្ងុយ សម្រាប់ម្ហូបអាំង និងឆា។"),
          p(
              1007,
              "SPC-1007",
              "Kampot Red Pepper 100 g",
              "ម្រេចក្រហមកំពត ១០០ ក្រាម",
              "Kampot Farms",
              1250,
              CAT_SPICES,
              120,
              130,
              45,
              "Fully ripened red peppercorns from Kampot with a sweet, fruity heat.",
              "ម្រេចក្រហមទុំពេញពីខេត្តកំពត រសជាតិហឹរផ្អែមក្លិនផ្លែឈើ។"),
          p(
              1008,
              "SPC-1008",
              "Kampot Pepper Gift Set",
              "ឈុតកាដូម្រេចកំពត",
              "Kampot Farms",
              3500,
              CAT_SPICES,
              50,
              40,
              35,
              "Gift set of Kampot black and red pepper, packed for gifting.",
              "ឈុតកាដូម្រេចខ្មៅ និងម្រេចក្រហមកំពត វេចខ្ចប់សម្រាប់ជូនអំណោយ។"),
          p(
              1009,
              "SPC-1009",
              "Kampot Salt Flower 200 g",
              "ផ្កាអំបិលកំពត ២០០ ក្រាម",
              "Kampot Salt Fields",
              650,
              CAT_SPICES,
              100,
              75,
              40,
              "Hand-harvested salt flower from the Kampot salt fields, a finishing salt for grilled dishes.",
              "ផ្កាអំបិលប្រមូលដោយដៃពីស្រែអំបិលកំពត សម្រាប់ប្រោះលើម្ហូបអាំង។"),
          p(
              1010,
              "SPC-1010",
              "Kampong Speu Palm Sugar 500 g",
              "ស្ករត្នោតកំពង់ស្ពឺ ៥០០ ក្រាម",
              "Kampong Speu Sugar Co-op",
              450,
              CAT_SPICES,
              200,
              260,
              60,
              "Pure palm sugar cooked down in Kampong Speu, used in curries and desserts.",
              "ស្ករត្នោតសុទ្ធដែលកូរនៅខេត្តកំពង់ស្ពឺ ប្រើក្នុងសម្ល និងនំ។"),
          p(
              1012,
              "SPC-1012",
              "Kampot Fish Sauce 700 ml",
              "ទឹកត្រីកំពត ៧០០ មីលីលីត្រ",
              "Kampot Sauce Works",
              300,
              CAT_SPICES,
              180,
              205,
              50,
              "Traditional fish sauce from Kampot, for dipping and cooking.",
              "ទឹកត្រីប្រពៃណីពីខេត្តកំពត សម្រាប់ជ្រលក់ និងចម្អិនម្ហូប។"),
          // ---- Rice & Grains ----
          p(
              1013,
              "RCE-1013",
              "Phka Rumduol Jasmine Rice",
              "អង្ករផ្ការំដួល",
              "Battambang Rice Mill",
              RICE_5KG_CENTS,
              CAT_RICE,
              0,
              420,
              70,
              "Soft, fragrant Phka Rumduol jasmine rice from Battambang for daily meals, in 5 kg and 10 kg bags.",
              "អង្ករផ្ការំដួលក្រអូប ទន់ ពីខេត្តបាត់ដំបង សម្រាប់ញ៉ាំជារៀងរាល់ថ្ងៃ មានកញ្ចប់ ៥ និង ១០ គីឡូក្រាម។"),
          p(
              1014,
              "RCE-1014",
              "Organic Red Rice 2 kg",
              "អង្ករក្រហមសរីរាង្គ ២ គីឡូក្រាម",
              "Preah Vihear Organic Farms",
              700,
              CAT_RICE,
              80,
              95,
              35,
              "Unpolished organic red rice from Preah Vihear, nutty and rich in fibre.",
              "អង្ករក្រហមមិនកិនស្អាតសរីរាង្គពីខេត្តព្រះវិហារ សម្បូរជាតិសរសៃ។"),
          p(
              1015,
              "RCE-1015",
              "Sticky Rice 2 kg",
              "អង្ករដំណើប ២ គីឡូក្រាម",
              "Kratie Farms",
              400,
              CAT_RICE,
              100,
              150,
              50,
              "Glutinous rice from Kratie, for sticky rice with mango and Khmer sweets.",
              "អង្ករដំណើបពីខេត្តក្រចេះ សម្រាប់បាយដំណើបស្វាយ និងនំខ្មែរ។"),
          p(
              1016,
              "RCE-1016",
              "Dried Num Banh Chok Noodles 500 g",
              "នំបញ្ចុកស្ងួត ៥០០ ក្រាម",
              "Siem Reap Kitchen",
              300,
              CAT_RICE,
              120,
              140,
              28,
              "Dried rice noodles from Siem Reap for num banh chok with fish gravy.",
              "នំបញ្ចុកស្ងួតពីខេត្តសៀមរាប សម្រាប់ធ្វើនំបញ្ចុកទឹកសម្លត្រី។"),
          // ---- Coffee & Tea ----
          p(
              1017,
              "CFT-1017",
              "Mondulkiri Arabica Beans 250 g",
              "កាហ្វេអារ៉ាប៊ីកា មណ្ឌលគិរី ២៥០ ក្រាម",
              "Mondulkiri Highlands Roasters",
              850,
              CAT_COFFEE_TEA,
              90,
              110,
              22,
              "Medium-roast arabica beans grown in the Mondulkiri highlands.",
              "គ្រាប់កាហ្វេអារ៉ាប៊ីកាអាំងកម្រិតមធ្យម ដាំនៅតំបន់ខ្ពង់រាបមណ្ឌលគិរី។"),
          p(
              1018,
              "CFT-1018",
              "Ratanakiri Robusta Ground 500 g",
              "កាហ្វេរ៉ូប៊ូស្តា រតនគិរី ៥០០ ក្រាម",
              "Ratanakiri Coffee Co-op",
              700,
              CAT_COFFEE_TEA,
              90,
              100,
              26,
              "Strong ground robusta from Ratanakiri, made for Khmer iced coffee.",
              "កាហ្វេរ៉ូប៊ូស្តាកិនម្សៅពីខេត្តរតនគិរី ខ្លាំង សម្រាប់កាហ្វេទឹកកក។"),
          p(
              1019,
              "CFT-1019",
              "Lotus Tea 50 g",
              "តែផ្កាឈូក ៥០ ក្រាម",
              "Siem Reap Tea House",
              600,
              CAT_COFFEE_TEA,
              70,
              60,
              18,
              "Dried lotus flower tea from Siem Reap with a gentle floral aroma.",
              "តែផ្កាឈូកស្ងួតពីខេត្តសៀមរាប ក្លិនផ្កាក្រអូបស្រាល។"),
          p(
              1020,
              "CFT-1020",
              "Lemongrass Tea, 30 bags",
              "តែស្លឹកគ្រៃ ៣០ កញ្ចប់",
              "Kampong Cham Tea House",
              450,
              CAT_COFFEE_TEA,
              80,
              90,
              24,
              "Lemongrass tea in 30 bags from Kampong Cham, served hot or iced.",
              "តែស្លឹកគ្រៃ ៣០ កញ្ចប់ ពីខេត្តកំពង់ចាម ផឹកក្តៅ ឬត្រជាក់។"),
          // ---- Snacks & Dried Fruit ----
          p(
              1021,
              "SNK-1021",
              "Kampong Thom Cashews 500 g",
              "គ្រាប់ស្វាយចន្ទី កំពង់ធំ ៥០០ ក្រាម",
              "Kampong Thom Orchards",
              900,
              CAT_SNACKS,
              100,
              160,
              32,
              "Roasted cashew nuts from Kampong Thom, crunchy and lightly salted.",
              "គ្រាប់ស្វាយចន្ទីអាំងពីខេត្តកំពង់ធំ ស្រួយ និងប្រៃស្រាល។"),
          p(
              1022,
              "SNK-1022",
              "Dried Mango 200 g",
              "ស្វាយក្រៀម ២០០ ក្រាម",
              "Kampong Cham Orchards",
              MANGO_CENTS,
              CAT_SNACKS,
              120,
              230,
              38,
              "Sweet and tangy dried mango slices from Kampong Cham.",
              "ស្វាយក្រៀមហាន់ជាចិញ្ចៀន ផ្អែមជូរ ពីខេត្តកំពង់ចាម។"),
          p(
              1023,
              "SNK-1023",
              "Banana Chips 200 g",
              "ចេកចៀន ២០០ ក្រាម",
              "Battambang Snacks",
              250,
              CAT_SNACKS,
              150,
              270,
              20,
              "Crispy fried banana chips from Battambang.",
              "ចេកចៀនស្រួយពីខេត្តបាត់ដំបង។"),
          p(
              1025,
              "SNK-1025",
              "Durian Candy 150 g",
              "ស្ករគ្រាប់ធុរេន ១៥០ ក្រាម",
              "Kampot Sweets",
              350,
              CAT_SNACKS,
              80,
              70,
              12,
              "Chewy durian candy made from Kampot durian.",
              "ស្ករគ្រាប់ធុរេនទន់ ធ្វើពីធុរេនកំពត។"),
          // ---- Handicrafts ----
          p(
              1026,
              "HND-1026",
              "Kampong Chhnang Clay Pot",
              "ឆ្នាំងដីកំពង់ឆ្នាំង",
              "Kampong Chhnang Potters",
              1500,
              CAT_HANDICRAFTS,
              40,
              48,
              60,
              "Hand-formed terracotta pot from Kampong Chhnang, for soups and steamed rice.",
              "ឆ្នាំងដីដុតធ្វើដោយដៃពីខេត្តកំពង់ឆ្នាំង សម្រាប់ស្ងោរសម្ល និងចំហុយបាយ។"),
          p(
              1027,
              "HND-1027",
              "Kampong Luong Silver Box",
              "ប្រអប់ប្រាក់ កំពង់លួង",
              "Kampong Luong Silversmiths",
              5500,
              CAT_HANDICRAFTS,
              LOW_STOCK_1027,
              14,
              45,
              "Hand-chased silver box from the silversmiths of Kampong Luong, Kandal.",
              "ប្រអប់ប្រាក់ឆ្លាក់ដោយដៃពីជាងប្រាក់កំពង់លួង ខេត្តកណ្ដាល។"),
          p(
              1028,
              "HND-1028",
              "Rattan Market Basket",
              "កន្ត្រកផ្ដៅ",
              "Siem Reap Weavers",
              1800,
              CAT_HANDICRAFTS,
              40,
              35,
              42,
              "Hand-woven rattan basket from Siem Reap for market trips and storage.",
              "កន្ត្រកផ្ដៅត្បាញដៃពីខេត្តសៀមរាប សម្រាប់ដើរផ្សារ និងដាក់ឥវ៉ាន់។"),
          p(
              1029,
              "HND-1029",
              "Apsara Stone Carving Replica",
              "រូបចម្លាក់អប្សរា",
              "Siem Reap Carvers",
              3500,
              CAT_HANDICRAFTS,
              25,
              26,
              33,
              "Replica of an Angkor Wat apsara carving, made by artisans in Siem Reap.",
              "រូបចម្លងចម្លាក់អប្សរាប្រាសាទអង្គរវត្ត ធ្វើដោយសិប្បករនៅសៀមរាប។"),
          p(
              1030,
              "HND-1030",
              "Palm Leaf Bookmark",
              "ចំណាំសៀវភៅស្លឹករឹត",
              "Siem Reap Carvers",
              500,
              CAT_HANDICRAFTS,
              100,
              120,
              16,
              "Palm leaf bookmark inscribed with Khmer script in the style of traditional manuscripts.",
              "ចំណាំសៀវភៅស្លឹករឹតចារអក្សរខ្មែរតាមបែបគម្ពីរបុរាណ។"),
          // ---- Beauty & Wellness ----
          p(
              1031,
              "BTY-1031",
              "Virgin Coconut Oil 250 ml",
              "ប្រេងដូងបរិសុទ្ធ ២៥០ មីលីលីត្រ",
              "Kampot Farms",
              700,
              CAT_BEAUTY,
              80,
              125,
              34,
              "Cold-pressed virgin coconut oil from Kampot, for skin, hair and cooking.",
              "ប្រេងដូងបរិសុទ្ធច្របាច់ត្រជាក់ពីខេត្តកំពត សម្រាប់ថែស្បែក សក់ និងចម្អិនម្ហូប។"),
          p(
              1032,
              "BTY-1032",
              "Palm Sugar Body Scrub",
              "ស្ក្រាប់ស្ករត្នោត",
              "Kampong Speu Sugar Co-op",
              800,
              CAT_BEAUTY,
              60,
              58,
              27,
              "Gentle body scrub of palm sugar and coconut oil from Kampong Speu.",
              "ស្ក្រាប់ខ្លួនពីស្ករត្នោត និងប្រេងដូងពីខេត្តកំពង់ស្ពឺ ធ្វើឲ្យស្បែករលោង។"),
          p(
              1033,
              "BTY-1033",
              "Lemongrass Soap",
              "សាប៊ូស្លឹកគ្រៃ",
              "Battambang Soap Makers",
              350,
              CAT_BEAUTY,
              120,
              190,
              44,
              "Handmade soap scented with lemongrass, from Battambang.",
              "សាប៊ូធ្វើដោយដៃ ក្លិនស្លឹកគ្រៃ ពីខេត្តបាត់ដំបង។"),
          p(
              1034,
              "BTY-1034",
              "Moringa Powder 100 g",
              "ម្សៅស្លឹកម្រុំ ១០០ ក្រាម",
              "Kratie Farms",
              600,
              CAT_BEAUTY,
              70,
              64,
              21,
              "Dried moringa leaf powder from Kratie, to stir into drinks and porridge.",
              "ម្សៅស្លឹកម្រុំស្ងួតពីខេត្តក្រចេះ សម្រាប់លាយក្នុងភេសជ្ជៈ និងបបរ។"),
          p(
              1035,
              "BTY-1035",
              "Khmer Herbal Balm",
              "ប្រេងខ្យល់ឱសថខ្មែរ",
              "Kandal Herbalists",
              400,
              CAT_BEAUTY,
              100,
              175,
              31,
              "Traditional Khmer herbal balm with a cooling scent, for massage.",
              "ប្រេងខ្យល់ឱសថខ្មែរប្រពៃណី ក្លិនត្រជាក់ សម្រាប់ម៉ាស្សា។"),
          // ---- Books & Culture ----
          p(
              1036,
              "BKS-1036",
              "Angkor and the Khmer Civilization",
              "អង្គរ និងអរិយធម៌ខ្មែរ",
              "Phnom Penh Press",
              2800,
              CAT_BOOKS,
              40,
              36,
              58,
              "Illustrated history of Angkor, its temples and its kings.",
              "ប្រវត្តិសាស្ត្រអង្គរមានរូបភាព អំពីប្រាសាទ និងព្រះមហាក្សត្រ។"),
          p(
              1037,
              "BKS-1037",
              "Treasury of Cambodian Folk Tales (Bilingual)",
              "រឿងនិទានខ្មែរ (ពីរភាសា)",
              "Phnom Penh Press",
              1500,
              CAT_BOOKS,
              50,
              52,
              52,
              "Classic Cambodian folk tales in Khmer and English side by side.",
              "រឿងនិទានខ្មែរបុរាណជាភាសាខ្មែរ និងអង់គ្លេសនៅទំព័រជាប់គ្នា។"),
          p(
              1038,
              "BKS-1038",
              "Khmer–English Dictionary",
              "វចនានុក្រម ខ្មែរ-អង់គ្លេស",
              "Phnom Penh Press",
              1800,
              CAT_BOOKS,
              40,
              44,
              48,
              "Khmer–English dictionary for students, travellers and translators.",
              "វចនានុក្រមខ្មែរ-អង់គ្លេស សម្រាប់សិស្ស អ្នកដំណើរ និងអ្នកបកប្រែ។"),
          p(
              1039,
              "BKS-1039",
              "Khmer Alphabet for Children",
              "សៀវភៅព្យញ្ជនៈខ្មែរ សម្រាប់កុមារ",
              "Phnom Penh Press",
              600,
              CAT_BOOKS,
              60,
              98,
              39,
              "Illustrated Khmer consonants and vowels for young learners.",
              "សៀវភៅរូបភាពអំពីព្យញ្ជនៈ និងស្រៈខ្មែរ សម្រាប់កុមារ។"),
          p(
              1040,
              "BKS-1040",
              "Cambodian Home Cooking",
              "សៀវភៅម្ហូបខ្មែរ",
              "Phnom Penh Press",
              2200,
              CAT_BOOKS,
              30,
              41,
              37,
              "Home-style Khmer recipes, including fish amok and sour soup.",
              "រូបមន្តម្ហូបខ្មែរតាមផ្ទះ មានអាម៉ុកត្រី និងសម្លម្ជូរ។"));

  private static final Map<Long, Product> BY_ID =
      PRODUCTS.stream().collect(Collectors.toMap(Product::id, Function.identity()));

  public static Product product(long id) {
    Product product = BY_ID.get(id);
    if (product == null) {
      throw new IllegalArgumentException("Not a seeded product: " + id);
    }
    return product;
  }

  /** Units on hand for a non-variant product (variant products are stocked per variant). */
  public static int stock(long id) {
    return product(id).stock();
  }

  public static Variant variant(long id) {
    return VARIANTS.stream().filter(v -> v.id() == id).findFirst().orElseThrow();
  }

  public static long productsInCategory(long categoryId) {
    return PRODUCTS.stream().filter(p -> p.categoryId() == categoryId).count();
  }

  /** Spices & Sauces on the seasonal banner: every spice except the flash-sale pepper (1006). */
  public static boolean isSeasonalProduct(long productId) {
    return productId >= FIRST_SPICE_ID && productId <= LAST_SPICE_ID && productId != PEPPER_ID;
  }

  /** Cents to Khmer riel at the seeded rate (the app stores amountKhr as cents * rate / 100). */
  public static long toKhr(long cents) {
    return cents * USD_KHR_RATE / 100;
  }
}
