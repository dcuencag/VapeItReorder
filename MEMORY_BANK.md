# VapeItReorder - Memory Bank

## Project Overview
Spring Boot app para comparación de precios entre distribuidores de productos de vapeo. Consulta qué productos están bajo mínimos en VapeIt (sibling project en localhost:8080), busca las URLs de cada distribuidor en su base de datos local (PostgreSQL), y con Playwright hace scraping del precio en cada web.

## Current State
- `src/` es una app Spring Boot con JPA + PostgreSQL + Playwright
- Paquete: `org.ppoole.vapeitreorder.playtest.app`
- **Flujo funcional**: Pedro (API) → filtrar SKUs bajo mínimos → buscar URLs en BD → Playwright scrape → mostrar precios
- La app arranca, ejecuta el flujo y se cierra (`context.close()` en main)
- Puerto: **8081** (Tomcat, para futuros endpoints)
- **Sin scheduler, sin endpoints REST** — el flujo se ejecuta al arrancar via ApplicationRunner

## Project Structure
```
src/main/java/org/ppoole/vapeitreorder/playtest/app/
├── PlaytestApp.java                         # Entry point, ApplicationRunner que orquesta el flujo
├── config/
│   └── RestTemplateConfig.java              # Bean RestTemplate
├── domain/
│   ├── Distribuidora.java                   # Entity: id, name (unique)
│   ├── Producto.java                        # Entity: sku (PK), nombre
│   └── ProductoDistribuidora.java           # Entity: id, producto (FK), distribuidora (FK), url
├── dto/
│   └── ItemDto.java                         # DTO API Pedro: sku, unidadesActuales, minimoUnidades, maximoUnidades, needsReorder()
├── repository/
│   ├── DistribuidoraRepository.java         # findByName()
│   ├── ProductoRepository.java              # standard CRUD
│   └── ProductoDistribuidoraRepository.java # findByProductoSku(), findSkuUrlPairsBySkuIn()
├── service/
│   └── ItemApiClient.java                   # GET /api/items → filtra needsReorder() → devuelve SKUs
└── vaperalia/
    └── VaperaliaPlaytestService.java        # --login / scrape modes con Playwright
```

## Flujo Actual (ApplicationRunner en PlaytestApp)
1. `ItemApiClient.fetchSkusNeedingReorder()` → llama a Pedro `GET /api/items`, filtra `unidadesActuales < minimoUnidades`, devuelve SKUs
2. `ProductoDistribuidoraRepository.findSkuUrlPairsBySkuIn(skus)` → busca URLs en BD local
3. `VaperaliaPlaytestService.scrape(urls)` → valida sesión, navega a cada URL, extrae nombre y precio

## API de Pedro (VapeIt)
- Endpoint: `GET http://localhost:8080/api/items`
- Campos relevantes: `sku`, `unidadesActuales`, `minimoUnidades`, `maximoUnidades`
- Config: `vapeit.api-url` en application.yml (default `http://localhost:8080`)

## Database
- **PostgreSQL 17** via Docker Compose en puerto **5433** (host) → 5432 (container)
- DB: `vapeit_reorder`, user: `vapeit`, password: `vapeit`
- Volumen: `./data/postgres-vapeit` (en .gitignore, carpeta `data/` sin punto)
- Hibernate ddl-auto: `update` (crea tablas automáticamente)
- Tablas: `DISTRIBUIDORA`, `PRODUCTO`, `PRODUCTO_DISTRIBUIDORA`
- Datos de ejemplo insertados: distribuidora "vaperalia" + 3 productos con URLs

## Tech Stack
- Java 21, Spring Boot 3.5.0 (starter-web + starter-data-jpa)
- Playwright Java 1.49.0
- PostgreSQL driver (runtime)
- Jackson (databind + jsr310)
- Maven build

## Vaperalia Scraping
- Sesión Playwright guardada en `vaperalia-session.json` (raíz del proyecto, en .gitignore)
- **Validación de sesión**: antes de scrapear, navega a `/mi-cuenta` y comprueba que no redirige a `autenticacion`
- Si no hay fichero de sesión o la sesión ha caducado → error claro y para
- Selectores: nombre `h1[itemprop='name']`, precio `#our_price_display` (elemento DOM, se parsea quitando `€` y convirtiendo `,` a `.`)
- **NO usar `window.productPrice`** — es una variable JS con race condition, a veces no está lista cuando Playwright la lee. El elemento DOM `#our_price_display` siempre tiene el precio renderizado

## Plan Pendiente
1. ~~Docker + PostgreSQL~~ HECHO
2. ~~Entidades JPA + repositorios~~ HECHO
3. ~~ItemApiClient (Pedro → SKUs)~~ HECHO
4. ~~Flujo completo Pedro → BD → Playwright~~ HECHO
5. Integrar `EciglogisticaBot` con `searchProduct()`
6. `PriceComparator` orquestador multi-distribuidor
7. Endpoints REST
8. Scheduler (cron)

## Código Legacy
- `old/` contiene el código Spring Boot anterior (multi-distributor bots, cart scraping, order reporting)
- `playtest/` contiene el playtest original standalone (sin Spring), ahora migrado a `src/`

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
# Levantar PostgreSQL
docker compose up -d

# Guardar sesión de Vaperalia (primera vez o cuando caduque)
mvn spring-boot:run -Dspring-boot.run.arguments="--login"

# Ejecutar flujo completo
mvn spring-boot:run
```

## Sibling Project
VapeIt main app at `/Users/daniel/code/VapeIt` — provides REST API at localhost:8080.
