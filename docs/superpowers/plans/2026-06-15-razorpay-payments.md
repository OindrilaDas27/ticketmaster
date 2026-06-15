# Razorpay Payments + Ticket Booking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add paid ticket booking to the Ticketmaster platform — users pick an event, choose ticket tiers + quantity, pay via Razorpay, and receive confirmed tickets — with concurrency-safe inventory and webhook-driven confirmation.

**Architecture:** Spring Boot creates a Razorpay Order; the React frontend opens Razorpay Standard Checkout; the HMAC-signed `payment.captured` webhook is the source-of-truth confirmation. Inventory is reserved via "hold-on-checkout" (PENDING booking with a 10-min TTL) and released by a scheduled job if payment doesn't arrive in time.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Data JPA, PostgreSQL (H2 for tests), `com.razorpay:razorpay-java:1.4.8`, React 18 + TypeScript + Vite, Razorpay JS Checkout SDK.

**Spec:** `docs/superpowers/specs/2026-06-15-razorpay-payments-design.md`

**Repos:**
- Backend: `/Users/oindriladas/Documents/prozect/ticketmaster`
- Frontend: `/Users/oindriladas/Documents/prozect/ticketmaster_fe`

All backend paths below are relative to the backend repo; frontend paths to the frontend repo.

---

## File Map

### Backend (new)
- `src/main/java/com/example/entity/BookingStatus.java`
- `src/main/java/com/example/entity/TicketTier.java`
- `src/main/java/com/example/entity/Booking.java`
- `src/main/java/com/example/entity/BookingItem.java`
- `src/main/java/com/example/entity/Payment.java`
- `src/main/java/com/example/dao/TicketTierRepository.java`
- `src/main/java/com/example/dao/BookingRepository.java`
- `src/main/java/com/example/dao/BookingItemRepository.java`
- `src/main/java/com/example/dao/PaymentRepository.java`
- `src/main/java/com/example/dto/TicketTierDTO.java`
- `src/main/java/com/example/dto/CreateBookingRequest.java`
- `src/main/java/com/example/dto/CreateBookingResponse.java`
- `src/main/java/com/example/dto/BookingDTO.java`
- `src/main/java/com/example/dto/BookingItemDTO.java`
- `src/main/java/com/example/dto/ClientAckRequest.java`
- `src/main/java/com/example/service/TicketTierService.java`
- `src/main/java/com/example/service/BookingService.java`
- `src/main/java/com/example/service/RazorpayService.java`
- `src/main/java/com/example/service/WebhookService.java`
- `src/main/java/com/example/service/impl/TicketTierServiceImpl.java`
- `src/main/java/com/example/service/impl/BookingServiceImpl.java`
- `src/main/java/com/example/service/impl/RazorpayServiceImpl.java`
- `src/main/java/com/example/service/impl/WebhookServiceImpl.java`
- `src/main/java/com/example/controller/TicketTierController.java`
- `src/main/java/com/example/controller/BookingController.java`
- `src/main/java/com/example/controller/WebhookController.java`
- `src/main/java/com/example/scheduled/BookingExpiryJob.java`
- `src/test/java/com/example/service/RazorpayServiceImplTest.java`
- `src/test/java/com/example/service/BookingServiceImplTest.java`
- `src/test/java/com/example/service/WebhookServiceImplTest.java`
- `src/test/java/com/example/scheduled/BookingExpiryJobTest.java`

### Backend (modify)
- `pom.xml` — add `razorpay-java` dep
- `src/main/java/com/example/Application.java` — add `@EnableScheduling`
- `src/main/java/com/example/ApplicationConstants.java` — add booking/webhook endpoints
- `.env.example` — add Razorpay vars
- `src/main/resources/application.properties` — bind Razorpay props (if needed)

### Frontend (new)
- `src/pages/EventDetailPage.tsx`
- `src/pages/BookingConfirmationPage.tsx`
- `src/service/bookingService.ts`
- `src/service/paymentService.ts`
- `src/types/booking.ts`

### Frontend (modify)
- `index.html` — load Razorpay Checkout SDK
- `src/App.tsx` (or router file) — add new routes
- `src/pages/CreateEvent.tsx` — add tier definition inputs
- `src/pages/ProfilePage.tsx` — add MyBookings section
- `src/service/index.ts` — export new services

---

# Phase 1 — Backend foundation

## Task 1: Add Razorpay dependency and enable scheduling

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/example/Application.java`
- Modify: `.env.example`

- [ ] **Step 1: Add Razorpay dependency**

Add to `pom.xml` inside `<dependencies>`:

```xml
<dependency>
    <groupId>com.razorpay</groupId>
    <artifactId>razorpay-java</artifactId>
    <version>1.4.8</version>
</dependency>
```

- [ ] **Step 2: Enable scheduling**

Edit `src/main/java/com/example/Application.java`. Add the annotation:

```java
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

- [ ] **Step 3: Add env vars**

Append to `.env.example`:

```
# Razorpay
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxx
RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxx
RAZORPAY_WEBHOOK_SECRET=xxxxxxxxxxxxxxxx
BOOKING_HOLD_MINUTES=10
```

- [ ] **Step 4: Add endpoint constants**

Edit `src/main/java/com/example/ApplicationConstants.java`. Add inside the class:

```java
public static final String BOOKINGS_ENDPOINT = "/bookings";
public static final String TIERS_ENDPOINT = "/tiers";
public static final String WEBHOOKS_RAZORPAY_ENDPOINT = "/webhooks/razorpay";

// Booking errors
public static final String INSUFFICIENT_INVENTORY = "Insufficient tickets available for tier: ";
public static final String BOOKING_NOT_FOUND = "Booking not found with id: ";
public static final String INVALID_WEBHOOK_SIGNATURE = "Invalid webhook signature";
public static final String TIER_NOT_FOUND = "Ticket tier not found with id: ";
```

- [ ] **Step 5: Verify build**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/main/java/com/example/Application.java src/main/java/com/example/ApplicationConstants.java .env.example
git commit -m "feat: add razorpay dependency, scheduling, and booking constants"
```

---

## Task 2: BookingStatus enum + TicketTier entity + repo

**Files:**
- Create: `src/main/java/com/example/entity/BookingStatus.java`
- Create: `src/main/java/com/example/entity/TicketTier.java`
- Create: `src/main/java/com/example/dao/TicketTierRepository.java`

- [ ] **Step 1: Create BookingStatus enum**

`src/main/java/com/example/entity/BookingStatus.java`:

```java
package com.example.entity;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    EXPIRED,
    FAILED,
    REFUNDED
}
```

- [ ] **Step 2: Create TicketTier entity**

`src/main/java/com/example/entity/TicketTier.java`:

```java
package com.example.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_tiers")
@Data
@NoArgsConstructor
public class TicketTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "price_inr_paise", nullable = false)
    private Long priceInrPaise;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Create TicketTierRepository**

`src/main/java/com/example/dao/TicketTierRepository.java`:

```java
package com.example.dao;

import com.example.entity.TicketTier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketTierRepository extends JpaRepository<TicketTier, Long> {

    List<TicketTier> findByEventId(Long eventId);

    /**
     * Atomically decrement available_quantity ONLY IF enough remain.
     * Returns the number of rows updated (1 = success, 0 = insufficient inventory).
     */
    @Modifying
    @Query("UPDATE TicketTier t SET t.availableQuantity = t.availableQuantity - :qty " +
           "WHERE t.id = :id AND t.availableQuantity >= :qty")
    int decrementIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE TicketTier t SET t.availableQuantity = t.availableQuantity + :qty WHERE t.id = :id")
    int incrementAvailable(@Param("id") Long id, @Param("qty") int qty);
}
```

- [ ] **Step 4: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/entity/BookingStatus.java src/main/java/com/example/entity/TicketTier.java src/main/java/com/example/dao/TicketTierRepository.java
git commit -m "feat: add TicketTier entity with atomic inventory decrement query"
```

---

## Task 3: Booking entity + repo

**Files:**
- Create: `src/main/java/com/example/entity/Booking.java`
- Create: `src/main/java/com/example/dao/BookingRepository.java`

- [ ] **Step 1: Create Booking entity**

`src/main/java/com/example/entity/Booking.java`:

```java
package com.example.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_bookings_user_id", columnList = "user_id"),
    @Index(name = "idx_bookings_status_expires", columnList = "status,hold_expires_at"),
    @Index(name = "idx_bookings_razorpay_order_id", columnList = "razorpay_order_id")
})
@Data
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "total_amount_paise", nullable = false)
    private Long totalAmountPaise;

    @Column(name = "hold_expires_at", nullable = false)
    private LocalDateTime holdExpiresAt;

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Create BookingRepository**

`src/main/java/com/example/dao/BookingRepository.java`:

```java
package com.example.dao;

import com.example.entity.Booking;
import com.example.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Booking> findByRazorpayOrderId(String razorpayOrderId);

    @Query("SELECT b FROM Booking b WHERE b.status = :status AND b.holdExpiresAt < :now")
    List<Booking> findExpiredHolds(@Param("status") BookingStatus status, @Param("now") LocalDateTime now);
}
```

