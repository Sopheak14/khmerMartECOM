# KhmerMart Architectural Discussion & Lesson Answers
## ការឆ្លើយសំណួរពិភាក្សាស្នូលស្តីពីស្ថាបត្យកម្មប្រព័ន្ធ KhmerMart

This document contains production-grade technical answers for the 8 core lesson discussion questions in both **English** and **ភាសាខ្មែរ (Khmer, with technical terms in parentheses)**, citing real classes, methods, and architectural designs from the KhmerMart codebase.

---

### Question 1: What is the role of the shopping cart in an e-commerce system?
#### សំណួរទី ១៖ តើកន្ត្រកទំនិញ (Shopping Cart) មានតួនាទីអ្វីខ្លះនៅក្នុងប្រព័ន្ធពាណិជ្ជកម្មអេឡិចត្រូនិច?

##### English Answer
In an e-commerce system, the shopping cart acts as a stateful, interactive staging area that bridges product catalog browsing and final checkout commitment. Its primary roles in KhmerMart include:
1. **Purchase Intent Staging**: It temporarily aggregates selected items and variants without prematurely locking warehouse inventory. A user can freely adjust quantities or remove items via [`CartController`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/controller/CartController.java).
2. **Session Continuity & Cart Merging**: It supports both anonymous browsing (guest cart indexed by guest token in Redis/Database) and authenticated shopping. When a guest user logs in, [`CartServiceImpl.mergeGuestCartToUser`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/CartServiceImpl.java) merges the guest cart items into the customer's persistent user cart.
3. **Dynamic Price & Promotion Evaluation**: It delegates to [`PricingService`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/PricingService.java) and [`PricingServiceImpl`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/PricingServiceImpl.java) to recalculate item subtotals, automatic campaign promotions (`FLASH_SALE`, `SEASONAL`, `BOGO`), and coupon vouchers (`SAVE5`, `WELCOME10`).
4. **Pre-Checkout Validation**: It performs live stock and price sanity checks before invoking the final immutable checkout pipeline in [`CheckoutServiceImpl`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/CheckoutServiceImpl.java).

##### ចម្លើយជាភាសាខ្មែរ (Khmer Answer)
នៅក្នុងប្រព័ន្ធពាណិជ្ជកម្មអេឡិចត្រូនិច (E-commerce System) កន្ត្រកទំនិញ (Shopping Cart) ដើរតួជាកន្លែងប្រមូលផ្តុំទំនិញបណ្តោះអាសន្ន ដែលតភ្ជាប់រវាងការរុករកមើលទំនិញ និងការបញ្ជាទិញពិតប្រាកដ។ តួនាទីសំខាន់ៗរបស់វារួមមាន៖
1. **ការរក្សាទុកទំនិញបណ្តោះអាសន្ន (Staging Purchase Intent)**៖ អនុញ្ញាតឱ្យអតិថិជនប្រមូលផ្តុំទំនិញ និងជម្រើសខ្នាត/ពណ៌ (Variants) ដោយមិនទាន់កាត់ស្តុកក្នុងឃ្លាំងភ្លាមៗនោះទេ តាមរយៈ [`CartController`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/controller/CartController.java)។
2. **ការបន្តវគ្គអ្នកប្រើប្រាស់ និងការច្របាច់បញ្ចូលកន្ត្រក (Cart Merging)**៖ គាំទ្រទាំងការទិញដោយមិនទាន់ចូលគណនី (Guest Cart តាមរយៈ Redis/Database) និងអ្នកប្រើប្រាស់ដែលបាន Login។ នៅពេលភ្ញៀវចូល Login មុខងារ [`CartServiceImpl.mergeGuestCartToUser`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/CartServiceImpl.java) នឹងច្របាច់បញ្ចូលទំនិញក្នុងកន្ត្រកភ្ញៀវទៅកាន់គណនីរបស់គាត់ដោយស្វ័យប្រវត្តិ។
3. **ការគណនាតម្លៃ និងប្រូម៉ូសិនភ្លាមៗ (Dynamic Price Calculation)**៖ ដំណើរការតាមរយៈ [`PricingService`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/PricingService.java) និង [`PricingServiceImpl`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/PricingServiceImpl.java) ដើម្បីគណនាតម្លៃសរុប រួមទាំងប្រូម៉ូសិនស្វ័យប្រវត្តិ (`FLASH_SALE`, `SEASONAL`, `BOGO`) និងការបញ្ចុះតម្លៃពីគូប៉ុង (`SAVE5`)។
4. **ការផ្ទៀងផ្ទាត់មុនពេលទូទាត់ (Pre-Checkout Validation)**៖ ផ្ទៀងផ្ទាត់តម្លៃ និងពិនិត្យស្តុកបឋមមុនពេលបញ្ជូនទៅដំណើរការទូទាត់ចុងក្រោយក្នុង [`CheckoutServiceImpl`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/CheckoutServiceImpl.java)។

