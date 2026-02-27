# VapeItReorder

## What This Project Is
A Spring Boot app that compares prices across vape product distributors. It queries the VapeIt API for items below minimum stock, looks up supplier URLs in a local PostgreSQL database, and uses Playwright to scrape the current price from each supplier's website.

## How It Works
1. `PlaytestApp` starts as an ApplicationRunner (runs once, then exits)
2. `ItemApiClient` calls VapeIt API (`GET /api/items`) to fetch inventory items
3. Items are filtered by `needsReorder()` — true when `unidadesActuales < minimoUnidades`
4. `ProductoDistribuidoraRepository` looks up URLs for each SKU by distributor
5. URLs are grouped by distributor (currently only Vaperalia)
6. `VaperaliaPlaytestService` opens a headless Chromium browser, validates the saved session, and scrapes each product page for name and price
7. Errors are logged per-item so one failure doesn't block others

## Relationship to VapeIt
This is a **sibling project** to `/Users/daniel/code/VapeIt` (the main inventory app). It depends on VapeIt's REST API running at `localhost:8080` but has no shared code. Communication is purely via HTTP. It also has its own PostgreSQL database for storing distributor URLs.

The VapeIt `Item` entity extends `Referencia` (a `@MappedSuperclass`). Key fields from `Referencia`: `id`, `sku`, `codBarras`, `nombre`, `precioCompra`, `iva`, `recargoEquivalencia`, `peso`, `tamano`, `minimoUnidadesCompra`. `Item` adds: `minimoUnidades`, `maximoUnidades`, `fechaUltimaCompra`, `precioVenta`.

## Tech Stack
- Java 21, Spring Boot 3.5.0 (starter-web + starter-data-jpa)
- Playwright Java 1.49.0 for browser automation
- PostgreSQL 17 (Docker Compose, port 5433)
- Spring Data JPA + Hibernate
- RestTemplate for API calls
- Maven build

## Project Structure
```
src/main/java/org/ppoole/vapeitreorder/playtest/app/
├── PlaytestApp.java                         # Entry point, ApplicationRunner
├── config/
│   └── RestTemplateConfig.java              # RestTemplate bean
├── domain/
│   ├── Distribuidora.java                   # Entity: id, name (unique)
│   ├── Producto.java                        # Entity: sku (PK), nombre
│   ├── ProductoDistribuidora.java           # Entity: id, producto (FK), distribuidora (FK), url
│   └── ProductoRespuesta.java               # POJO: sku, nombre, precio, url, distribuidora
├── dto/
│   └── ItemDto.java                         # DTO from VapeIt API: sku, unidadesActuales, minimoUnidades, maximoUnidades, needsReorder()
├── repository/
│   ├── DistribuidoraRepository.java         # findByName()
│   ├── ProductoRepository.java              # standard CRUD
│   └── ProductoDistribuidoraRepository.java # findSkuUrlDistribuidoraTriosBySkuIn() (native query, returns SkuUrlDistribuidoraTrio projection)
├── service/
│   └── ItemApiClient.java                   # GET /api/items → filters needsReorder() → returns SKU list
└── vaperalia/
    └── VaperaliaPlaytestService.java        # --login (save session) / scrape modes with Playwright
```

## Current State
The Vaperalia scraping flow is **fully functional**:
- `saveSession()` — opens visible browser, user logs in, session saved to `vaperalia-session.json`
- `scrape(urls)` — headless browser, validates session, navigates each URL, extracts name + price
- Price is read from DOM element `#our_price_display` (not `window.productPrice` which has race conditions)
- Eciglogistica scraping is not yet implemented

## Configuration
All in `src/main/resources/application.yml`:
- `vapeit.api-url` — VapeIt API endpoint (default `http://localhost:8080`)
- `spring.datasource.*` — PostgreSQL connection (port 5433, db `vapeit_reorder`)
- `spring.jpa.hibernate.ddl-auto: update` — auto-creates tables

## Key Design Decisions
- **Browser per run**: A fresh Chromium instance is created each run
- **Log and continue**: API failures, individual item failures, and browser crashes are logged but never crash the service
- **Session-based auth**: Vaperalia session is saved to a JSON file and reused across runs. Re-login only needed when session expires
- **DOM scraping over JS variables**: Price is read from `#our_price_display` element, not `window.productPrice`, to avoid race conditions
- **Trio projection**: Repository returns `(sku, url, distribuidoraName)` tuples via native query to avoid N+1 joins

## Coding Style & Conventions
Follow the patterns established in both this project and the sibling VapeIt project:

- **No Lombok** — use explicit getters/setters, no `@Data` or `@Builder`
- **Constructor injection** — never use `@Autowired` on fields. All dependencies go through constructor parameters
- **`@Value` in constructors** — config properties are injected via `@Value("${...}")` directly in constructor parameters, not on fields
- **SLF4J logging** — `private static final Logger log = LoggerFactory.getLogger(ClassName.class)` with `{}` placeholders, never string concatenation
- **Spanish domain names, English code** — field names like `minimoUnidades`, `nombre`, `precioVenta` reflect the business domain. Code structure (class names, method names, log messages) is in English
- **Minimal annotations** — only use what's needed. No `@Component` when `@Service` is more descriptive. No `@Autowired` at all.
- **Plain Java POJOs for DTOs** — no records, no Lombok. `@JsonIgnoreProperties(ignoreUnknown = true)` on DTOs that consume external APIs
- **`var` for local type inference** — use `var` when the type is obvious from the right side
- **Stream API** — use `.stream().filter().toList()` for collection filtering
- **try-with-resources** — for anything `AutoCloseable` (Playwright, Browser)
- **Per-item error handling** — catch exceptions inside loops so one failure doesn't block the rest
- **No javadoc, no comments unless necessary** — code should be self-explanatory. Use `// TODO:` only for genuinely unfinished work
- **One blank line** between methods, no trailing whitespace
- **Imports** — no wildcards, organized by package (jakarta, java, com, org)

## Running
```bash
# Start PostgreSQL
docker compose up -d

# Install Playwright browser (first time only)
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"

# Save Vaperalia session (first time or when expired)
mvn spring-boot:run -Dspring-boot.run.arguments="--login"

# Run price comparison (VapeIt backend must be up at localhost:8080)
mvn spring-boot:run
```