- [ ] **Step 3: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/entity/Booking.java src/main/java/com/example/dao/BookingRepository.java
git commit -m "feat: add Booking entity and repository"
```

---

## Task 4: BookingItem entity + repo

**Files:**
- Create: `src/main/java/com/example/entity/BookingItem.java`
- Create: `src/main/java/com/example/dao/BookingItemRepository.java`

- [ ] **Step 1: Create BookingItem entity**

`src/main/java/com/example/entity/BookingItem.java`:

```java
package com.example.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booking_items", indexes = {
    @Index(name = "idx_booking_items_booking_id", columnList = "booking_id")
})
@Data
@NoArgsConstructor
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "ticket_tier_id", nullable = false)
    private Long ticketTierId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_paise", nullable = false)
    private Long unitPricePaise;

    @Column(name = "line_total_paise", nullable = false)
    private Long lineTotalPaise;
}
```

- [ ] **Step 2: Create BookingItemRepository**

`src/main/java/com/example/dao/BookingItemRepository.java`:

```java
package com.example.dao;

import com.example.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {
    List<BookingItem> findByBookingId(Long bookingId);
}
```

- [ ] **Step 3: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/entity/BookingItem.java src/main/java/com/example/dao/BookingItemRepository.java
git commit -m "feat: add BookingItem entity and repository"
```

---

## Task 5: Payment entity + repo (with idempotency unique constraint)

**Files:**
- Create: `src/main/java/com/example/entity/Payment.java`
- Create: `src/main/java/com/example/dao/PaymentRepository.java`

- [ ] **Step 1: Create Payment entity**

`src/main/java/com/example/entity/Payment.java`:

```java
package com.example.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments",
    uniqueConstraints = @UniqueConstraint(name = "uk_payments_event_id", columnNames = "razorpay_event_id"),
    indexes = {
        @Index(name = "idx_payments_booking_id", columnList = "booking_id"),
        @Index(name = "idx_payments_razorpay_payment_id", columnList = "razorpay_payment_id")
    })
@Data
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_event_id", nullable = false, length = 100)
    private String razorpayEventId;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @Column(name = "amount_paise")
    private Long amountPaise;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        receivedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Create PaymentRepository**

`src/main/java/com/example/dao/PaymentRepository.java`:

```java
package com.example.dao;

import com.example.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByRazorpayEventId(String razorpayEventId);
    boolean existsByRazorpayEventId(String razorpayEventId);
}
```

- [ ] **Step 3: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/entity/Payment.java src/main/java/com/example/dao/PaymentRepository.java
git commit -m "feat: add Payment audit entity with unique constraint on event_id"
```

---

# Phase 2 — DTOs

## Task 6: Booking & payment DTOs

**Files:**
- Create: `src/main/java/com/example/dto/TicketTierDTO.java`
- Create: `src/main/java/com/example/dto/CreateBookingRequest.java`
- Create: `src/main/java/com/example/dto/CreateBookingResponse.java`
- Create: `src/main/java/com/example/dto/BookingDTO.java`
- Create: `src/main/java/com/example/dto/BookingItemDTO.java`
- Create: `src/main/java/com/example/dto/ClientAckRequest.java`

- [ ] **Step 1: TicketTierDTO**

`src/main/java/com/example/dto/TicketTierDTO.java`:

```java
package com.example.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketTierDTO {
    private Long id;
    private Long eventId;

    @NotBlank
    private String name;

    @NotNull
    @Min(0)
    private Long priceInrPaise;

    @NotNull
    @Min(1)
    private Integer totalQuantity;

    private Integer availableQuantity;
}
```

- [ ] **Step 2: CreateBookingRequest**

`src/main/java/com/example/dto/CreateBookingRequest.java`:

```java
package com.example.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CreateBookingRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long eventId;

    @NotEmpty
    @Valid
    private List<Item> items;

    @Data
    @NoArgsConstructor
    public static class Item {
        @NotNull
        private Long tierId;

        @NotNull
        @Min(1)
        private Integer quantity;
    }
}
```

- [ ] **Step 3: CreateBookingResponse**

`src/main/java/com/example/dto/CreateBookingResponse.java`:

```java
package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingResponse {
    private Long bookingId;
    private String razorpayOrderId;
    private String razorpayKeyId;
    private Long amountPaise;
    private String currency;
}
```

- [ ] **Step 4: BookingItemDTO**

`src/main/java/com/example/dto/BookingItemDTO.java`:

```java
package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingItemDTO {
    private Long id;
    private Long ticketTierId;
    private String tierName;
    private Integer quantity;
    private Long unitPricePaise;
    private Long lineTotalPaise;
}
```

- [ ] **Step 5: BookingDTO**

`src/main/java/com/example/dto/BookingDTO.java`:

```java
package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingDTO {
    private Long id;
    private Long userId;
    private Long eventId;
    private String status;
    private Long totalAmountPaise;
    private LocalDateTime holdExpiresAt;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private List<BookingItemDTO> items;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 6: ClientAckRequest**

`src/main/java/com/example/dto/ClientAckRequest.java`:

```java
package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ClientAckRequest {
    @NotBlank private String razorpayPaymentId;
    @NotBlank private String razorpayOrderId;
    @NotBlank private String razorpaySignature;
}
```

- [ ] **Step 7: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/dto/
git commit -m "feat: add DTOs for tiers, booking creation, and client ack"
```

---

# Phase 3 — Razorpay service (signature verification + Orders API)

## Task 7: RazorpayService interface + impl skeleton

**Files:**
- Create: `src/main/java/com/example/service/RazorpayService.java`
- Create: `src/main/java/com/example/service/impl/RazorpayServiceImpl.java`

- [ ] **Step 1: Define service interface**

`src/main/java/com/example/service/RazorpayService.java`:

```java
package com.example.service;

public interface RazorpayService {

    /** Creates a Razorpay Order and returns its order_id. */
    String createOrder(long amountPaise, String currency, String receipt) throws Exception;

    /** Verifies a webhook payload signature using webhook_secret. */
    boolean verifyWebhookSignature(String payload, String signature);

    /** Verifies a Checkout success callback signature using key_secret. */
    boolean verifyCheckoutSignature(String orderId, String paymentId, String signature);

    String getKeyId();
}
```

- [ ] **Step 2: Implement service**

`src/main/java/com/example/service/impl/RazorpayServiceImpl.java`:

```java
package com.example.service.impl;

import com.example.service.RazorpayService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RazorpayServiceImpl implements RazorpayService {

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final RazorpayClient client;

    public RazorpayServiceImpl(
            @Value("${RAZORPAY_KEY_ID}") String keyId,
            @Value("${RAZORPAY_KEY_SECRET}") String keySecret,
            @Value("${RAZORPAY_WEBHOOK_SECRET}") String webhookSecret
    ) throws Exception {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        this.client = new RazorpayClient(keyId, keySecret);
    }

    @Override
    public String createOrder(long amountPaise, String currency, String receipt) throws Exception {
        JSONObject body = new JSONObject();
        body.put("amount", amountPaise);
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("payment_capture", 1);
        Order order = client.orders.create(body);
        return order.get("id");
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            return Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);
            return Utils.verifyPaymentSignature(options, keySecret);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getKeyId() {
        return keyId;
    }
}
```

- [ ] **Step 3: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/service/RazorpayService.java src/main/java/com/example/service/impl/RazorpayServiceImpl.java
git commit -m "feat: add RazorpayService with order creation and signature verification"
```

---

## Task 8: RazorpayService signature verification test

**Files:**
- Create: `src/test/java/com/example/service/RazorpayServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/service/RazorpayServiceImplTest.java`:

```java
package com.example.service;

import com.example.service.impl.RazorpayServiceImpl;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RazorpayServiceImplTest {

    private static final String KEY_ID = "rzp_test_dummy";
    private static final String KEY_SECRET = "key-secret-dummy";
    private static final String WEBHOOK_SECRET = "webhook-secret-dummy";

    private RazorpayServiceImpl newService() throws Exception {
        return new RazorpayServiceImpl(KEY_ID, KEY_SECRET, WEBHOOK_SECRET);
    }

    private static String hmacSha256Hex(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : raw) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    @Test
    void verifyWebhookSignature_acceptsValidSignature() throws Exception {
        RazorpayServiceImpl service = newService();
        String payload = "{\"event\":\"payment.captured\",\"id\":\"evt_test_1\"}";
        String validSig = hmacSha256Hex(payload, WEBHOOK_SECRET);

        assertTrue(service.verifyWebhookSignature(payload, validSig));
    }

    @Test
    void verifyWebhookSignature_rejectsInvalidSignature() throws Exception {
        RazorpayServiceImpl service = newService();
        String payload = "{\"event\":\"payment.captured\"}";

        assertFalse(service.verifyWebhookSignature(payload, "deadbeef"));
    }

    @Test
    void verifyCheckoutSignature_acceptsValidSignature() throws Exception {
        RazorpayServiceImpl service = newService();
        String orderId = "order_test_1";
        String paymentId = "pay_test_1";
        String validSig = hmacSha256Hex(orderId + "|" + paymentId, KEY_SECRET);

        assertTrue(service.verifyCheckoutSignature(orderId, paymentId, validSig));
    }

    @Test
    void verifyCheckoutSignature_rejectsInvalidSignature() throws Exception {
        RazorpayServiceImpl service = newService();
        assertFalse(service.verifyCheckoutSignature("order_test_1", "pay_test_1", "deadbeef"));
    }
}
```

- [ ] **Step 2: Run test (will fail until properties are surfaced in test env)**

Run: `mvn -q -Dtest=RazorpayServiceImplTest test`
Expected: tests pass — we instantiate the service directly with constructor args, bypassing Spring property binding.

If a test fails because constructor throws on `new RazorpayClient`, it means the SDK does network validation at construction; in that case mark the network-dependent test calls behind a stub. Re-run; if the constructor truly works offline, all four tests should pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/example/service/RazorpayServiceImplTest.java
git commit -m "test: verify Razorpay HMAC signature checks for webhook and checkout"
```

