# Buy a Number

A native Android client for [5sim.net](https://5sim.net) — buy a disposable phone
number, watch the verification code arrive, and keep an eye on your account,
without leaving your phone.

Built with Kotlin, Jetpack Compose (Material 3), Hilt, Retrofit and Room.

## What it does

**Buy** — pick a country, then a service, then an operator. Prices, remaining
stock and the recent delivery success rate come straight from 5sim, so you can
see what you are paying for before you commit. Both pickers are searchable,
which matters when the catalog runs to ~200 countries and several hundred
services.

**Sort** — operators sort by price (either direction), remaining stock or
success rate; services by name, price or stock; order history by newest or
oldest. Sold-out rows always sink to the bottom, whatever the sort — a sold-out
operator at a great price is still not something you can buy. Your choice is
remembered. Catalog sorting is local, so switching costs nothing; order history
sorts through 5sim's own paging parameters, because sorting one loaded page
would misrepresent the whole list.

**Add funds** — top up without leaving the app. 5sim's API is read-only where
money is concerned, so the payment itself happens on their hosted page (opened
in a tab over the app, so no card details ever reach this code). The balance is
snapshotted before the hand-off and polled on return, so the moment the money
lands you get a confirmation instead of having to go looking.

**Check messages** — the order screen shows the number with a one-tap copy
button, a live countdown to expiry, and the SMS thread as it fills in. It polls
every five seconds while the order is open. The verification code is pulled out
and shown on its own — from 5sim's `code` field where it exists, and otherwise
from the message body. From here you can finish the order, cancel it for a
refund, or report the number as unusable.

**Follow your account** — balance, held balance and rating on the home screen;
full order history with infinite scroll; and the payment history behind it. A
background worker keeps following your open numbers after you leave the app and
raises a notification the moment a code lands — tapping it opens straight to
that number.

## Getting started

1. Create an API key at 5sim.net under **Settings → API key**.
2. Install the app and paste the key into the first screen. It is checked
   against `user/profile` before it is saved, so a bad key is rejected up front.
3. The key is sealed with an AES-GCM key held in the Android Keystore, which
   never leaves the device. Backup and device-transfer are disabled for the
   app's data, since a leaked 5sim key can spend your balance.

## Building

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleRelease      # minified release APK
```

CI (`.github/workflows/android.yml`) runs the tests and lint and uploads both
APKs as build artifacts on every push.

> The release build is signed with the debug keystore so CI can produce an
> installable APK. Point `signingConfig` at a real keystore before publishing.

## How the 5sim API is used

Base URL `https://5sim.net/v1/`. `user/` calls carry `Authorization: Bearer
<key>`; `guest/` calls are public, so the catalog stays browsable before you
sign in.

| Endpoint | Used for |
| --- | --- |
| `GET user/profile` | Balance, rating, key validation |
| `GET user/orders` | Order history (paginated) |
| `GET user/payments` | Payment history, and confirming a top-up landed |
| `GET user/buy/activation/{country}/{operator}/{product}` | Buying a number |
| `GET user/check/{id}` | Polling for SMS |
| `GET user/finish/{id}` | Closing a used order |
| `GET user/cancel/{id}` | Refunding an unused order |
| `GET user/ban/{id}` | Reporting a bad number |
| `GET user/sms/inbox/{id}` | Full SMS thread |
| `GET guest/countries` | Country list, ISO codes and dialling prefixes |
| `GET guest/products/{country}/{operator}` | Services for a country |
| `GET guest/prices?country=&product=` | Per-operator price, stock, success rate |

Note what is **not** there: 5sim has no deposit endpoint. `user/payments` reads
history but nothing creates a payment, which is why topping up opens
`https://5sim.net/payment` in a Custom Tab rather than posting to an API.

Two quirks of the API are handled explicitly, because both would otherwise
surface as confusing crashes:

- **Plain-text errors on HTTP 200.** `no free phones` and friends come back with
  a success status and a bare string body. `PlainTextErrorInterceptor` rewrites
  any successful non-JSON response into a failure, and `FiveSimError` maps the
  text onto a typed error with a message worth showing a user.
- **`guest/prices` changes shape.** The response nests country → product →
  operator, but levels are dropped depending on which filters were sent.
  `flattenPrices` walks the tree until it finds a leaf and reads the path back
  from the right, filling missing levels from the query.

## Architecture

```
core/        Errors, Result wrapper, date parsing, formatters
data/
  remote/    Retrofit API, DTOs, auth + error interceptors
  local/     Keystore-sealed API key, settings, Room cache of open orders
  mapper/    DTO → domain
  repository/Account, Catalog, Order
domain/      Models the UI speaks in
work/        Background SMS polling and notifications
ui/          Compose screens, each with its own ViewModel
```

Repositories return `Result<T>` with failures already normalised to
`FiveSimError`, so no ViewModel has to reason about HTTP status codes. Open
orders are mirrored in Room, which is what lets the home screen render instantly
and offline and gives the background worker a list to follow.

Polling deserves a note: WorkManager's periodic work has a 15-minute floor,
which is useless for an SMS code. Instead `OrderTracker` chains short one-shot
runs — each poll reschedules itself while orders are still open and simply stops
when none are. The watermark of already-notified messages lives in the database,
so a killed worker process does not replay old alerts.

## Tests

```bash
./gradlew testDebugUnitTest
```

49 tests covering verification-code extraction, the timestamp formats 5sim
emits, the plain-text error mapping (end to end through OkHttp with
MockWebServer), every shape of the `guest/prices` tree, the documented response
payloads, and every sort comparator including the sold-out and missing-rate
edge cases.
