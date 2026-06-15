# Razorpay Payments + Ticket Booking — Design

**Date:** 2026-06-15
**Project:** Ticketmaster (Spring Boot backend + React/TS frontend)
**Status:** Approved, ready for implementation plan

## Goal

Add ticket booking with paid checkout to the existing event platform. Users browse events, pick a ticket tier and quantity, pay via Razorpay, and receive confirmed tickets. The payment integration must be production-grade: webhook-driven confirmation, signature verification, idempotent processing, and safe inventory under concurrent purchases.

## Scope

- **In:** Ticket tiers per event (e.g., VIP / Standard), quantity + tier selection, Razorpay Standard Checkout, server-side Orders API, signed webhook for confirmation, hold-on-checkout inventory with TTL, scheduled release of expired holds, user-facing booking history.
- **Out (deferred):** Multi-event cart, seat selection, refunds via UI (DB model supports it; flow can come later), discount codes, partial payments, multi-currency. INR only.

## Approach

**Razorpay Standard Checkout + Orders API + Webhook** (textbook integration):

1. Backend creates a Razorpay Order via the SDK.
2. Frontend opens the Razorpay Checkout modal with the `order_id` and key_id.
3. User pays in the modal.
4. Razorpay sends a webhook (`payment.captured`) to the backend.
5. Backend verifies HMAC signature, dedupes by event_id, and flips the booking to `CONFIRMED`.

Rejected alternatives:
- **Razorpay Payment Links** — too thin for a resume project; doesn't exercise the Orders/Webhook flow.
- **Pessimistic row locks across the payment** — locks held across external API calls are an anti-pattern.
- **Trusting the frontend `onSuccess` callback as source of truth** — insecure. Frontend callback is for UX only; the webhook is authoritative.

## Data Model

Four new tables alongside the existing `events`, `users`, `locations`. Money is stored as `paise` (long) to match Razorpay's API and avoid floating-point bugs.

```
ticket_tiers
  id BIGSERIAL PK
  event_id BIGINT FK -> events(id)
  name VARCHAR(50)              -- "VIP", "Standard"
  price_inr_paise BIGINT NOT NULL
  total_quantity INT NOT NULL
  available_quantity INT NOT NULL  -- decremented on hold, restored on expiry
  created_at TIMESTAMP

bookings
  id BIGSERIAL PK
  user_id BIGINT FK -> users(id)
  event_id BIGINT FK -> events(id)
  status VARCHAR(20) NOT NULL    -- PENDING | CONFIRMED | EXPIRED | FAILED | REFUNDED
  total_amount_paise BIGINT NOT NULL
  hold_expires_at TIMESTAMP NOT NULL
  razorpay_order_id VARCHAR(100) -- set after Order create
  razorpay_payment_id VARCHAR(100) -- set after payment.captured webhook
  created_at TIMESTAMP
  updated_at TIMESTAMP

booking_items
  id BIGSERIAL PK
  booking_id BIGINT FK -> bookings(id)
  ticket_tier_id BIGINT FK -> ticket_tiers(id)
  quantity INT NOT NULL
  unit_price_paise BIGINT NOT NULL  -- snapshot at booking time
  line_total_paise BIGINT NOT NULL

payments  -- audit log: one row per processed webhook event
  id BIGSERIAL PK
  booking_id BIGINT FK -> bookings(id)
  razorpay_payment_id VARCHAR(100)
  razorpay_event_id VARCHAR(100) UNIQUE NOT NULL  -- idempotency key
  event_type VARCHAR(50)        -- payment.captured, payment.failed, refund.processed, ...
  amount_paise BIGINT
  status VARCHAR(20)
  raw_payload JSONB
  received_at TIMESTAMP
```

The `UNIQUE` constraint on `payments.razorpay_event_id` is the idempotency guarantee: replays of the same webhook insert is rejected by the DB, and the handler treats that as a no-op.

## End-to-End Flow

```
[Frontend]                    [Spring Boot API]              [Razorpay]
    |                                |                            |
    |--- POST /api/bookings -------->|                            |
    |    {eventId, items:[{tierId,qty}]}                          |
    |                                |--- TX: decrement available_qty per tier
    |                                |        insert booking PENDING
    |                                |        hold_expires_at = now + 10min
    |                                |--- POST /v1/orders ------->|
    |                                |<-- {order_id, amount} -----|
    |                                |--- save razorpay_order_id
    |<-- {bookingId, orderId, keyId, amountPaise} ---             |
    |                                                             |
    |--- open Razorpay Checkout (JS SDK) ------------------------>|
    |    user pays in modal                                       |
    |<-- onSuccess({paymentId, signature}) ----------------------|
    |--- POST /api/bookings/{id}/client-ack ----->|              |
    |    (verify signature, mark client_ack       |              |
    |     for UX; NOT authoritative)              |              |
    |                                             |              |
    |                                             |<-- webhook: payment.captured
    |                                             |--- verify HMAC SHA256 with webhook_secret
    |                                             |--- dedupe: INSERT INTO payments (event_id UNIQUE)
    |                                             |--- TX: booking PENDING -> CONFIRMED
    |                                             |--- (optional) email user
```

**Source of truth:** the webhook. The client `onSuccess` callback is a UX optimization only.

