# VapeItReorder - Memory Bank

## Project Overview
Spring Boot microservice que automatiza el rellenado del carrito en webs de distribuidores cuando el stock baja del mínimo. Corre en el **puerto 8081**. El backend VapeIt corre en el 8080.

## How It Works
1. Cron fires at 14:30 and 21:30 → `ReorderScheduler` calls VapeIt API (`GET /api/items`)
2. Filters items where `currentStock < minimoUnidades`
3. `BotEngine` groups items by `distribuidor`, opens one Chromium browser per distributor
4. Each `DistributorBot` logs in, adds items to cart, scrapes cart → returns `CartResultDto`
5. `OrderReporter` POSTs each `CartResultDto` to `vapeit.report-url`
6. `POST /trigger` on port 8081 fires a manual run

## Tech Stack
- Java 21, Spring Boot 3.5.0 + spring-boot-starter-web (embedded Tomcat, port 8081)
- Playwright Java 1.49.0 — Chromium installed at `/Users/daniel/Library/Caches/ms-playwright/`
- RestTemplate for HTTP calls to VapeIt at localhost:8080
- Maven build, no database

## Key Files
- `VapeItReorderApplication.java` — entry point, @EnableScheduling
- `config/RestTemplateConfig.java` — RestTemplate bean
- `dto/ItemDto.java` — POJO with `needsReorder()`; fields: id, sku, nombre, minimoUnidades, maximoUnidades, currentStock, supplierUrl, distribuidor, urlProducto, cantidadAPedir
- `dto/CartItemDto.java` — sku, nombre, cantidad, precioUnitario (scraped cart line)
- `dto/CartResultDto.java` — distribuidor, timestamp, status, carrito, errores
- `service/ItemApiClient.java` — GET /api/items from VapeIt
- `service/ReorderScheduler.java` — cron 14:30/21:30, uses BotEngine + OrderReporter
- `service/BotEngine.java` — groups items by distributor, opens browser per group, infers distributor from supplierUrl domain if distribuidor field is null
- `service/OrderReporter.java` — POSTs CartResultDto list to vapeit.report-url
- `distributor/DistributorBot.java` — interface: getDistributorName(), run(Page, List<ItemDto>)
- `distributor/EciglogisticaBot.java` — structure ready; login/addToCart/scrapeCart selectors are TODOs
- `distributor/VaperaliaBot.java` — stub, throws UnsupportedOperationException
- `controller/TriggerController.java` — POST /trigger fires checkAndReorder()
- `application.yml` — server.port 8081, cron, eciglogistica+vaperalia creds (blank), vapeit urls

## Current State
- Project compiles and runs (`mvn spring-boot:run`)
- Chromium **is installed** at `/Users/daniel/Library/Caches/ms-playwright/chromium-1148`
- Bot navigates to eciglogistica URL but login/addToCart/scrapeCart selectors are all **TODOs**
- Credentials for eciglogistica and vaperalia are blank in application.yml
- cron is set to `"0 * * * * *"` (every minute) for development — change to `"0 30 14,21 * * *"` for production

## Next Step
Implement `EciglogisticaBot` selectors:
1. Set `playwright.headless: false` in application.yml
2. `curl -X POST http://localhost:8081/trigger` to open the browser
3. Inspect the DOM of https://nueva.eciglogistica.com with DevTools
4. Fill in selectors in `login()`, `addToCart()`, `scrapeCart()` in `EciglogisticaBot.java`

## Docs
- `README.md` — setup, comandos, estado actual, qué necesita el backend
- `plan.md` — arquitectura, flujo, componentes, próximos pasos
- `playwright.md` — explicación de cómo funciona Playwright (qué es, scraping, waitForLoadState, DOM, cómo se implementan los selectores manualmente)

## Running
```bash
mvn spring-boot:run                          # run the service
curl -X POST http://localhost:8081/trigger   # manual trigger
```

## Sibling Project
VapeIt main app at `/Users/daniel/code/VapeIt` — provides REST API. Item entity extends Referencia (@MappedSuperclass). Fields `currentStock`, `supplierUrl`, `distribuidor`, `urlProducto`, `cantidadAPedir` need to be added/confirmed in VapeIt's Item entity for full integration.

## Coding Conventions
- No Lombok, no records — plain POJOs with explicit getters/setters
- Constructor injection only, @Value in constructor params
- SLF4J logging with {} placeholders
- Spanish domain names, English code
- var for local type inference, Stream API for filtering
- try-with-resources for Closeable, per-item error handling in loops
- No javadoc/comments unless necessary