---

### Question 2: Why is inventory management critical for preventing customer dissatisfaction?
#### សំណួរទី ២៖ ហេតុអ្វីបានជាការគ្រប់គ្រងស្តុកទំនិញមានសារៈសំខាន់ខ្លាំងក្នុងការទប់ស្កាត់ការមិនពេញចិត្តរបស់អតិថិជន?

##### English Answer
Poor inventory management leads to two critical operational failures that directly destroy customer trust: **overselling** (selling products that are out of stock, forcing painful order cancellations and refund delays) and **phantom stockouts** (falsely showing items as out of stock due to stale locks, driving customers to competitors).

KhmerMart prevents customer dissatisfaction using atomic, high-performance inventory controls:
1. **Atomic Conditional Reservation**: In [`InventoryRepository.tryReserve`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java), stock reservation executes via a single atomic SQL update:
   ```sql
   UPDATE inventory SET quantity_reserved = quantity_reserved + :qty, updated_at = now()
   WHERE id = :id AND quantity_on_hand - quantity_reserved >= :qty
   ```
   If stock is insufficient, the update affects 0 rows, and [`CheckoutServiceImpl`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/CheckoutServiceImpl.java) immediately aborts checkout with a clear [`CustomException`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/common/exception/CustomException.java) containing [`ErrorCode.OUT_OF_STOCK`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/common/exception/ErrorCode.java), informing the customer of the exact remaining quantity before payment occurs.
2. **Audit Ledger & Traceability**: Every stock change creates an audit row in [`InventoryMovementRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryMovementRepository.java) tagged with [`InventoryMovementType`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/model/enumerate/InventoryMovementType.java) (`RESERVE`, `SALE`, `RELEASE`, `RESTOCK`, `ADJUST`).
3. **Automated Expiry & Release**: If an unpaid order expires, [`InventoryRepository.releaseReserved`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) promptly restores reserved units so other shoppers can purchase them immediately.

##### ចម្លើយជាភាសាខ្មែរ (Khmer Answer)
ការគ្រប់គ្រងស្តុកមិនបានល្អបង្កឱ្យមានបញ្ហាធ្ងន់ធ្ងរពីរដែលបំផ្លាញទំនុកចិត្តអតិថិជន៖ **ការលក់លើសស្តុក (Overselling)** (អតិថិជនបង់លុយរួច តែគ្មានទំនិញដឹកជូន បង្ខំឱ្យលុបការបញ្ជាទិញ និងសងលុយវិញយឺតយ៉ាវ) និង **ការបង្ហាញស្តុកខុស (Phantom Stockout)** (ទំនិញមានក្នុងឃ្លាំង តែប្រព័ន្ធបង្ហាញថាអស់ស្តុក បង្កឱ្យបាត់បង់អតិថិជនទៅដៃគូប្រកួតប្រជែង)។

KhmerMart ការពារបញ្ហានេះដោយយន្តការគ្រប់គ្រងស្តុកកម្រិតខ្ពស់ (Atomic Inventory Control)៖
1. **ការកក់ស្តុកតាមលក្ខខណ្ឌជាក់ស្តែង (Atomic Conditional Reservation)**៖ នៅក្នុង [`InventoryRepository.tryReserve`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) ប្រព័ន្ធដំណើរការកក់ស្តុកដោយផ្ទាល់ក្នុង Query SQL តែមួយ `WHERE quantity_on_hand - quantity_reserved >= :qty`។ ប្រសិនបើស្តុកមិនគ្រប់គ្រាន់ នោះ Database នឹងកែប្រែបាន 0 ជួរ ហើយ [`CheckoutServiceImpl`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/CheckoutServiceImpl.java) នឹងផ្អាកការបញ្ជាទិញភ្លាមៗជាមួយ [`CustomException`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/common/exception/CustomException.java) និង [`ErrorCode.OUT_OF_STOCK`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/common/exception/ErrorCode.java) ដោយប្រាប់ចំនួននៅសល់ពិតប្រាកដមុនពេលអតិថិជនចំណាយប្រាក់។
2. **កំណត់ត្រាចលនាស្តុក (Audit Ledger)**៖ រាល់ការប្រែប្រួលស្តុកត្រូវបានកត់ត្រាក្នុង [`InventoryMovementRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryMovementRepository.java) ជាមួយប្រភេទ [`InventoryMovementType`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/model/enumerate/InventoryMovementType.java) (`RESERVE`, `SALE`, `RELEASE`, `RESTOCK`, `ADJUST`) ធានាបាននូវតម្លាភាព។
3. **ការដោះលែងស្តុកស្វ័យប្រវត្តិ (Automatic Stock Release)**៖ ប្រសិនបើការបញ្ជាទិញផុតកំណត់ដោយសារមិនបានបង់ប្រាក់ ប្រព័ន្ធនឹងហៅ [`InventoryRepository.releaseReserved`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) ដើម្បីដោះលែងស្តុកមកវិញភ្លាមៗសម្រាប់អតិថិជនផ្សេងទៀត។