---

# Phase 4 — Booking service (core business logic)

## Task 9: BookingService interface

**Files:**
- Create: `src/main/java/com/example/service/BookingService.java`

- [ ] **Step 1: Define interface**

`src/main/java/com/example/service/BookingService.java`:

```java
package com.example.service;

import com.example.dto.BookingDTO;
import com.example.dto.CreateBookingRequest;
import com.example.dto.CreateBookingResponse;

import java.util.List;

public interface BookingService {

    CreateBookingResponse createBooking(CreateBookingRequest request) throws Exception;

    BookingDTO getBooking(Long bookingId);

    List<BookingDTO> getBookingsByUserId(Long userId);

    /** Mark booking as CONFIRMED and persist razorpay payment id. */
    void markConfirmed(Long bookingId, String razorpayPaymentId);

    /** Mark booking as FAILED and release inventory. */
    void markFailed(Long bookingId);

    /** Release any PENDING bookings whose hold has expired. Returns count released. */
    int expireHolds();
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/service/BookingService.java
git commit -m "feat: define BookingService interface"
```

---

## Task 10: BookingService createBooking — TDD with concurrency test

**Files:**
- Create: `src/test/resources/application.properties` (if it doesn't already exist)
- Create: `src/main/java/com/example/service/impl/BookingServiceImpl.java`
- Create: `src/test/java/com/example/service/BookingServiceImplTest.java`

- [ ] **Step 0: Add test-profile H2 config**

Check if `src/test/resources/application.properties` exists. If not, create it with:

```properties
spring.datasource.url=jdbc:h2:mem:ticketmaster_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
spring.sql.init.mode=never

# Razorpay test placeholders — real values come from @TestPropertySource on each test class
RAZORPAY_KEY_ID=rzp_test_dummy
RAZORPAY_KEY_SECRET=key-secret-dummy
RAZORPAY_WEBHOOK_SECRET=webhook-secret-dummy
BOOKING_HOLD_MINUTES=10
```

This makes `@SpringBootTest` boot against H2 instead of the production PostgreSQL config.

- [ ] **Step 1: Write the failing test (concurrency + happy path)**

`src/test/java/com/example/service/BookingServiceImplTest.java`:

```java
package com.example.service;

import com.example.dao.BookingItemRepository;
import com.example.dao.BookingRepository;
import com.example.dao.TicketTierRepository;
import com.example.dto.CreateBookingRequest;
import com.example.dto.CreateBookingResponse;
import com.example.entity.BookingStatus;
import com.example.entity.TicketTier;
import com.example.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "RAZORPAY_KEY_ID=rzp_test_dummy",
    "RAZORPAY_KEY_SECRET=key-secret-dummy",
    "RAZORPAY_WEBHOOK_SECRET=webhook-secret-dummy",
    "BOOKING_HOLD_MINUTES=10"
})
class BookingServiceImplTest {

    @Autowired private BookingServiceImpl bookingService;
    @Autowired private TicketTierRepository tierRepo;
    @Autowired private BookingRepository bookingRepo;
    @Autowired private BookingItemRepository bookingItemRepo;

    @MockBean private RazorpayService razorpayService;

    private Long tierId;

    @BeforeEach
    void setUp() throws Exception {
        bookingItemRepo.deleteAll();
        bookingRepo.deleteAll();
        tierRepo.deleteAll();

        TicketTier tier = new TicketTier();
        tier.setEventId(1L);
        tier.setName("Standard");
        tier.setPriceInrPaise(50000L); // ₹500
        tier.setTotalQuantity(2);
        tier.setAvailableQuantity(2);
        tierId = tierRepo.save(tier).getId();

        Mockito.when(razorpayService.createOrder(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn("order_test_dummy");
        Mockito.when(razorpayService.getKeyId()).thenReturn("rzp_test_dummy");
    }

    @Test
    void createBooking_happyPath_decrementsInventoryAndPersistsBooking() throws Exception {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setUserId(1L);
        req.setEventId(1L);
        CreateBookingRequest.Item item = new CreateBookingRequest.Item();
        item.setTierId(tierId);
        item.setQuantity(1);
        req.setItems(List.of(item));

        CreateBookingResponse resp = bookingService.createBooking(req);

        assertNotNull(resp.getBookingId());
        assertEquals("order_test_dummy", resp.getRazorpayOrderId());
        assertEquals(50000L, resp.getAmountPaise());
        assertEquals("INR", resp.getCurrency());

        assertEquals(1, tierRepo.findById(tierId).orElseThrow().getAvailableQuantity());
        assertEquals(BookingStatus.PENDING, bookingRepo.findById(resp.getBookingId()).orElseThrow().getStatus());
    }

    @Test
    void createBooking_concurrent_doesNotOversellLastTwoTickets() throws Exception {
        int threadCount = 5;
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final long userId = 100L + i;
            futures.add(exec.submit(() -> {
                try {
                    start.await();
                    CreateBookingRequest req = new CreateBookingRequest();
                    req.setUserId(userId);
                    req.setEventId(1L);
                    CreateBookingRequest.Item item = new CreateBookingRequest.Item();
                    item.setTierId(tierId);
                    item.setQuantity(1);
                    req.setItems(List.of(item));
                    bookingService.createBooking(req);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(10, TimeUnit.SECONDS);
        exec.shutdown();

        assertEquals(2, successes.get(), "exactly 2 threads should succeed");
        assertEquals(3, failures.get(), "remaining 3 should fail with out-of-stock");
        assertEquals(0, tierRepo.findById(tierId).orElseThrow().getAvailableQuantity());
    }

    @Test
    void createBooking_insufficientInventory_throws() {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setUserId(1L);
        req.setEventId(1L);
        CreateBookingRequest.Item item = new CreateBookingRequest.Item();
        item.setTierId(tierId);
        item.setQuantity(99);
        req.setItems(List.of(item));

        assertThrows(IllegalStateException.class, () -> bookingService.createBooking(req));
        assertEquals(2, tierRepo.findById(tierId).orElseThrow().getAvailableQuantity());
    }
}
```

- [ ] **Step 2: Run test (will fail — impl missing)**

Run: `mvn -q -Dtest=BookingServiceImplTest test`
Expected: compilation error or test failures because `BookingServiceImpl` doesn't exist yet.

- [ ] **Step 3: Implement BookingServiceImpl (createBooking only)**

`src/main/java/com/example/service/impl/BookingServiceImpl.java`:

```java
package com.example.service.impl;

import com.example.ApplicationConstants;
import com.example.dao.BookingItemRepository;
import com.example.dao.BookingRepository;
import com.example.dao.TicketTierRepository;
import com.example.dto.*;
import com.example.entity.*;
import com.example.service.BookingService;
import com.example.service.RazorpayService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BookingServiceImpl implements BookingService {

    private final TicketTierRepository tierRepo;
    private final BookingRepository bookingRepo;
    private final BookingItemRepository bookingItemRepo;
    private final RazorpayService razorpayService;
    private final int holdMinutes;

    public BookingServiceImpl(
            TicketTierRepository tierRepo,
            BookingRepository bookingRepo,
            BookingItemRepository bookingItemRepo,
            RazorpayService razorpayService,
            @Value("${BOOKING_HOLD_MINUTES:10}") int holdMinutes) {
        this.tierRepo = tierRepo;
        this.bookingRepo = bookingRepo;
        this.bookingItemRepo = bookingItemRepo;
        this.razorpayService = razorpayService;
        this.holdMinutes = holdMinutes;
    }

    @Override
    @Transactional
    public CreateBookingResponse createBooking(CreateBookingRequest request) throws Exception {
        long totalPaise = 0;
        List<BookingItem> items = new ArrayList<>();
        List<Long> decrementedTierIds = new ArrayList<>();
        List<Integer> decrementedQtys = new ArrayList<>();

        try {
            for (CreateBookingRequest.Item req : request.getItems()) {
                TicketTier tier = tierRepo.findById(req.getTierId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                ApplicationConstants.TIER_NOT_FOUND + req.getTierId()));

                int updated = tierRepo.decrementIfAvailable(tier.getId(), req.getQuantity());
                if (updated == 0) {
                    throw new IllegalStateException(
                            ApplicationConstants.INSUFFICIENT_INVENTORY + tier.getName());
                }
                decrementedTierIds.add(tier.getId());
                decrementedQtys.add(req.getQuantity());

                long lineTotal = tier.getPriceInrPaise() * req.getQuantity();
                totalPaise += lineTotal;

                BookingItem it = new BookingItem();
                it.setTicketTierId(tier.getId());
                it.setQuantity(req.getQuantity());
                it.setUnitPricePaise(tier.getPriceInrPaise());
                it.setLineTotalPaise(lineTotal);
                items.add(it);
            }

            Booking booking = new Booking();
            booking.setUserId(request.getUserId());
            booking.setEventId(request.getEventId());
            booking.setStatus(BookingStatus.PENDING);
            booking.setTotalAmountPaise(totalPaise);
            booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(holdMinutes));
            booking = bookingRepo.save(booking);

            for (BookingItem it : items) {
                it.setBookingId(booking.getId());
            }
            bookingItemRepo.saveAll(items);

            String orderId = razorpayService.createOrder(totalPaise, "INR", "bk_" + booking.getId());
            booking.setRazorpayOrderId(orderId);
            bookingRepo.save(booking);

            return new CreateBookingResponse(
                    booking.getId(), orderId, razorpayService.getKeyId(), totalPaise, "INR");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // Razorpay order create failed — release any holds we placed.
            for (int i = 0; i < decrementedTierIds.size(); i++) {
                tierRepo.incrementAvailable(decrementedTierIds.get(i), decrementedQtys.get(i));
            }
            throw e;
        }
    }

    @Override
    public BookingDTO getBooking(Long bookingId) {
        Booking b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException(ApplicationConstants.BOOKING_NOT_FOUND + bookingId));
        return toDto(b);
    }

    @Override
    public List<BookingDTO> getBookingsByUserId(Long userId) {
        return bookingRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void markConfirmed(Long bookingId, String razorpayPaymentId) {
        Booking b = bookingRepo.findById(bookingId).orElseThrow();
        if (b.getStatus() == BookingStatus.CONFIRMED) return; // idempotent
        b.setStatus(BookingStatus.CONFIRMED);
        b.setRazorpayPaymentId(razorpayPaymentId);
        bookingRepo.save(b);
    }

    @Override
    @Transactional
    public void markFailed(Long bookingId) {
        Booking b = bookingRepo.findById(bookingId).orElseThrow();
        if (b.getStatus() != BookingStatus.PENDING) return;
        b.setStatus(BookingStatus.FAILED);
        bookingRepo.save(b);
        for (BookingItem it : bookingItemRepo.findByBookingId(bookingId)) {
            tierRepo.incrementAvailable(it.getTicketTierId(), it.getQuantity());
        }
    }

    @Override
    @Transactional
    public int expireHolds() {
        List<Booking> expired = bookingRepo.findExpiredHolds(BookingStatus.PENDING, LocalDateTime.now());
        for (Booking b : expired) {
            b.setStatus(BookingStatus.EXPIRED);
            bookingRepo.save(b);
            for (BookingItem it : bookingItemRepo.findByBookingId(b.getId())) {
                tierRepo.incrementAvailable(it.getTicketTierId(), it.getQuantity());
            }
        }
        return expired.size();
    }

    private BookingDTO toDto(Booking b) {
        List<BookingItemDTO> itemDtos = bookingItemRepo.findByBookingId(b.getId()).stream()
                .map(it -> {
                    Optional<TicketTier> tier = tierRepo.findById(it.getTicketTierId());
                    return new BookingItemDTO(
                            it.getId(),
                            it.getTicketTierId(),
                            tier.map(TicketTier::getName).orElse(null),
                            it.getQuantity(),
                            it.getUnitPricePaise(),
                            it.getLineTotalPaise());
                }).collect(Collectors.toList());
        return new BookingDTO(
                b.getId(), b.getUserId(), b.getEventId(), b.getStatus().name(),
                b.getTotalAmountPaise(), b.getHoldExpiresAt(),
                b.getRazorpayOrderId(), b.getRazorpayPaymentId(), itemDtos, b.getCreatedAt());
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q -Dtest=BookingServiceImplTest test`
Expected: all 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/service/impl/BookingServiceImpl.java src/test/java/com/example/service/BookingServiceImplTest.java src/test/resources/application.properties
git commit -m "feat: BookingService.createBooking with hold-on-checkout inventory + concurrency test"
```

---

## Task 11: BookingService.expireHolds test

**Files:**
- Modify: `src/test/java/com/example/service/BookingServiceImplTest.java`

- [ ] **Step 1: Add expireHolds test**

Append inside `BookingServiceImplTest`:

```java
    @Test
    void expireHolds_releasesPendingBookingsPastTTL_andRestoresInventory() throws Exception {
        // Create booking
        CreateBookingRequest req = new CreateBookingRequest();
        req.setUserId(1L);
        req.setEventId(1L);
        CreateBookingRequest.Item item = new CreateBookingRequest.Item();
        item.setTierId(tierId);
        item.setQuantity(2);
        req.setItems(List.of(item));
        CreateBookingResponse resp = bookingService.createBooking(req);

        // Force hold_expires_at into the past
        com.example.entity.Booking b = bookingRepo.findById(resp.getBookingId()).orElseThrow();
        b.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));
        bookingRepo.save(b);

        int released = bookingService.expireHolds();

        assertEquals(1, released);
        assertEquals(BookingStatus.EXPIRED,
                bookingRepo.findById(resp.getBookingId()).orElseThrow().getStatus());
        assertEquals(2, tierRepo.findById(tierId).orElseThrow().getAvailableQuantity());
    }