**Expiry job:** `@Scheduled(fixedDelay = 60_000)` finds bookings with `status=PENDING AND hold_expires_at < now()`, flips them to `EXPIRED`, and returns `quantity` to each `ticket_tier.available_quantity` in a single transaction.

## Backend Changes (Spring Boot)

Follow the existing `controller → service → dao → entity/dto + validation` layered pattern.

**Entities:** `TicketTier`, `Booking`, `BookingItem`, `Payment`

**DTOs:**
- `CreateBookingRequest { eventId, items:[{tierId, quantity}] }`
- `CreateBookingResponse { bookingId, razorpayOrderId, razorpayKeyId, amountPaise, currency }`
- `RazorpayWebhookPayload` (typed wrapper around the JSON)
- `ClientAckRequest { paymentId, orderId, signature }`

**Repositories:** `TicketTierRepository`, `BookingRepository`, `BookingItemRepository`, `PaymentRepository`

**Services:**
- `BookingService` — `createBooking`, `getBooking`, `getUserBookings`, `expireHolds`
- `RazorpayService` — wraps `com.razorpay:razorpay-java` SDK; `createOrder`, `verifyWebhookSignature`, `verifyCheckoutSignature`
- `WebhookService` — verify signature, insert payment row (dedupe), dispatch by event_type, update booking

**Controllers:**
- `BookingController` — `POST /api/bookings`, `GET /api/bookings/{id}`, `POST /api/bookings/{id}/client-ack`, `GET /api/users/{userId}/bookings`
- `WebhookController` — `POST /api/webhooks/razorpay`. **Must read raw request body** (not parsed JSON) for HMAC verification — use a `HttpServletRequest` and read the input stream once, then parse.
- `TicketTierController` — `POST /api/events/{eventId}/tiers`, `GET /api/events/{eventId}/tiers`, `PUT /api/tiers/{id}`

**Scheduled job:** `BookingExpiryJob` — `@Scheduled(fixedDelay = 60_000)`, calls `BookingService.expireHolds()`.

**Config / env:**
- `RAZORPAY_KEY_ID`
- `RAZORPAY_KEY_SECRET`
- `RAZORPAY_WEBHOOK_SECRET`
- `BOOKING_HOLD_MINUTES` (default 10)

Add to `.env.example`, load via the existing `spring-dotenv` setup.

**Maven dependency:** `com.razorpay:razorpay-java:1.4.8`

**Enable scheduling:** add `@EnableScheduling` to `Application.java`.

## Frontend Changes (React + TS + Vite)

- **New page:** `EventDetailPage.tsx` — event info + list of tiers + per-tier quantity stepper + total + "Buy" button. Mantine components, Tailwind for layout.
- **New page:** `BookingConfirmationPage.tsx` — receives `bookingId`, polls `GET /api/bookings/{id}` every 2s until `status === CONFIRMED` or timeout (~30s). Shows ticket details on confirm.
- **New services:** `bookingService.ts`, `paymentService.ts`
- **Razorpay JS SDK:** load `https://checkout.razorpay.com/v1/checkout.js` in `index.html`.
- **Buy handler:**
  1. `POST /api/bookings` → get `{bookingId, razorpayOrderId, razorpayKeyId, amountPaise}`
  2. Open `Razorpay({key, order_id, amount, currency: 'INR', handler: onSuccess})`
  3. In `onSuccess`, `POST /api/bookings/{id}/client-ack` then navigate to confirmation page.
- **Edit `ProfilePage.tsx`:** add a `MyBookings` section calling `GET /api/users/{userId}/bookings`.
- **Edit `CreateEvent.tsx`:** allow the host to define ticket tiers (name, price, quantity) when creating an event.

## Error Handling

- Insufficient inventory at booking creation → `400 Bad Request` with which tier ran out.
- Razorpay Order create fails → roll back the hold (release inventory), return `502`.
- Webhook signature mismatch → `401 Unauthorized`, do not process.
- Duplicate webhook (same event_id) → DB unique violation, caught and treated as success (idempotent).
- `payment.failed` webhook → mark booking `FAILED`, release inventory.
- Booking accessed by non-owner → `403 Forbidden`.

## Testing Strategy

- **Unit:** `RazorpayService.verifyWebhookSignature` — HMAC SHA256 with `webhook_secret`, golden-payload assertions.
- **Integration (H2, already in project):**
  - `BookingService.createBooking` — concurrent requests with `CompletableFuture` racing for the last 2 tickets; assert no oversell.
  - Webhook idempotency — POST the same event_id twice; assert only one `PENDING → CONFIRMED` transition.
  - Expiry job — seed a booking with `hold_expires_at` in the past; run job; assert `EXPIRED` and inventory restored.
- **Manual:** Razorpay test mode keys + ngrok for webhook delivery to localhost during dev.

## Open Questions / Future Work

- Email/SMS notification on confirm (out of scope for v1).
- Refund flow exposed in the UI (model supports it; controller not built).
- Replace polling on the confirmation page with WebSocket / SSE.
- Multi-currency support (currently INR only).

## Resume Bullet

> **Integrated Razorpay payments end-to-end** — backend Orders API, hosted Checkout on the frontend, and HMAC-signed webhook as the source-of-truth confirmation — with a hold-on-checkout inventory model (PENDING booking + 10-minute TTL + scheduled release job) and idempotent webhook handling to prevent double-charges or oversold tickets under concurrent purchases.