---

### Question 3: What is the difference between the frontend and the backend?
#### សំណួរទី ៣៖ តើអ្វីជាភាពខុសគ្នារវាង Frontend និង Backend?

##### English Answer
The frontend and backend form a client-server architecture with strictly separated responsibilities:
1. **Frontend (Client Tier - Next.js / React / Flutter)**:
   - **Responsibility**: User Interface (UI) and User Experience (UX). It runs inside the customer's web browser or mobile operating system.
   - **Key Tasks**: Rendering responsive web pages, collecting user inputs (forms, cart actions, search terms), displaying generated KHQR images to the user, and communicating with the backend over HTTP/HTTPS and SSE.
   - **Security Context**: Inherently untrusted environment. It cannot be trusted to perform sensitive operations like price calculation, payment settlement, or stock modification.
2. **Backend (Server Tier - Spring Boot / Java 21)**:
   - **Responsibility**: Business logic, data integrity, security enforcement, and state management.
   - **Key Tasks**: Authenticates requests via [`JwtAuthFilter`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/JwtAuthFilter.java), throttles abuse with [`RateLimitFilter`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/RateLimitFilter.java), enforces the lifecycle state machine in [`OrderStateMachine`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/OrderStateMachine.java), interacts with the PostgreSQL database, and coordinates third-party financial integrations.
   - **Security Context**: Trusted execution environment where business invariants and financial transactions are authoritatively settled.

##### ចម្លើយជាភាសាខ្មែរ (Khmer Answer)
Frontend និង Backend គឺជាផ្នែកទាំងពីរនៃស្ថាបត្យកម្ម Client-Server ដែលមានការបែងចែកភារកិច្ចដាច់ដោយឡែកពីគ្នា៖
1. **Frontend (ផ្នែកខាងមុខ - Next.js / React / Flutter)**៖
   - **តួនាទី**៖ ផ្ដោតលើចំណុចប្រទាក់អ្នកប្រើប្រាស់ (UI) និងបទពិសោធន៍អ្នកប្រើប្រាស់ (UX) ដែលដំណើរការលើ Browser ឬទូរស័ព្ទដៃរបស់អតិថិជន។
   - **ភារកិច្ច**៖ បង្ហាញទំព័រទំនិញ ទទួលព័ត៌មានពីអ្នកប្រើប្រាស់ (ការចុចដាក់កន្ត្រក ការបំពេញអាសយដ្ឋាន) បង្ហាញរូបភាព KHQR សម្រាប់ការស្កេន និងផ្ញើសំណើ HTTP ទៅកាន់ Backend។
   - **បរិបទសុវត្ថិភាព**៖ ជាតំបន់ដែលមិនអាចទុកចិត្តបាន (Untrusted) ព្រោះកូដនៅលើ Client អាចត្រូវគេកែប្រែ ដូច្នេះមិនអាចអនុញ្ញាតឱ្យគណនាតម្លៃទំនិញ ឬកាត់ស្តុកដោយខ្លួនឯងបានឡើយ។
2. **Backend (ផ្នែកខាងក្រោយ - Spring Boot / Java 21)**៖
   - **តួនាទី**៖ គ្រប់គ្រងក្បួនច្បាប់អាជីវកម្ម (Business Logic) សុវត្ថិភាពប្រព័ន្ធ និងភាពត្រឹមត្រូវនៃទិន្នន័យ។
   - **ភារកិច្ច**៖ ត្រួតពិនិត្យសិទ្ធិ និងសុពលភាព Token តាមរយៈ [`JwtAuthFilter`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/JwtAuthFilter.java) ទប់ស្កាត់ការវាយប្រហារជាមួយ [`RateLimitFilter`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/RateLimitFilter.java) គ្រប់គ្រងវដ្តជីវិតការបញ្ជាទិញតាម [`OrderStateMachine`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/OrderStateMachine.java) និងរក្សាទុកទិន្នន័យក្នុង PostgreSQL។
   - **បរិបទសុវត្ថិភាព**៖ ជាតំបន់សុវត្ថិភាព (Trusted Environment) ដែលសម្រេចការកាត់ប្រាក់ និងការកែប្រែទិន្នន័យជាផ្លូវការ។

---

### Question 4: Why does an e-commerce system need a database? What happens without one?
#### សំណួរទី ៤៖ ហេតុអ្វីបានជាប្រព័ន្ធពាណិជ្ជកម្មអេឡិចត្រូនិចត្រូវការមូលដ្ឋានទិន្នន័យ (Database)? តើនឹងមានអ្វីកើតឡើងបើគ្មានវា?