```

Make sure the test file has `import java.time.LocalDateTime;` at the top (add it if missing).

- [ ] **Step 2: Run test**

Run: `mvn -q -Dtest=BookingServiceImplTest#expireHolds_releasesPendingBookingsPastTTL_andRestoresInventory test`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/example/service/BookingServiceImplTest.java
git commit -m "test: BookingService.expireHolds releases stale holds and restores inventory"
```

---

# Phase 5 — Webhook service (idempotent processing)

## Task 12: WebhookService interface + impl

**Files:**
- Create: `src/main/java/com/example/service/WebhookService.java`
- Create: `src/main/java/com/example/service/impl/WebhookServiceImpl.java`

- [ ] **Step 1: Define interface**

`src/main/java/com/example/service/WebhookService.java`:

```java
package com.example.service;

public interface WebhookService {
    /**
     * Process a Razorpay webhook. Returns one of: "ok", "duplicate",
     * "invalid_signature", "ignored". Never throws on duplicate events.
     */
    String process(String rawPayload, String signatureHeader);
}
```

- [ ] **Step 2: Implement service**

`src/main/java/com/example/service/impl/WebhookServiceImpl.java`:

```java
package com.example.service.impl;

import com.example.dao.BookingRepository;
import com.example.dao.PaymentRepository;
import com.example.entity.Booking;
import com.example.entity.Payment;
import com.example.service.BookingService;
import com.example.service.RazorpayService;
import com.example.service.WebhookService;
import org.json.JSONObject;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class WebhookServiceImpl implements WebhookService {

    private final RazorpayService razorpayService;
    private final PaymentRepository paymentRepo;
    private final BookingRepository bookingRepo;
    private final BookingService bookingService;

    public WebhookServiceImpl(RazorpayService razorpayService,
                              PaymentRepository paymentRepo,
                              BookingRepository bookingRepo,
                              BookingService bookingService) {
        this.razorpayService = razorpayService;
        this.paymentRepo = paymentRepo;
        this.bookingRepo = bookingRepo;
        this.bookingService = bookingService;
    }

    @Override
    @Transactional
    public String process(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || !razorpayService.verifyWebhookSignature(rawPayload, signatureHeader)) {
            return "invalid_signature";
        }

        JSONObject json = new JSONObject(rawPayload);
        String eventId = json.optString("id", null);
        String eventType = json.optString("event", null);
        if (eventId == null) return "ignored";

        if (paymentRepo.existsByRazorpayEventId(eventId)) {
            return "duplicate";
        }

        JSONObject paymentEntity = json
                .optJSONObject("payload", new JSONObject())
                .optJSONObject("payment", new JSONObject())
                .optJSONObject("entity", new JSONObject());

        String razorpayPaymentId = paymentEntity.optString("id", null);
        String razorpayOrderId = paymentEntity.optString("order_id", null);
        Long amountPaise = paymentEntity.has("amount") ? paymentEntity.getLong("amount") : null;
        String status = paymentEntity.optString("status", null);

        Optional<Booking> maybeBooking = razorpayOrderId == null
                ? Optional.empty()
                : bookingRepo.findByRazorpayOrderId(razorpayOrderId);

        if (maybeBooking.isEmpty()) return "ignored";
        Booking booking = maybeBooking.get();

        Payment record = new Payment();
        record.setBookingId(booking.getId());
        record.setRazorpayPaymentId(razorpayPaymentId);
        record.setRazorpayEventId(eventId);
        record.setEventType(eventType);
        record.setAmountPaise(amountPaise);
        record.setStatus(status);
        record.setRawPayload(rawPayload);

        try {
            paymentRepo.save(record);
        } catch (DataIntegrityViolationException dup) {
            return "duplicate";
        }

        switch (eventType == null ? "" : eventType) {
            case "payment.captured":
                bookingService.markConfirmed(booking.getId(), razorpayPaymentId);
                return "ok";
            case "payment.failed":
                bookingService.markFailed(booking.getId());
                return "ok";
            default:
                return "ignored";
        }
    }
}
```

- [ ] **Step 3: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/service/WebhookService.java src/main/java/com/example/service/impl/WebhookServiceImpl.java
git commit -m "feat: WebhookService with signature verification and idempotent processing"
```

