# VapeItReorder - Memory Bank

## Project Overview
Spring Boot microservice que automatiza el rellenado del carrito en webs de distribuidores cuando el stock baja del mínimo. Corre en el **puerto 8081**. El backend VapeIt corre en el 8080.

## Current State
- `src/` fue borrado — el código Spring Boot ya no existe en el árbol de trabajo
- El código anterior está guardado en `old/src/` como referencia
- `playtest/` es un proyecto Maven standalone (sin Spring) para inspeccionar DOM de distribuidores
- El siguiente paso es reconstruir `src/` usando `old/src/` como base y los selectores descubiertos

## Playtest Project (`playtest/`)
Proyecto Java standalone (no Spring Boot) con Playwright para inspeccionar webs de distribuidores.

### VaperaliaPlaytest.java
- `--login` mode: abre Chromium visible, espera login manual, guarda sesión en `vaperalia-session.json`
- Default mode: scraping headless usando sesión guardada
- Archivos: `VaperaliaPlaytest.java`, `pom.xml`, `debug-output.html` (HTML capturado)
- **`vaperalia-session.json` está en `.gitignore`** — contiene cookies reales

### Vaperalia DOM (descubierto)
Vaperalia es una tienda **PrestaShop** (v6.6.7). Selectores y variables JS confirmados:
- Nombre del producto: `h1[itemprop='name']` → `.textContent().trim()`
- Precio (con impuesto especial): `window.productPrice` — JS global, **no está en el DOM**
- Precio sin impuestos: `window.productBasePriceTaxExcl`
- Disponible para comprar: `window.productAvailableForOrder` (boolean)
- Stock disponible: `window.quantityAvailable`
- Descuento de grupo aplicado: `window.group_reduction` (ej: `-0.15` = 15% descuento cuando logueado)
- `window.productShowPrice = false` cuando no está logueado (precio en JS igual existe)
- Login URL: `https://vaperalia.es/autenticacion?back=my-account`
- Tras login, redirige a `**/mi-cuenta**`
- Sesión Playwright: `context.storageState(path)` para guardar, `Browser.NewContextOptions().setStorageStatePath()` para cargar

## Old Code Reference (`old/`)
Contiene el código Spring Boot anterior completo:
- `old/src/` — todo el código Java
- `old/plan.md`, `old/playwright.md`, `old/plan-compare.md` — documentación anterior

## Arquitectura Anterior (referencia para reconstruir)
1. Cron fires at 14:30 and 21:30 → `ReorderScheduler` calls VapeIt API (`GET /api/items`)
2. Filters items where `currentStock < minimoUnidades`
3. `BotEngine` groups items by `distribuidor`, opens one Chromium browser per distributor
4. Each `DistributorBot` logs in, adds items to cart, scrapes cart → returns `CartResultDto`
5. `OrderReporter` POSTs each `CartResultDto` to `vapeit.report-url`
6. `POST /trigger` on port 8081 fires a manual run
7. `POST /compare` y `POST /order` para comparación de precios

## Tech Stack
- Java 21, Spring Boot 3.5.0 + spring-boot-starter-web (embedded Tomcat, port 8081)
- Playwright Java 1.49.0 — Chromium installed at `/Users/daniel/Library/Caches/ms-playwright/chromium-1148`
- RestTemplate for HTTP calls to VapeIt at localhost:8080
- Maven build, no database

## Key DTOs (referencia)
- `ItemDto` — id, sku, nombre, minimoUnidades, maximoUnidades, currentStock, supplierUrl, distribuidor, urlProducto, cantidadAPedir; método `needsReorder()`
- `CartItemDto` — sku, nombre, cantidad, precioUnitario (línea de carrito scrapeada)
- `CartResultDto` — distribuidor, timestamp, status, carrito, errores
- `PriceOptionDto` — distribuidor, nombre, precio, urlProducto, disponible
- `PriceComparisonDto` — sku, nombre, cantidadAPedir, List<PriceOptionDto> opciones
- `OrderSelectionDto` — sku, distribuidor, cantidadAPedir

## Coding Conventions
- No Lombok, no records — plain POJOs with explicit getters/setters
- Constructor injection only, @Value in constructor params
- SLF4J logging with {} placeholders
- Spanish domain names, English code
- var for local type inference, Stream API for filtering
- try-with-resources for Closeable, per-item error handling in loops
- No javadoc/comments unless necessary

## Running
```bash
# Playtest (desde playtest/)
mvn compile exec:java -Dexec.mainClass=VaperaliaPlaytest -Dexec.args="--login"  # guardar sesión
mvn compile exec:java -Dexec.mainClass=VaperaliaPlaytest                         # scraping

# Main app (cuando se reconstruya src/)
mvn spring-boot:run
curl -X POST http://localhost:8081/trigger
```

## Sibling Project
VapeIt main app at `/Users/daniel/code/VapeIt` — provides REST API at localhost:8080.
