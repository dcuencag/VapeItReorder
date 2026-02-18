# VapeItReorder

## What This Project Is
A headless Spring Boot microservice that automatically reorders vape product inventory. It runs as a background worker (no web server) and checks stock levels twice daily. When items fall below their minimum threshold, it uses Playwright to automate ordering on the supplier website.

## How It Works
1. A cron job fires at **14:10** and **21:10** daily
2. `ReorderScheduler` calls the VapeIt API (`GET /api/items`) to fetch all inventory items
3. Items are filtered by `needsReorder()` — true when `currentStock < minimoUnidades`
4. For each item needing reorder, `PlaywrightOrderService` opens a Chromium browser, logs into https://nueva.eciglogistica.com, and places an order for `maximoUnidades - currentStock` units
5. Errors are logged per-item so one failure doesn't block others. The next scheduled run acts as a natural retry.

## Relationship to VapeIt
This is a **sibling project** to `/Users/daniel/code/VapeIt` (the main inventory app). It depends on VapeIt's REST API running at `localhost:8080` but has no shared code or database. Communication is purely via HTTP.

The VapeIt `Item` entity extends `Referencia` (a `@MappedSuperclass`). Key fields from `Referencia`: `id`, `sku`, `codBarras`, `nombre`, `precioCompra`, `iva`, `recargoEquivalencia`, `peso`, `tamano`, `minimoUnidadesCompra`. `Item` adds: `minimoUnidades`, `maximoUnidades`, `fechaUltimaCompra`, `precioVenta`. The fields `currentStock` and `supplierUrl` are being added by a partner.

## Tech Stack
- Java 21, Spring Boot 3.5.0 (starter only, no Tomcat)
- Playwright Java 1.49.0 for browser automation
- RestTemplate for API calls
- No database

## Project Structure
```
src/main/java/org/ppoole/vapeitreorder/
├── VapeItReorderApplication.java    # Entry point, @EnableScheduling
├── config/
│   └── RestTemplateConfig.java      # RestTemplate bean
├── dto/
│   └── ItemDto.java                 # API response DTO, needsReorder() logic
└── service/
    ├── ItemApiClient.java           # Calls VapeIt GET /api/items
    ├── ReorderScheduler.java        # Cron trigger, filters items, orchestrates
    └── PlaywrightOrderService.java  # Browser automation (login + order placeholders)
```

## Current State
The project compiles and runs, but **PlaywrightOrderService has placeholder methods**:
- `login(Page)` — navigates to the supplier site but selectors are commented out
- `orderItem(Page, ItemDto)` — calculates quantity but doesn't interact with the page yet

These need to be filled in after inspecting the actual DOM of https://nueva.eciglogistica.com.

## Configuration
All in `src/main/resources/application.yml`:
- `vapeit.api-url` — VapeIt API endpoint
- `reorder.cron` — schedule expression (Spring 6-field cron)
- `eciglogistica.url`, `eciglogistica.username`, `eciglogistica.password` — supplier credentials (blank for now)
- `playwright.headless` — run browser headless or visible

## Key Design Decisions
- **Browser per run**: A fresh Chromium instance is created each run, not kept alive across the 7+ hour gap between schedules
- **Log and continue**: API failures, individual item failures, and browser crashes are logged but never crash the service
- **No retry logic**: The twice-daily schedule is sufficient; no exponential backoff needed
- **Reorder quantity**: Always tops up to `maximoUnidades`

## Coding Style & Conventions
Follow the patterns established in both this project and the sibling VapeIt project:

- **No Lombok** — use explicit getters/setters, no `@Data` or `@Builder`
- **Constructor injection** — never use `@Autowired` on fields. All dependencies go through constructor parameters (see `ReorderScheduler`, `ItemApiClient`, `PlaywrightOrderService`)
- **`@Value` in constructors** — config properties are injected via `@Value("${...}")` directly in constructor parameters, not on fields
- **SLF4J logging** — `private static final Logger log = LoggerFactory.getLogger(ClassName.class)` with `{}` placeholders, never string concatenation
- **Spanish domain names, English code** — field names like `minimoUnidades`, `nombre`, `precioVenta` reflect the business domain. Code structure (class names, method names, log messages) is in English
- **Minimal annotations** — only use what's needed. No `@Component` when `@Service` is more descriptive. No `@Autowired` at all.
- **Plain Java POJOs for DTOs** — no records, no Lombok. `@JsonIgnoreProperties(ignoreUnknown = true)` on DTOs that consume external APIs
- **`var` for local type inference** — use `var` when the type is obvious from the right side (see `ItemApiClient.fetchAllItems()`)
- **Stream API** — use `.stream().filter().toList()` for collection filtering (see `ReorderScheduler`)
- **try-with-resources** — for anything `AutoCloseable` (Playwright, Browser)
- **Per-item error handling** — catch exceptions inside loops so one failure doesn't block the rest
- **No javadoc, no comments unless necessary** — code should be self-explanatory. Use `// TODO:` only for genuinely unfinished work
- **One blank line** between methods, no trailing whitespace
- **Imports** — no wildcards, organized by package (jakarta, java, com, org)

## Running
```bash
# Build
mvn clean package -DskipTests

# Install Playwright browser (first time only)
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"

# Run (VapeIt backend must be up at localhost:8080)
java -jar target/vapeit-reorder-1.0.0-SNAPSHOT.jar

# To test without waiting for cron, change reorder.cron in application.yml to:
# "0 * * * * *"   (every minute)
```