---

## Task 13: WebhookService tests (idempotency + payment.captured)

**Files:**
- Create: `src/test/java/com/example/service/WebhookServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/service/WebhookServiceImplTest.java`:

```java
package com.example.service;

import com.example.dao.*;
import com.example.dto.CreateBookingRequest;
import com.example.dto.CreateBookingResponse;
import com.example.entity.BookingStatus;
import com.example.entity.TicketTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "RAZORPAY_KEY_ID=rzp_test_dummy",
    "RAZORPAY_KEY_SECRET=key-secret-dummy",
    "RAZORPAY_WEBHOOK_SECRET=webhook-secret-dummy",
    "BOOKING_HOLD_MINUTES=10"
})
class WebhookServiceImplTest {

    @Autowired private WebhookService webhookService;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepo;
    @Autowired private BookingItemRepository bookingItemRepo;
    @Autowired private PaymentRepository paymentRepo;
    @Autowired private TicketTierRepository tierRepo;

    @MockBean private RazorpayService razorpayService;

    private Long bookingId;
    private String razorpayOrderId = "order_test_webhook_1";

    @BeforeEach
    void setUp() throws Exception {
        paymentRepo.deleteAll();
        bookingItemRepo.deleteAll();
        bookingRepo.deleteAll();
        tierRepo.deleteAll();

        TicketTier tier = new TicketTier();
        tier.setEventId(1L);
        tier.setName("VIP");
        tier.setPriceInrPaise(100000L);
        tier.setTotalQuantity(5);
        tier.setAvailableQuantity(5);
        Long tierId = tierRepo.save(tier).getId();

        Mockito.when(razorpayService.createOrder(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(razorpayOrderId);
        Mockito.when(razorpayService.getKeyId()).thenReturn("rzp_test_dummy");
        Mockito.when(razorpayService.verifyWebhookSignature(Mockito.anyString(), Mockito.eq("valid-sig")))
                .thenReturn(true);
        Mockito.when(razorpayService.verifyWebhookSignature(Mockito.anyString(), Mockito.eq("bad-sig")))
                .thenReturn(false);

        CreateBookingRequest req = new CreateBookingRequest();
        req.setUserId(1L);
        req.setEventId(1L);
        CreateBookingRequest.Item item = new CreateBookingRequest.Item();
        item.setTierId(tierId);
        item.setQuantity(1);
        req.setItems(List.of(item));
        CreateBookingResponse resp = bookingService.createBooking(req);
        bookingId = resp.getBookingId();
    }

    private String payload(String eventId, String eventType, String paymentId) {
        return "{" +
                "\"id\":\"" + eventId + "\"," +
                "\"event\":\"" + eventType + "\"," +
                "\"payload\":{\"payment\":{\"entity\":{" +
                "\"id\":\"" + paymentId + "\"," +
                "\"order_id\":\"" + razorpayOrderId + "\"," +
                "\"amount\":100000," +
                "\"status\":\"captured\"" +
                "}}}" +
                "}";
    }

    @Test
    void process_paymentCaptured_marksBookingConfirmed() {
        String result = webhookService.process(payload("evt_1", "payment.captured", "pay_1"), "valid-sig");

        assertEquals("ok", result);
        assertEquals(BookingStatus.CONFIRMED, bookingRepo.findById(bookingId).orElseThrow().getStatus());
        assertEquals("pay_1", bookingRepo.findById(bookingId).orElseThrow().getRazorpayPaymentId());
    }

    @Test
    void process_duplicateEventId_isIdempotent() {
        String p = payload("evt_dup", "payment.captured", "pay_1");
        assertEquals("ok", webhookService.process(p, "valid-sig"));
        assertEquals("duplicate", webhookService.process(p, "valid-sig"));
        assertEquals(1L, paymentRepo.count());
    }

    @Test
    void process_invalidSignature_rejected() {
        String result = webhookService.process(payload("evt_2", "payment.captured", "pay_2"), "bad-sig");
        assertEquals("invalid_signature", result);
        assertEquals(0L, paymentRepo.count());
        assertEquals(BookingStatus.PENDING, bookingRepo.findById(bookingId).orElseThrow().getStatus());
    }

    @Test
    void process_paymentFailed_releasesInventoryAndMarksFailed() {
        int before = tierRepo.findAll().get(0).getAvailableQuantity();

        String result = webhookService.process(payload("evt_3", "payment.failed", "pay_3"), "valid-sig");

        assertEquals("ok", result);
        assertEquals(BookingStatus.FAILED, bookingRepo.findById(bookingId).orElseThrow().getStatus());
        assertEquals(before + 1, tierRepo.findAll().get(0).getAvailableQuantity());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn -q -Dtest=WebhookServiceImplTest test`
Expected: all 4 tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/example/service/WebhookServiceImplTest.java
git commit -m "test: WebhookService idempotency, signature, payment.captured, payment.failed"
```

---

# Phase 6 — TicketTier service + controller

## Task 14: TicketTierService + impl

**Files:**
- Create: `src/main/java/com/example/service/TicketTierService.java`
- Create: `src/main/java/com/example/service/impl/TicketTierServiceImpl.java`

- [ ] **Step 1: Define interface**

`src/main/java/com/example/service/TicketTierService.java`:

```java
package com.example.service;

import com.example.dto.TicketTierDTO;

import java.util.List;

public interface TicketTierService {
    TicketTierDTO create(Long eventId, TicketTierDTO dto);
    List<TicketTierDTO> findByEventId(Long eventId);
}
```

- [ ] **Step 2: Implement**

`src/main/java/com/example/service/impl/TicketTierServiceImpl.java`:

```java
package com.example.service.impl;

import com.example.dao.TicketTierRepository;
import com.example.dto.TicketTierDTO;
import com.example.entity.TicketTier;
import com.example.service.TicketTierService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TicketTierServiceImpl implements TicketTierService {

    private final TicketTierRepository repo;

    public TicketTierServiceImpl(TicketTierRepository repo) {
        this.repo = repo;
    }

    @Override
    public TicketTierDTO create(Long eventId, TicketTierDTO dto) {
        TicketTier t = new TicketTier();
        t.setEventId(eventId);
        t.setName(dto.getName());
        t.setPriceInrPaise(dto.getPriceInrPaise());
        t.setTotalQuantity(dto.getTotalQuantity());
        t.setAvailableQuantity(dto.getTotalQuantity());
        TicketTier saved = repo.save(t);
        return toDto(saved);
    }

    @Override
    public List<TicketTierDTO> findByEventId(Long eventId) {
        return repo.findByEventId(eventId).stream().map(this::toDto).collect(Collectors.toList());
    }

    private TicketTierDTO toDto(TicketTier t) {
        return new TicketTierDTO(t.getId(), t.getEventId(), t.getName(),
                t.getPriceInrPaise(), t.getTotalQuantity(), t.getAvailableQuantity());
    }
}
```

- [ ] **Step 3: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/service/TicketTierService.java src/main/java/com/example/service/impl/TicketTierServiceImpl.java
git commit -m "feat: TicketTierService for per-event tier management"
```

---

## Task 15: TicketTierController

**Files:**
- Create: `src/main/java/com/example/controller/TicketTierController.java`

- [ ] **Step 1: Create controller**

`src/main/java/com/example/controller/TicketTierController.java`:

```java
package com.example.controller;

import com.example.ApplicationConstants;
import com.example.dto.TicketTierDTO;
import com.example.service.TicketTierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApplicationConstants.EVENTS_ENDPOINT + "/{eventId}" + ApplicationConstants.TIERS_ENDPOINT)
public class TicketTierController {

    private final TicketTierService service;

    public TicketTierController(TicketTierService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@PathVariable Long eventId,
                                                      @Valid @RequestBody TicketTierDTO dto) {
        try {
            TicketTierDTO saved = service.create(eventId, dto);
            Map<String, Object> body = new HashMap<>();
            body.put("status", "success");
            body.put("message", ApplicationConstants.CREATED);
            body.put("data", saved);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", "error");
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@PathVariable Long eventId) {
        List<TicketTierDTO> tiers = service.findByEventId(eventId);
        Map<String, Object> body = new HashMap<>();
        body.put("status", "success");
        body.put("message", ApplicationConstants.SUCCESS);
        body.put("data", tiers);
        return ResponseEntity.ok(body);
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/controller/TicketTierController.java
git commit -m "feat: TicketTierController for creating and listing tiers per event"
```

---

# Phase 7 — Booking + Webhook controllers

## Task 16: BookingController

**Files:**
- Create: `src/main/java/com/example/controller/BookingController.java`

- [ ] **Step 1: Create controller**

`src/main/java/com/example/controller/BookingController.java`:

```java
package com.example.controller;

import com.example.ApplicationConstants;
import com.example.dto.BookingDTO;
import com.example.dto.ClientAckRequest;
import com.example.dto.CreateBookingRequest;
import com.example.dto.CreateBookingResponse;
import com.example.service.BookingService;
import com.example.service.RazorpayService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApplicationConstants.BOOKINGS_ENDPOINT)
public class BookingController {

    private final BookingService bookingService;
    private final RazorpayService razorpayService;

    public BookingController(BookingService bookingService, RazorpayService razorpayService) {
        this.bookingService = bookingService;
        this.razorpayService = razorpayService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateBookingRequest req) {
        try {
            CreateBookingResponse resp = bookingService.createBooking(req);
            Map<String, Object> body = new HashMap<>();
            body.put("status", "success");
            body.put("message", ApplicationConstants.CREATED);
            body.put("data", resp);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", "error");
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        } catch (Exception e) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", "error");
            body.put("message", "Failed to create booking: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        try {
            BookingDTO dto = bookingService.getBooking(id);
            Map<String, Object> body = new HashMap<>();
            body.put("status", "success");
            body.put("message", ApplicationConstants.SUCCESS);
            body.put("data", dto);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", "error");
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> listForUser(@PathVariable Long userId) {
        List<BookingDTO> bookings = bookingService.getBookingsByUserId(userId);
        Map<String, Object> body = new HashMap<>();
        body.put("status", "success");
        body.put("message", ApplicationConstants.SUCCESS);
        body.put("data", bookings);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/client-ack")
    public ResponseEntity<Map<String, Object>> clientAck(@PathVariable Long id,
                                                         @Valid @RequestBody ClientAckRequest req) {
        boolean ok = razorpayService.verifyCheckoutSignature(
                req.getRazorpayOrderId(), req.getRazorpayPaymentId(), req.getRazorpaySignature());
        Map<String, Object> body = new HashMap<>();
        body.put("status", ok ? "success" : "error");
        body.put("message", ok ? "ack received" : "invalid signature");
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.UNAUTHORIZED).body(body);
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/controller/BookingController.java
git commit -m "feat: BookingController with create, get, list by user, and client-ack endpoints"
```

---

## Task 17: WebhookController (raw body reading)

**Files:**
- Create: `src/main/java/com/example/controller/WebhookController.java`

- [ ] **Step 1: Create controller**

`src/main/java/com/example/controller/WebhookController.java`:

```java
package com.example.controller;

import com.example.ApplicationConstants;
import com.example.service.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(ApplicationConstants.WEBHOOKS_RAZORPAY_ENDPOINT)
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        String result = webhookService.process(rawBody, signature);
        Map<String, Object> body = new HashMap<>();
        body.put("result", result);

        HttpStatus status;
        switch (result) {
            case "ok":
            case "duplicate":
            case "ignored":
                status = HttpStatus.OK;
                break;
            case "invalid_signature":
                status = HttpStatus.UNAUTHORIZED;
                break;
            default:
                status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(body);
    }
}
```

**Note:** Using `@RequestBody String rawBody` ensures we receive the unparsed raw body, which is what HMAC verification requires.

- [ ] **Step 2: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/controller/WebhookController.java
git commit -m "feat: WebhookController forwards raw body to WebhookService for verification"
```

---

# Phase 8 — Scheduled expiry job

## Task 18: BookingExpiryJob + test

**Files:**
- Create: `src/main/java/com/example/scheduled/BookingExpiryJob.java`
- Create: `src/test/java/com/example/scheduled/BookingExpiryJobTest.java`

- [ ] **Step 1: Create scheduled job**

`src/main/java/com/example/scheduled/BookingExpiryJob.java`:

```java
package com.example.scheduled;

import com.example.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

    private final BookingService bookingService;

    public BookingExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void releaseExpiredHolds() {
        int released = bookingService.expireHolds();
        if (released > 0) {
            log.info("Released {} expired booking hold(s)", released);
        }
    }
}
```

- [ ] **Step 2: Write the job test**

`src/test/java/com/example/scheduled/BookingExpiryJobTest.java`:

```java
package com.example.scheduled;

import com.example.service.BookingService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BookingExpiryJobTest {

    @Test
    void releaseExpiredHolds_delegatesToBookingService() {
        BookingService svc = Mockito.mock(BookingService.class);
        Mockito.when(svc.expireHolds()).thenReturn(3);

        new BookingExpiryJob(svc).releaseExpiredHolds();

        verify(svc, times(1)).expireHolds();
    }
}
```

- [ ] **Step 3: Run test**

Run: `mvn -q -Dtest=BookingExpiryJobTest test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/scheduled/BookingExpiryJob.java src/test/java/com/example/scheduled/BookingExpiryJobTest.java
git commit -m "feat: BookingExpiryJob runs every 60s to release stale holds"
```

---

# Phase 9 — Run all backend tests + smoke test the API

## Task 19: Full backend test pass

- [ ] **Step 1: Run all tests**

Run: `mvn -q test`
Expected: BUILD SUCCESS; all tests green.

If any test fails, fix the underlying issue before proceeding.

- [ ] **Step 2: Start the app locally**

Set your `.env` with test Razorpay keys (from https://dashboard.razorpay.com/app/keys in test mode). Then:

Run: `mvn -q spring-boot:run`
Expected: app starts on port 8080.

- [ ] **Step 3: Create a tier and a booking manually**

```bash
# Create a tier for event 1
curl -X POST http://localhost:8080/api/events/1/tiers \
  -H "Content-Type: application/json" \
  -d '{"name":"Standard","priceInrPaise":50000,"totalQuantity":10}'

# Create a booking
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"eventId":1,"items":[{"tierId":1,"quantity":2}]}'
```
Expected: booking response includes `razorpayOrderId` and `amountPaise=100000`.

- [ ] **Step 4: Stop the app and commit any fixes**

```bash
git add -u
git commit -m "fix: resolve issues uncovered by full-suite test run" || echo "no fixes needed"
```

---

# Phase 10 — Frontend

> All paths below are relative to `ticketmaster_fe/`.

## Task 20: Load Razorpay SDK + booking types + services

**Files:**
- Modify: `index.html`
- Create: `src/types/booking.ts`
- Create: `src/service/bookingService.ts`
- Create: `src/service/paymentService.ts`
- Modify: `src/service/index.ts`

- [ ] **Step 1: Load Razorpay SDK**

Edit `index.html`. Inside `<head>`, before the closing tag, add:

```html
<script src="https://checkout.razorpay.com/v1/checkout.js"></script>
```

- [ ] **Step 2: Add booking types**

`src/types/booking.ts`:

```ts
export interface TicketTier {
  id: number;
  eventId: number;
  name: string;
  priceInrPaise: number;
  totalQuantity: number;
  availableQuantity: number;
}

export interface BookingItemDto {
  id: number;
  ticketTierId: number;
  tierName: string;
  quantity: number;
  unitPricePaise: number;
  lineTotalPaise: number;
}

export interface Booking {
  id: number;
  userId: number;
  eventId: number;
  status: "PENDING" | "CONFIRMED" | "EXPIRED" | "FAILED" | "REFUNDED";
  totalAmountPaise: number;
  holdExpiresAt: string;
  razorpayOrderId: string | null;
  razorpayPaymentId: string | null;
  items: BookingItemDto[];
  createdAt: string;
}

export interface CreateBookingResponse {
  bookingId: number;
  razorpayOrderId: string;
  razorpayKeyId: string;
  amountPaise: number;
  currency: string;
}

export interface ClientAckRequest {
  razorpayOrderId: string;
  razorpayPaymentId: string;
  razorpaySignature: string;
}
```

- [ ] **Step 3: Booking service**

`src/service/bookingService.ts`:

```ts
import type {
  Booking,
  CreateBookingResponse,
  ClientAckRequest,
} from "../types/booking";

const API = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

interface CreateBookingPayload {
  userId: number;
  eventId: number;
  items: { tierId: number; quantity: number }[];
}

async function asJson<T>(res: Response): Promise<T> {
  const json = await res.json();
  if (!res.ok || json.status === "error") {
    throw new Error(json.message ?? `HTTP ${res.status}`);
  }
  return json.data as T;
}