##### English Answer
An e-commerce system relies on a relational database (PostgreSQL in KhmerMart) to provide ACID guarantees (Atomicity, Consistency, Isolation, Durability) across interdependent financial and physical assets:
1. **Durability & Persistence**: Transactions such as paid orders in [`OrderRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/OrderRepository.java) and stock counts in [`InventoryRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) are written to persistent write-ahead logs on disk.
2. **Relational Consistency**: Foreign keys guarantee referential integrity across users, orders, order items, inventory records, and payment receipts.
3. **Isolation & Concurrency**: The database enforces row-level locking and transaction isolation levels, preventing concurrent purchases from producing corrupt inventory values.

**What happens without a database?**
- **Catastrophic Data Loss**: If application server memory clears or crashes, all orders, registered customers, and payment confirmations vanish instantly.
- **Data Corruption & Double Spending**: Without ACID transactions, concurrent requests would cause race conditions, resulting in oversold inventory and lost payments.
- **Inability to Audit or Report**: Generating financial reports, managing tax invoices, and tracking delivery timelines become impossible without historical tabular records.

##### ចម្លើយជាភាសាខ្មែរ (Khmer Answer)
ប្រព័ន្ធពាណិជ្ជកម្មអេឡិចត្រូនិចពឹងផ្អែកលើមូលដ្ឋានទិន្នន័យទំនាក់ទំនង (Relational Database ដូចជា PostgreSQL ក្នុង KhmerMart) ដើម្បីធានាបាននូវលក្ខណៈសម្បត្តិ **ACID** (Atomicity, Consistency, Isolation, Durability) លើប្រតិបត្តិការហិរញ្ញវត្ថុ និងទំនិញ៖
1. **ការរក្សាទិន្នន័យជាអចិន្ត្រៃយ៍ (Durability & Persistence)**៖ រាល់ការបញ្ជាទិញក្នុង [`OrderRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/OrderRepository.java) និងស្តុកក្នុង [`InventoryRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) ត្រូវបានកត់ត្រាទុកយ៉ាងរឹងមាំលើ Hard Disk ទោះបីជា Server បិទឬដាច់ភ្លើងក៏មិនបាត់បង់ដែរ។
2. **ភាពត្រឹមត្រូវនៃទំនាក់ទំនងទិន្នន័យ (Referential Integrity)**៖ ការប្រើប្រាស់ Foreign Keys ធានាថាការបញ្ជាទិញនីមួយៗត្រូវតែភ្ជាប់ជាមួយអតិថិជន ទំនិញ និងបង្កាន់ដៃទូទាត់ដែលមានពិតប្រាកដ។
3. **ការគ្រប់គ្រងប្រតិបត្តិការស្របគ្នា (Isolation & Concurrency)**៖ Database ជួយចាក់សោជួរទិន្នន័យ (Row-Level Locking) ការពារកុំឱ្យប្រតិបត្តិការដំណាលគ្នាបង្កភាពរញ៉េរញ៉ៃដល់ទិន្នន័យ។

**តើមានអ្វីកើតឡើងបើគ្មានមូលដ្ឋានទិន្នន័យ (Without Database)?**
- **ការបាត់បង់ទិន្នន័យទាំងស្រុង (Catastrophic Data Loss)**៖ នៅពេល Server គាំង ឬ Restart រាល់ព័ត៌មានអតិថិជន ការបញ្ជាទិញ និងប្រាក់ដែលបានបង់ នឹងត្រូវរលុបបាត់អស់ភ្លាមៗ។
- **ទិន្នន័យខូច និងលក់ជាន់គ្នា (Data Corruption & Overselling)**៖ បើគ្មាន Transaction ទេនោះ សំណើច្រើនដំណាលគ្នានឹងកាត់ស្តុកជាន់គ្នា បង្កឱ្យស្តុកខុស និងបាត់បង់ប្រាក់កាស។
- **មិនអាចធ្វើសវនកម្ម ឬរបាយការណ៍ (Inability to Audit)**៖ មិនអាចទាញរបាយការណ៍លក់ បង្កើតវិក្កយបត្រពន្ធ ឬតាមដានការដឹកជញ្ជូនបានឡើយ។

---

### Question 5: What happens after a customer completes payment — step by step?
#### សំណួរទី ៥៖ តើមានអ្វីកើតឡើងបន្ទាប់ពីអតិថិជនបានទូទាត់ប្រាក់រួចរាល់ — តាមលំដាប់លំដោយជំហាន?

