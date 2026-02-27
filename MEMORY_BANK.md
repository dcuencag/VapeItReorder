# VapeItReorder - Memory Bank

## Project Overview
Spring Boot app para comparación de precios entre distribuidores de productos de vapeo. Consulta qué productos están bajo mínimos en VapeIt (sibling project en localhost:8080), busca las URLs de cada distribuidor en su base de datos local (PostgreSQL), y con Playwright hace scraping del precio en cada web.

## Current State
- `src/` es una app Spring Boot con JPA + PostgreSQL + Playwright
- Paquete: `org.ppoole.vapeitreorder.playtest.app`
- **Flujo funcional**: VapeIt (API) → filtrar SKUs bajo mínimos → buscar URLs en BD → Playwright scrape → mostrar precios
- La app arranca, ejecuta el flujo y se cierra (`context.close()` en main)
- Puerto: **8081** (Tomcat, para futuros endpoints)
- **Sin scheduler, sin endpoints REST** — el flujo se ejecuta al arrancar via ApplicationRunner
- **Dos distribuidores funcionales**: Vaperalia y Eciglogistica

## Project Structure
```
src/main/java/org/ppoole/vapeitreorder/playtest/app/
├── PlaytestApp.java                         # Entry point, ApplicationRunner que orquesta el flujo
├── config/
│   └── RestTemplateConfig.java              # Bean RestTemplate
├── domain/
│   ├── Distribuidora.java                   # Entity: id, name (unique)
│   ├── Producto.java                        # Entity: sku (PK), nombre
│   ├── ProductoDistribuidora.java           # Entity: id, producto (FK), distribuidora (FK), url, variante (nullable)
│   └── ProductoRespuesta.java               # POJO: sku, nombre, precio, url, distribuidora
├── dto/
│   └── ItemDto.java                         # DTO API VapeIt: sku, unidadesActuales, minimoUnidades, maximoUnidades, needsReorder()
├── repository/
│   ├── DistribuidoraRepository.java         # findByName()
│   ├── ProductoRepository.java              # standard CRUD
│   └── ProductoDistribuidoraRepository.java # findSkuUrlDistribuidoraTriosBySkuIn() — proyección (sku, url, distribuidoraName, variante)
├── service/
│   └── ItemApiClient.java                   # GET /api/items → filtra needsReorder() → devuelve SKUs
├── vaperalia/
│   └── VaperaliaPlaytestService.java        # --login / scrape modes con Playwright
└── eciglogistica/
    └── EciglogisticaPlaytestService.java    # --login-ecig / scrape modes con Playwright
```

## Flujo Actual (ApplicationRunner en PlaytestApp)
1. `ItemApiClient.fetchSkusNeedingReorder()` → llama a VapeIt `GET /api/items`, filtra `unidadesActuales < minimoUnidades`, devuelve SKUs
2. `ProductoDistribuidoraRepository.findSkuUrlDistribuidoraTriosBySkuIn(skus)` → busca URLs en BD local (incluye variante)
3. URLs se agrupan por distribuidora
4. `VaperaliaPlaytestService.scrape(urls)` → valida sesión, navega a cada URL, extrae nombre y precio
5. `EciglogisticaPlaytestService.scrape(urls)` → valida sesión, si hay variante selecciona en dropdown, extrae nombre y precio

## API de VapeIt
- Endpoint: `GET http://localhost:8080/api/items`
- Campos relevantes: `sku`, `unidadesActuales`, `minimoUnidades`, `maximoUnidades`
- Config: `vapeit.api-url` en application.yml (default `http://localhost:8080`)

## Database
- **PostgreSQL 17** via Docker Compose en puerto **5433** (host) → 5432 (container)
- DB: `vapeit_reorder`, user: `vapeit`, password: `vapeit`
- Volumen: `./data/postgres-vapeit` (en .gitignore, carpeta `data/` sin punto)
- Hibernate ddl-auto: `update` (crea tablas automáticamente)
- Tablas: `DISTRIBUIDORA`, `PRODUCTO`, `PRODUCTO_DISTRIBUIDORA`
- Distribuidoras: `vaperalia`, `ECIGLOGISTICA`
- Unique constraint: `(SKU, ID_DISTRIBUIDORA, VARIANTE)` — permite mismo SKU+distribuidora con variantes distintas

## Variantes
- Columna `variante` en `producto_distribuidora` — nullable, solo se usa cuando un producto en el distribuidor tiene variantes seleccionables (ej. 10mg, 20mg)
- En Eciglogistica: algunos productos comparten URL con un `<select class="select-attribute-product">` para elegir variante. El scraper selecciona la opción por label y espera 1.5s para que AJAX actualice el precio
- En Vaperalia: cada variante tiene URL distinta, no se usa el campo variante
- **Decisión de diseño**: aunque sean variantes, en VapeIt se tratan como productos independientes con SKUs distintos (ej. `BLRSPBRRIVGSLTS10`, `BLRSPBRRIVGSLTS20`)

## Vaperalia Scraping
- Sesión Playwright guardada en `vaperalia-session.json` (raíz del proyecto, en .gitignore)
- **Validación de sesión**: antes de scrapear, navega a `/mi-cuenta` y comprueba que no redirige a `autenticacion`
- Si no hay fichero de sesión o la sesión ha caducado → error claro y para
- Selectores: nombre `h1[itemprop='name']`, precio `#our_price_display` (elemento DOM, se parsea quitando `€` y convirtiendo `,` a `.`)
- **NO usar `window.productPrice`** — es una variable JS con race condition, a veces no está lista cuando Playwright la lee

## Eciglogistica Scraping
- Sesión Playwright guardada en `eciglogistica-session.json` (raíz del proyecto, en .gitignore)
- **Validación de sesión**: navega a `/perfil/basico` y comprueba que no redirige a `entrar`
- Login: `--login-ecig` abre navegador visible en `https://nueva.eciglogistica.com/entrar`
- Selectores: nombre `h1`, precio `h6.product-price` (se parsea quitando `€`)
- Soporte de variantes: si `variante != null`, selecciona en `select.select-attribute-product` por label y espera 1.5s

## Plan Pendiente
1. ~~Docker + PostgreSQL~~ HECHO
2. ~~Entidades JPA + repositorios~~ HECHO
3. ~~ItemApiClient (VapeIt → SKUs)~~ HECHO
4. ~~Flujo completo VapeIt → BD → Playwright~~ HECHO
5. ~~Eciglogistica scraper~~ HECHO
6. ~~Soporte de variantes en Eciglogistica~~ HECHO
7. `PriceComparator` orquestador multi-distribuidor
8. Endpoints REST
9. Scheduler (cron)

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

# Guardar sesión de Eciglogistica (primera vez o cuando caduque)
mvn spring-boot:run -Dspring-boot.run.arguments="--login-ecig"

# Ejecutar flujo completo
mvn spring-boot:run
```

## Sibling Project
VapeIt main app at `/Users/daniel/code/VapeIt` — provides REST API at localhost:8080.