export const bookingService = {
  create: (payload: CreateBookingPayload): Promise<CreateBookingResponse> =>
    fetch(`${API}/bookings`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    }).then(asJson<CreateBookingResponse>),

  get: (bookingId: number): Promise<Booking> =>
    fetch(`${API}/bookings/${bookingId}`).then(asJson<Booking>),

  listForUser: (userId: number): Promise<Booking[]> =>
    fetch(`${API}/bookings/user/${userId}`).then(asJson<Booking[]>),

  clientAck: (bookingId: number, req: ClientAckRequest): Promise<void> =>
    fetch(`${API}/bookings/${bookingId}/client-ack`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(req),
    }).then(asJson<void>),
};
```

- [ ] **Step 4: Payment / Razorpay-checkout service**

`src/service/paymentService.ts`:

```ts
import type { CreateBookingResponse, ClientAckRequest } from "../types/booking";

declare global {
  interface Window {
    Razorpay: new (opts: RazorpayOptions) => { open: () => void };
  }
}

interface RazorpayOptions {
  key: string;
  order_id: string;
  amount: number;
  currency: string;
  name?: string;
  description?: string;
  handler: (resp: {
    razorpay_payment_id: string;
    razorpay_order_id: string;
    razorpay_signature: string;
  }) => void;
  modal?: { ondismiss?: () => void };
}

export function openCheckout(
  booking: CreateBookingResponse,
  options: {
    eventName: string;
    onSuccess: (ack: ClientAckRequest) => void;
    onDismiss?: () => void;
  }
): void {
  if (typeof window.Razorpay !== "function") {
    throw new Error("Razorpay Checkout SDK not loaded");
  }

  const rzp = new window.Razorpay({
    key: booking.razorpayKeyId,
    order_id: booking.razorpayOrderId,
    amount: booking.amountPaise,
    currency: booking.currency,
    name: "Ticketmaster",
    description: options.eventName,
    handler: (resp) => {
      options.onSuccess({
        razorpayOrderId: resp.razorpay_order_id,
        razorpayPaymentId: resp.razorpay_payment_id,
        razorpaySignature: resp.razorpay_signature,
      });
    },
    modal: { ondismiss: options.onDismiss },
  });
  rzp.open();
}
```

- [ ] **Step 5: Export from `service/index.ts`**

Append to `src/service/index.ts`:

```ts
export { bookingService } from "./bookingService";
export { openCheckout } from "./paymentService";
```

- [ ] **Step 6: Type-check**

Run: `yarn build`
Expected: build passes (TypeScript compiles, Vite bundles).

- [ ] **Step 7: Commit**

```bash
git add index.html src/types/booking.ts src/service/bookingService.ts src/service/paymentService.ts src/service/index.ts
git commit -m "feat: add booking types, services, and Razorpay Checkout integration"
```

---

## Task 21: EventDetailPage with tier picker and Buy button

**Files:**
- Create: `src/pages/EventDetailPage.tsx`

- [ ] **Step 1: Create the page**

`src/pages/EventDetailPage.tsx`:

```tsx
import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Button, NumberInput, Stack, Group, Title, Text, Card } from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { bookingService, openCheckout } from "../service";
import type { TicketTier } from "../types/booking";

const API = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";
// Placeholder until auth is wired up — replace with the logged-in user id from your auth context.
const CURRENT_USER_ID = 1;

export default function EventDetailPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const navigate = useNavigate();
  const [tiers, setTiers] = useState<TicketTier[]>([]);
  const [quantities, setQuantities] = useState<Record<number, number>>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!eventId) return;
    fetch(`${API}/events/${eventId}/tiers`)
      .then((r) => r.json())
      .then((j) => setTiers(j.data ?? []))
      .catch((e) => notifications.show({ color: "red", message: e.message }));
  }, [eventId]);

  const totalPaise = tiers.reduce(
    (sum, t) => sum + (quantities[t.id] ?? 0) * t.priceInrPaise,
    0
  );

  const handleBuy = async () => {
    const items = Object.entries(quantities)
      .filter(([, q]) => q > 0)
      .map(([tierId, q]) => ({ tierId: Number(tierId), quantity: q }));
    if (items.length === 0) {
      notifications.show({ color: "yellow", message: "Pick at least 1 ticket" });
      return;
    }

    setSubmitting(true);
    try {
      const booking = await bookingService.create({
        userId: CURRENT_USER_ID,
        eventId: Number(eventId),
        items,
      });
      openCheckout(booking, {
        eventName: `Event #${eventId}`,
        onSuccess: async (ack) => {
          try {
            await bookingService.clientAck(booking.bookingId, ack);
          } catch (e) {
            // Webhook will still confirm — ack is best-effort.
            console.warn("client-ack failed", e);
          }
          navigate(`/bookings/${booking.bookingId}/confirmation`);
        },
        onDismiss: () => {
          notifications.show({
            color: "yellow",
            message: "Payment cancelled. Your hold will expire shortly.",
          });
          setSubmitting(false);
        },
      });
    } catch (e: any) {
      notifications.show({ color: "red", message: e.message ?? "Booking failed" });
      setSubmitting(false);
    }
  };

  return (
    <Stack p="lg">
      <Title order={2}>Event #{eventId}</Title>
      {tiers.length === 0 && <Text>No tiers available for this event.</Text>}
      {tiers.map((t) => (
        <Card key={t.id} withBorder padding="md">
          <Group justify="space-between">
            <div>
              <Text fw={600}>{t.name}</Text>
              <Text size="sm" c="dimmed">
                ₹{(t.priceInrPaise / 100).toFixed(2)} · {t.availableQuantity} left
              </Text>
            </div>
            <NumberInput
              min={0}
              max={t.availableQuantity}
              value={quantities[t.id] ?? 0}
              onChange={(v) =>
                setQuantities((q) => ({ ...q, [t.id]: typeof v === "number" ? v : 0 }))
              }
              w={120}
            />
          </Group>
        </Card>
      ))}
      <Group justify="space-between">
        <Text fw={700}>Total: ₹{(totalPaise / 100).toFixed(2)}</Text>
        <Button onClick={handleBuy} loading={submitting} disabled={totalPaise === 0}>
          Buy
        </Button>
      </Group>
    </Stack>
  );
}
```

- [ ] **Step 2: Type-check**

Run: `yarn build`
Expected: build passes.

- [ ] **Step 3: Commit**

```bash
git add src/pages/EventDetailPage.tsx
git commit -m "feat: EventDetailPage with tier picker, totals, and Razorpay Buy flow"
```

---

## Task 22: BookingConfirmationPage (polls for CONFIRMED)

**Files:**
- Create: `src/pages/BookingConfirmationPage.tsx`

- [ ] **Step 1: Create page**

`src/pages/BookingConfirmationPage.tsx`:

```tsx
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Title, Text, Stack, Loader, Card, List } from "@mantine/core";
import { bookingService } from "../service";
import type { Booking } from "../types/booking";

const POLL_INTERVAL_MS = 2000;
const TIMEOUT_MS = 30_000;

export default function BookingConfirmationPage() {
  const { id } = useParams<{ id: string }>();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [timedOut, setTimedOut] = useState(false);

  useEffect(() => {
    if (!id) return;
    const bookingId = Number(id);
    const started = Date.now();
    let stop = false;

    const tick = async () => {
      if (stop) return;
      try {
        const b = await bookingService.get(bookingId);
        setBooking(b);
        if (b.status === "CONFIRMED" || b.status === "FAILED" || b.status === "EXPIRED") {
          return;
        }
      } catch {
        // ignore transient errors
      }
      if (Date.now() - started > TIMEOUT_MS) {
        setTimedOut(true);
        return;
      }
      setTimeout(tick, POLL_INTERVAL_MS);
    };
    tick();
    return () => {
      stop = true;
    };
  }, [id]);

  if (!booking) {
    return (
      <Stack p="lg" align="center">
        <Loader />
        <Text>Confirming your booking...</Text>
      </Stack>
    );
  }

  if (booking.status === "CONFIRMED") {
    return (
      <Stack p="lg">
        <Title order={2}>🎉 Booking confirmed</Title>
        <Card withBorder padding="md">
          <Text>Booking #{booking.id}</Text>
          <Text>Total: ₹{(booking.totalAmountPaise / 100).toFixed(2)}</Text>
          <List>
            {booking.items.map((it) => (
              <List.Item key={it.id}>
                {it.tierName} × {it.quantity} (₹{(it.lineTotalPaise / 100).toFixed(2)})
              </List.Item>
            ))}
          </List>
        </Card>
      </Stack>
    );
  }

  if (timedOut) {
    return (
      <Stack p="lg">
        <Title order={3}>Still processing</Title>
        <Text>
          We haven't received confirmation yet. Check your bookings list — it should appear
          there shortly.
        </Text>
      </Stack>
    );
  }

  return (
    <Stack p="lg" align="center">
      <Loader />
      <Text>Status: {booking.status}</Text>
    </Stack>
  );
}
```

- [ ] **Step 2: Type-check**

Run: `yarn build`
Expected: build passes.

- [ ] **Step 3: Commit**

```bash
git add src/pages/BookingConfirmationPage.tsx
git commit -m "feat: BookingConfirmationPage polls until CONFIRMED or timeout"
```

---

## Task 23: Wire up routes

**Files:**
- Modify: `src/App.tsx` (or whichever file holds the router)

- [ ] **Step 1: Inspect current router**

Open `src/App.tsx`. Locate where `<Routes>` is defined. (If using a separate router file, edit there instead.)

- [ ] **Step 2: Add new routes**

Inside `<Routes>`, add:

```tsx
import EventDetailPage from "./pages/EventDetailPage";
import BookingConfirmationPage from "./pages/BookingConfirmationPage";