##### English Answer
When a payment settlement is detected, KhmerMart coordinates an automated multi-step pipeline:
1. **Payment Settlement Confirmation**: [`PaymentConfirmationServiceImpl.confirmPayment`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/PaymentConfirmationServiceImpl.java) transitions the `Payment` record status from `PENDING` to `PAID`, recording the external transaction reference and settlement timestamp.
2. **Order Lifecycle Transition**: The service transitions the order from `PENDING_PAYMENT` to `CONFIRMED` via [`OrderStateMachine`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/OrderStateMachine.java) and stamps `order.confirmedAt = Instant.now()`.
3. **Finalize Inventory (Deduct Reserved Stock)**: For each line item, the service executes [`InventoryRepository.deductReservedSale`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java):
   ```sql
   UPDATE inventory
   SET quantity_on_hand = quantity_on_hand - :qty,
       quantity_reserved = quantity_reserved - :qty,
       updated_at = now()
   WHERE id = :id AND quantity_reserved >= :qty AND quantity_on_hand >= :qty
   ```
   It logs an [`InventoryMovementType.SALE`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/model/enumerate/InventoryMovementType.java) audit entry in [`InventoryMovementRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryMovementRepository.java).
4. **Publish Asynchronous Domain Event**: A [`PaymentPaidEvent`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/event/PaymentPaidEvent.java) is published inside the Spring transaction.
5. **Notification Dispatch**: [`NotificationEventListener`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/event/listener/NotificationEventListener.java) reacts asynchronously:
   - Dispatches the payment confirmation email via [`MailService`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/MailService.java).
   - Generates SMS alert log ("Your payment of $... was successful").
   - Inserts an in-app customer notification.
6. **Real-Time Client Broadcast**: [`PaymentSseRegistry`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/PaymentSseRegistry.java) pushes a real-time `PAID` event across the open Server-Sent Events stream to redirect the customer's browser to the confirmation success page.

##### ចម្លើយជាភាសាខ្មែរ (Khmer Answer)
នៅពេលការទូទាត់ប្រាក់ត្រូវបានផ្ទៀងផ្ទាត់ជោគជ័យ ប្រព័ន្ធ KhmerMart ដំណើរការជំហានស្វ័យប្រវត្តិតាមលំដាប់ដូចខាងក្រោម៖
1. **ការបញ្ជាក់ការទូទាត់ (Payment Settlement Confirmation)**៖ នៅក្នុង [`PaymentConfirmationServiceImpl.confirmPayment`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/PaymentConfirmationServiceImpl.java) ប្រព័ន្ធផ្លាស់ប្តូរស្ថានភាព `Payment` ពី `PENDING` ទៅជា `PAID` ព្រមទាំងកត់ត្រាលេខយោងប្រតិបត្តិការធនាគារ (Transaction Reference) និងពេលវេលាជោគជ័យ។
2. **ការផ្លាស់ប្តូរស្ថានភាពបញ្ជាទិញ (Order Status Transition)**៖ ប្រព័ន្ធផ្លាស់ប្តូរស្ថានភាពការបញ្ជាទិញពី `PENDING_PAYMENT` ទៅជា `CONFIRMED` តាមរយៈ [`OrderStateMachine`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/OrderStateMachine.java) និងកត់ត្រា `confirmedAt`។
3. **ការកាត់ស្តុកជាក់ស្តែង (Finalize Inventory / SALE)**៖ ចំពោះទំនិញនីមួយៗ ប្រព័ន្ធដំណើរការ [`InventoryRepository.deductReservedSale`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) ដើម្បីកាត់បន្ថយទាំង `quantityOnHand` និង `quantityReserved` ព្រមទាំងកត់ត្រាចលនាស្តុកប្រភេទ `SALE` ក្នុង [`InventoryMovementRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryMovementRepository.java)។
4. **ការបញ្ជូនព្រឹត្តិការណ៍ក្នុងប្រព័ន្ធ (Publish Domain Event)**៖ បញ្ជូន [`PaymentPaidEvent`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/event/PaymentPaidEvent.java) ក្នុង Spring Event Bus។
5. **ការផ្ញើដំណឹងជូនអតិថិជន (Notification Dispatch)**៖ [`NotificationEventListener`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/event/listener/NotificationEventListener.java) ដំណើរការបញ្ជូនដំណឹង៖
   - ផ្ញើអ៊ីមែលបញ្ជាក់ការទូទាត់តាមរយៈ [`MailService`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/MailService.java)។
   - កត់ត្រាសារ SMS ដំណឹងជោគជ័យ ("Your payment of $... was successful")។
   - បង្កើត Notification ក្នុងកម្មវិធី (In-App Notification)។
6. **ការបញ្ជូនដំណឹងផ្ទាល់ទៅកាន់ Browser (Real-Time SSE Broadcast)**៖ [`PaymentSseRegistry`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/PaymentSseRegistry.java) ផ្សាយដំណឹង `PAID` ភ្លាមៗទៅកាន់ Browser របស់អតិថិជនតាមរយៈ Server-Sent Events (SSE) ដើម្បីប្តូរទំព័រទៅកាន់ទំព័រជោគជ័យ (Success Page)។