// ... inside <Routes>:
<Route path="/events/:eventId" element={<EventDetailPage />} />
<Route path="/bookings/:id/confirmation" element={<BookingConfirmationPage />} />
```

- [ ] **Step 3: Type-check**

Run: `yarn build`
Expected: build passes.

- [ ] **Step 4: Commit**

```bash
git add src/App.tsx
git commit -m "feat: route /events/:id to detail page and /bookings/:id/confirmation"
```

---

## Task 24: Add MyBookings to ProfilePage

**Files:**
- Modify: `src/pages/ProfilePage.tsx`

- [ ] **Step 1: Inspect ProfilePage**

Open `src/pages/ProfilePage.tsx` to see the existing structure and identify where to insert the new section.

- [ ] **Step 2: Add MyBookings section**

Add these imports at the top:

```tsx
import { useEffect, useState } from "react";
import { bookingService } from "../service";
import type { Booking } from "../types/booking";
import { Card, Text, Stack, Title, Badge } from "@mantine/core";
```

Inside the component, after existing state, add:

```tsx
const CURRENT_USER_ID = 1; // placeholder; wire to auth context later
const [bookings, setBookings] = useState<Booking[]>([]);

useEffect(() => {
  bookingService
    .listForUser(CURRENT_USER_ID)
    .then(setBookings)
    .catch(() => setBookings([]));
}, []);
```

In the rendered JSX, add a section:

```tsx
<Stack mt="xl">
  <Title order={3}>My Bookings</Title>
  {bookings.length === 0 && <Text c="dimmed">No bookings yet.</Text>}
  {bookings.map((b) => (
    <Card key={b.id} withBorder padding="sm">
      <Text fw={600}>Booking #{b.id} · Event #{b.eventId}</Text>
      <Badge
        color={
          b.status === "CONFIRMED"
            ? "green"
            : b.status === "PENDING"
            ? "yellow"
            : "gray"
        }
      >
        {b.status}
      </Badge>
      <Text size="sm">₹{(b.totalAmountPaise / 100).toFixed(2)}</Text>
    </Card>
  ))}
</Stack>
```

- [ ] **Step 3: Type-check**

Run: `yarn build`
Expected: build passes.

- [ ] **Step 4: Commit**

```bash
git add src/pages/ProfilePage.tsx
git commit -m "feat: ProfilePage shows current user's bookings"
```

---

## Task 25: CreateEvent — add tier definition

**Files:**
- Modify: `src/pages/CreateEvent.tsx`

- [ ] **Step 1: Inspect CreateEvent**

Open `src/pages/CreateEvent.tsx` to see how the event-creation form currently submits. Identify the success handler (after the event is created and you have an `eventId`).

- [ ] **Step 2: Add tier inputs to the form**

Add a state slice for tiers:

```tsx
import { ActionIcon, Button, Group, NumberInput, TextInput, Stack, Text } from "@mantine/core";
import { IconPlus, IconTrash } from "@tabler/icons-react";
// ...
interface TierDraft {
  name: string;
  priceInrPaise: number;
  totalQuantity: number;
}

const [tiers, setTiers] = useState<TierDraft[]>([
  { name: "Standard", priceInrPaise: 50000, totalQuantity: 100 },
]);
```

In the form JSX (before the submit button), add:

```tsx
<Stack>
  <Text fw={600}>Ticket Tiers</Text>
  {tiers.map((t, idx) => (
    <Group key={idx} align="end">
      <TextInput
        label="Name"
        value={t.name}
        onChange={(e) => {
          const v = e.currentTarget.value;
          setTiers((arr) => arr.map((x, i) => (i === idx ? { ...x, name: v } : x)));
        }}
      />
      <NumberInput
        label="Price (paise)"
        value={t.priceInrPaise}
        min={0}
        onChange={(v) =>
          setTiers((arr) =>
            arr.map((x, i) => (i === idx ? { ...x, priceInrPaise: typeof v === "number" ? v : 0 } : x))
          )
        }
      />
      <NumberInput
        label="Quantity"
        value={t.totalQuantity}
        min={1}
        onChange={(v) =>
          setTiers((arr) =>
            arr.map((x, i) => (i === idx ? { ...x, totalQuantity: typeof v === "number" ? v : 1 } : x))
          )
        }
      />
      <ActionIcon
        color="red"
        variant="subtle"
        onClick={() => setTiers((arr) => arr.filter((_, i) => i !== idx))}
        disabled={tiers.length === 1}
      >
        <IconTrash size={16} />
      </ActionIcon>
    </Group>
  ))}
  <Button
    variant="light"
    leftSection={<IconPlus size={16} />}
    onClick={() =>
      setTiers((arr) => [...arr, { name: "", priceInrPaise: 0, totalQuantity: 1 }])
    }
  >
    Add tier
  </Button>
</Stack>
```

- [ ] **Step 3: POST tiers after event create**

In the existing submit handler, after the event is created and you have its id (e.g. `createdEventId`), add:

```tsx
const API = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";
for (const t of tiers) {
  await fetch(`${API}/events/${createdEventId}/tiers`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(t),
  });
}
```

- [ ] **Step 4: Type-check**

Run: `yarn build`
Expected: build passes.

- [ ] **Step 5: Commit**

```bash
git add src/pages/CreateEvent.tsx
git commit -m "feat: CreateEvent posts ticket tiers after event creation"
```

---

# Phase 11 — End-to-end verification

## Task 26: Local end-to-end run

- [ ] **Step 1: Start the backend**

```bash
cd /Users/oindriladas/Documents/prozect/ticketmaster
mvn -q spring-boot:run
```
Expected: app on port 8080.

- [ ] **Step 2: Start the frontend**

In a second terminal:
```bash
cd /Users/oindriladas/Documents/prozect/ticketmaster_fe
yarn dev
```
Expected: Vite dev server on port 5173.

- [ ] **Step 3: Expose the backend webhook publicly (for Razorpay test mode)**

Install ngrok if not present (`brew install ngrok`), then in a third terminal:

```bash
ngrok http 8080
```
Copy the `https://...ngrok-free.app` URL.

- [ ] **Step 4: Register webhook in Razorpay dashboard**

In https://dashboard.razorpay.com (test mode) → Webhooks → Add Webhook:
- URL: `https://<ngrok-id>.ngrok-free.app/api/webhooks/razorpay`
- Secret: same value you set in `.env` as `RAZORPAY_WEBHOOK_SECRET`
- Events: `payment.captured`, `payment.failed`

- [ ] **Step 5: Run the full flow**

1. Open http://localhost:5173 → create an event (with at least one tier) → submit.
2. Navigate to `/events/<id>` → pick a quantity → click Buy → Razorpay modal opens.
3. Pay with a Razorpay test card (e.g. `4111 1111 1111 1111`, any future expiry, any CVV, any 3DS OTP).
4. Confirmation page polls and flips to ✅ within a few seconds.
5. Visit ProfilePage → booking listed as CONFIRMED.

- [ ] **Step 6: Verify oversell protection manually**

1. Create a tier with `totalQuantity=1`.
2. In two browser tabs, open the event page and click Buy simultaneously.
3. One should proceed to Checkout; the other should get "Insufficient tickets available".

- [ ] **Step 7: Verify expiry**

1. Create a tier, start a booking, but close the Razorpay modal without paying.
2. Wait > 10 min (or temporarily set `BOOKING_HOLD_MINUTES=1` for testing).
3. The expiry job logs `Released 1 expired booking hold(s)` and the tier's `availableQuantity` recovers.

- [ ] **Step 8: Final commit (if any cleanup)**

```bash
git add -u
git commit -m "chore: minor cleanups from end-to-end run" || echo "nothing to commit"
```

---

# Done

You have:
- A full booking + payment data model with concurrency-safe inventory.
- Razorpay Orders API + Checkout + signed webhook integration.
- Idempotent webhook handling via DB-level uniqueness.
- A scheduled job that reconciles expired holds.
- React UI: tier picker, Buy → Razorpay Checkout, polling confirmation, MyBookings, Create-with-tiers.
- Test coverage for signature verification, concurrent inventory, idempotency, expiry, and the scheduled job.