---

### Question 6: How does the payment gateway communicate with the e-commerce backend?
#### សំណួរទី ៦៖ តើ Payment Gateway ធ្វើការប្រាស្រ័យទាក់ទងជាមួយ Backend នៃប្រព័ន្ធពាណិជ្ជកម្មអេឡិចត្រូនិចយ៉ាងដូចម្តេច?

##### English Answer
KhmerMart implements the national standard NBC Bakong KHQR integration pattern. The end-to-end communication flow operates as follows:
1. **Dynamic QR Generation with Unique MD5 Hash**:
   When the customer chooses KHQR at checkout, [`KhqrServiceImpl`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/KhqrServiceImpl.java) formats an EMVCo-compliant payload with Merchant Account, currency, amount, bill number, and CRC16 checksum. It calculates a unique 32-character hexadecimal MD5 transaction hash over the KHQR string, records it in the database (`payments.khqr_md5`), and returns it to the client.
2. **Customer App Payment**:
   The customer scans the generated QR code inside any Bakong-enabled banking application (e.g., ABA, Wing, Canadia) and authorizes the financial transfer.
3. **Scheduled Polling Inquiries**:
   Because mobile bank apps settle directly on NBC Bakong payment rails without an inbound webhook to merchant backends, the backend uses a periodic background scheduler: [`KhqrPollingJob`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/KhqrPollingJob.java).
   - `KhqrPollingJob` selects all active payments with status `PENDING` whose expiry window has not lapsed.
   - For each payment, it invokes [`BakongApiClient.checkTransactionByMd5`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/BakongApiClient.java), issuing an authenticated HTTP `POST` request to Bakong's endpoint: `/v1/check_transaction_by_md5` containing `{ "md5": "<payment_md5>" }`.
4. **Authoritative Confirmation**:
   When Bakong returns `responseCode: 0` along with the settling transaction details (external reference, amount, currency), `KhqrPollingJob` verifies that the settled amount matches the order total. It then invokes [`PaymentConfirmationServiceImpl.confirmPayment`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/PaymentConfirmationServiceImpl.java) to confirm the order.

##### ចម្លើយជាភាសាខ្មែរ (Khmer Answer)
KhmerMart អនុវត្តតាមស្តង់ដារជាតិ Bakong KHQR របស់ធនាគារជាតិនៃកម្ពុជា (NBC)។ ដំណើរការប្រាស្រ័យទាក់ទងប្រព្រឹត្តទៅដូចខាងក្រោម៖
1. **ការបង្កើតកូដ QR ជាមួយ MD5 Hash សម្គាល់ (Generate QR with MD5)**៖
   នៅពេលអតិថិជនជ្រើសរើស KHQR ក្នុងពេល Checkout, [`KhqrServiceImpl`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/KhqrServiceImpl.java) បង្កើតទម្រង់ EMVCo QR រួមមានគណនី Merchant រូបិយប័ណ្ណ ចំនួនទឹកប្រាក់ និងឆែក CRC16 Checksum។ ជាមួយគ្នានេះ ប្រព័ន្ធគណនា MD5 Hash (ទំហំ 32 តួអក្សរ Hex) លើ QR String នោះ រួចកត់ត្រាទុកក្នុង Database (`payments.khqr_md5`) និងបញ្ជូនទៅកាន់ Frontend។
2. **អតិថិជនស្កេនទូទាត់លើ App ធនាគារ (Customer Pays in Bank App)**៖
   អតិថិជនប្រើប្រាស់ App ធនាគារណាមួយដែលគាំទ្រ Bakong (ដូចជា ABA, Wing, Canadia) ដើម្បីស្កេន QR Code និងបញ្ជាក់ការផ្ទេរប្រាក់។
3. **ការសាកសួរស្ថានភាពតាមកាលកំណត់ (KhqrPollingJob calls Bakong API)**៖
   ដោយសារ App ធនាគារទូទាត់នៅលើប្រព័ន្ធធនាគារកណ្តាល (Bakong) ដោយមិនបានបាញ់ Webhook ត្រឡប់មក Backend ផ្ទាល់ឡើយ ដូច្នេះ Backend ដំណើរការ Background Task ដោយស្វ័យប្រវត្តិតាមរយៈ [`KhqrPollingJob`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/KhqrPollingJob.java)៖
   - `KhqrPollingJob` ស្វែងរកការទូទាត់ដែលកំពុងរង់ចាំ (`PENDING`)។
   - ហៅទៅកាន់ [`BakongApiClient.checkTransactionByMd5`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/BakongApiClient.java) ដោយផ្ញើសំណើ HTTP POST ទៅកាន់ API របស់ Bakong `/v1/check_transaction_by_md5` ជាមួយ JSON `{ "md5": "<payment_md5>" }`។
4. **ការបញ្ជាក់ការទូទាត់ជោគជ័យ (PaymentConfirmationService Confirms)**៖
   នៅពេល Bakong ឆ្លើយតប `responseCode: 0` បញ្ជាក់ថាប្រាក់បានផ្ទេររួចរាល់ `KhqrPollingJob` នឹងផ្ទៀងផ្ទាត់ចំនួនទឹកប្រាក់ រួចបញ្ជូនទៅ [`PaymentConfirmationServiceImpl.confirmPayment`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/PaymentConfirmationServiceImpl.java) ដើម្បីកាត់ស្តុក និងបញ្ជាក់ការបញ្ជាទិញ។

---

### Question 7: What is the purpose of an API, and why is it important?
#### សំណួរទី ៧៖ តើ API មានគោលបំណងអ្វីខ្លះ ហើយហេតុអ្វីបានជាវាមានសារៈសំខាន់?

##### English Answer
An Application Programming Interface (API) is a formal, standardized contract that defines how disparate software systems exchange data and trigger operations. Its critical importance in KhmerMart includes:
1. **Separation of Presentation and Business Logic**: The REST API exposes structured JSON endpoints (`/api/products`, `/api/cart`, `/api/checkout`). This allows frontend web applications (React/Next.js), mobile apps (Flutter iOS/Android), and administrative backoffices to communicate with the same backend without duplicating validation or persistence logic.
2. **Centralized Security Perimeter**: All client interactions pass through centralized security filters: authentication and token inspection in [`JwtAuthFilter`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/JwtAuthFilter.java), and volumetric rate-limiting in [`RateLimitFilter`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/RateLimitFilter.java).
3. **Interoperability with External Ecosystems**: Standardized APIs enable integration with external banking networks (NBC Bakong via [`BakongApiClient`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/BakongApiClient.java)) and notification services (SMTP mail servers via [`MailService`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/MailService.java)).
4. **Contract Verifiability & Scalability**: APIs allow teams to develop client and server systems independently against an agreed specification (such as OpenAPI/Swagger at `/api/v3/api-docs`), validated automatically by contract verification scripts.

##### ចម្លើយជាភាសាខ្មែរ (Khmer Answer)
API (Application Programming Interface) គឺជាកិច្ចសន្យា ឬស្ពានទំនាក់ទំនងស្តង់ដារមួយដែលកំណត់ពីរបៀបដែលប្រព័ន្ធកម្មវិធីផ្សេងៗគ្នាអាចផ្លាស់ប្តូរទិន្នន័យ និងបញ្ជាប្រតិបត្តិការទៅវិញទៅមក។ សារៈសំខាន់របស់វារួមមាន៖
1. **ការបែងចែកដាច់ពីគ្នារវាង UI និង Business Logic**៖ REST API ផ្តល់នូវ Endpoints ជាទម្រង់ JSON ស្តង់ដារ (`/api/products`, `/api/cart`, `/api/checkout`)។ ចំណុចនេះអនុញ្ញាតឱ្យទាំង Web (Next.js), App ទូរស័ព្ទ (Flutter iOS/Android) និងផ្ទាំងគ្រប់គ្រង Admin អាចដំណើរការលើទិន្នន័យរួមតែមួយ ដោយមិនបាច់សរសេរកូដគ្រប់គ្រងទិន្នន័យឡើងវិញច្រើនដង។
2. **របាំងការពារសុវត្ថិភាពរួម (Centralized Security Perimeter)**៖ រាល់សំណើទាំងអស់ត្រូវបានត្រួតពិនិត្យសុវត្ថិភាពនៅកន្លែងតែមួយ រួមមានការឆែក Token តាមរយៈ [`JwtAuthFilter`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/JwtAuthFilter.java) និងការកម្រិតចំនួនសំណើការពារការវាយប្រហារតាមរយៈ [`RateLimitFilter`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/RateLimitFilter.java)។
3. **ការតភ្ជាប់ជាមួយប្រព័ន្ធខាងក្រៅ (External Interoperability)**៖ អនុញ្ញាតឱ្យ KhmerMart អាចភ្ជាប់ជាមួយបណ្តាញទូទាត់ធនាគារជាតិ Bakong តាម [`BakongApiClient`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/BakongApiClient.java) និងប្រព័ន្ធផ្ញើអ៊ីមែលតាម [`MailService`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/MailService.java)។
4. **ការផ្ទៀងផ្ទាត់កិច្ចសន្យា និងការពង្រីកប្រព័ន្ធ (Contract Verifiability)**៖ អនុញ្ញាតឱ្យក្រុមការងារ Frontend និង Backend អាចអភិវឌ្ឍប្រព័ន្ធស្របគ្នាដោយផ្អែកលើឯកសារកិច្ចសន្យារួម (OpenAPI Spec នៅលើ `/api/v3/api-docs`)។

---

### Question 8: Which component is responsible for automatically updating product stock?
#### សំណួរទី ៨៖ តើសមាសភាគ (Component) ណាខ្លះដែលទទួលខុសត្រូវក្នុងការកែប្រែបច្ចុប្បន្នភាពស្តុកទំនិញដោយស្វ័យប្រវត្តិ?

##### English Answer
Product stock updates are orchestrated by a coordinated set of service and repository components across different stages of the purchase lifecycle:
1. **Stock Reservation Component**:
   [`CheckoutServiceImpl`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/CheckoutServiceImpl.java) invokes [`InventoryRepository.tryReserve`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) during checkout. This atomically increments `quantity_reserved` if `quantity_on_hand - quantity_reserved >= :qty` and creates an [`InventoryMovementType.RESERVE`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/model/enumerate/InventoryMovementType.java) movement row.
2. **Stock Finalization (Sale) Component**:
   [`PaymentConfirmationServiceImpl`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/PaymentConfirmationServiceImpl.java) invokes [`InventoryRepository.deductReservedSale`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) upon successful payment confirmation. This atomically decrements both `quantity_on_hand` and `quantity_reserved` and inserts an [`InventoryMovementType.SALE`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/model/enumerate/InventoryMovementType.java) record in [`InventoryMovementRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryMovementRepository.java).
3. **Stock Release (Cancellation / Expiry) Component**:
   When an order is cancelled or its payment window expires, [`OrderServiceImpl.cancelOrder`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/OrderServiceImpl.java) and [`KhqrPollingJob`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/KhqrPollingJob.java) invoke [`InventoryRepository.releaseReserved`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) to decrement `quantity_reserved` and restore available stock with a `RELEASE` movement.
4. **Data Layer Execution Engine**:
   [`InventoryRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) is the authoritative component executing these atomic updates via native conditional SQL statements, ensuring zero race conditions at the database layer.

##### ចម្លើយជាភាសាខ្មែរ (Khmer Answer)
ការកែប្រែបច្ចុប្បន្នភាពស្តុកទំនិញដោយស្វ័យប្រវត្តិ ត្រូវបានសហការអនុវត្តដោយសមាសភាគ (Components) សំខាន់ៗជាច្រើនតាមដំណាក់កាលនីមួយៗនៃវដ្តជីវិតបញ្ជាទិញ៖
1. **សមាសភាគកក់ស្តុក (Stock Reservation)**៖
   [`CheckoutServiceImpl`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/CheckoutServiceImpl.java) ហៅទៅកាន់ [`InventoryRepository.tryReserve`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) នៅពេលអតិថិជន Checkout។ វាធ្វើការបង្កើន `quantity_reserved` ដោយស្វ័យប្រវត្តិតាមលក្ខខណ្ឌ `quantity_on_hand - quantity_reserved >= :qty` និងកត់ត្រាចលនាស្តុក [`InventoryMovementType.RESERVE`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/model/enumerate/InventoryMovementType.java)។
2. **សមាសភាគកាត់ស្តុកលក់ដាច់ជាផ្លូវការ (Stock Finalization / SALE)**៖
   [`PaymentConfirmationServiceImpl`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/PaymentConfirmationServiceImpl.java) ហៅទៅកាន់ [`InventoryRepository.deductReservedSale`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) នៅពេលការទូទាត់ប្រាក់ជោគជ័យ។ វានឹងកាត់បន្ថយទាំងចំនួនក្នុងឃ្លាំង (`quantity_on_hand`) និងចំនួនដែលបានកក់ (`quantity_reserved`) ព្រមទាំងកត់ត្រាចលនា [`InventoryMovementType.SALE`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/model/enumerate/InventoryMovementType.java) ក្នុង [`InventoryMovementRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryMovementRepository.java)។
3. **សមាសភាគដោះលែងស្តុកពេលលុបចោល ឬផុតកំណត់ (Stock Release)**៖
   នៅពេលការបញ្ជាទិញត្រូវលុបចោល ឬហួសពេលបង់ប្រាក់ [`OrderServiceImpl.cancelOrder`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/OrderServiceImpl.java) និង [`KhqrPollingJob`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/KhqrPollingJob.java) នឹងហៅ [`InventoryRepository.releaseReserved`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) ដើម្បីដោះលែងចំនួនកក់មកវិញជាមួយចលនា `RELEASE`។
4. **ម៉ាស៊ីនអនុវត្តកម្រិតទិន្នន័យ (Data Layer Execution Engine)**៖
   [`InventoryRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java) គឺជាសមាសភាគស្នូលដែលអនុវត្តការកែប្រែទិន្នន័យស្តុកដោយផ្ទាល់ក្នុង Database តាមរយៈ Conditional SQL Updates ធានាដាច់ខាតមិនឱ្យមាន Race Conditions។
