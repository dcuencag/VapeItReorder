# VapeItReorder - Memory Bank

## Project Goal
Servicio de reorder para VapeIt que:
1. Consulta inventario en VapeIt (`/api/items`) para detectar SKUs bajo mínimo.
2. Busca en PostgreSQL URLs candidatas por SKU y distribuidora.
3. Hace scraping autenticado con Playwright en distribuidoras externas.
4. Devuelve, por SKU, una lista priorizada de URLs/distribuidoras (más baratas primero).

## Current State (March 2026)
- Arquitectura actual: **Spring Boot web app + JPA + PostgreSQL + Playwright + frontend estático**.
- Ya no es flujo one-shot por `ApplicationRunner`; ahora se ejecuta por endpoints REST.
- Hay UI en `src/main/resources/static/` para lanzar login de sesiones y ejecutar reorder.
- No hay persistencia del resultado final de scraping/prioridades (solo respuesta en memoria por request).
- No hay scheduler/cron.

## Runtime Contracts
### Backend port and config
- Puerto: `8081` (`server.port`).
- API VapeIt configurable por `vapeit.api-url` (default `http://localhost:8080`).
- Directorio de sesiones configurable por `vapeit.sessions-dir` (default `sessions`).
- PostgreSQL default: `jdbc:postgresql://localhost:5433/vapeit_reorder`.

### REST API
- `POST /api/reorder/session/vaperalia`
  - Abre navegador visible, login manual Vaperalia, guarda storage state.
- `POST /api/reorder/session/eciglogistica`
  - Abre navegador visible, login manual Eciglogistica, guarda storage state.
- `POST /api/reorder/run`
  - Ejecuta pipeline completo y devuelve `List<ProductoPrioridades>`.
- Manejo de error de sesión:
  - `PlaytestSessionException` -> HTTP `409` con `{ "message": "..." }`.

## End-to-End Flow (`ReorderExecutionService`)
1. `ItemApiClient.fetchSkusNeedingReorder()` llama `GET {vapeit.api-url}/api/items`.
2. Filtra ítems con `unidadesActuales < minimoUnidades` (`ItemDto.needsReorder()`).
3. Consulta BD con `findSkuUrlDistribuidoraTriosBySkuIn(skus)`.
4. Divide candidatos por distribuidora (`VAPERALIA`, `ECIGLOGISTICA`).
5. Scrapea cada grupo con su servicio Playwright autenticado.
6. Agrupa respuestas por SKU.
7. Ordena por precio ascendente (`nullsLast`).
8. Construye `ProductoPrioridades` con:
   - `sku`, `nombre` (primer nombre no vacío),
   - `urls` ordenadas por precio,
   - `distribuidoras` en paralelo al orden de URLs,
   - deduplicación por URL.

## Scraping Services
### Vaperalia (`VaperaliaPlaytestService`)
- Sesión: `${sessionsDir}/vaperalia-session.json`.
- Validación sesión: `https://vaperalia.es/mi-cuenta`; inválida si redirige a `autenticacion`.
- Scrape:
  - Nombre: `h1[itemprop='name']`
  - Precio: `#our_price_display`
  - Parseo: quita `€`, convierte `,` -> `.`
- Si no hay sesión o caduca: lanza `PlaytestSessionException`.

### Eciglogistica (`EciglogisticaPlaytestService`)
- Sesión: `${sessionsDir}/eciglogistica-session.json`.
- Validación sesión: `https://nueva.eciglogistica.com/perfil/basico`; inválida si redirige a `entrar`.
- Scrape:
  - Nombre: primer `h1`
  - Precio: `h6.product-price`
  - Soporta variante (`variante`) seleccionando `select.select-attribute-product`.
- Anti-bloqueo básico:
  - delay entre requests: 2500ms + jitter aleatorio hasta 1000ms.
- Si no hay sesión o caduca: lanza `PlaytestSessionException`.

## Data Model / DB
- Entidades JPA:
  - `Distribuidora` -> `DISTRIBUIDORA(ID, NAME unique)`
  - `Producto` -> `PRODUCTO(SKU PK, NOMBRE)`
  - `ProductoDistribuidora` -> `PRODUCTO_DISTRIBUIDORA`
    - FK a `PRODUCTO.SKU`
    - FK a `DISTRIBUIDORA.ID`
    - columnas: `URL`, `VARIANTE`
    - unique constraint: `(SKU, ID_DISTRIBUIDORA, VARIANTE)`
- Proyección usada en pipeline:
  - `SkuUrlDistribuidoraTrio { sku, url, distribuidoraName, variante }`

## Frontend (static)
- Archivos: `index.html`, `app.js`, `styles.css`.
- Acciones UI:
  - Cargar sesión Vaperalia.
  - Cargar sesión Eciglogistica.
  - Ejecutar reorder.
- Render:
  - Muestra estado textual,
  - cantidad de productos priorizados,
  - tarjetas por SKU con prioridades (`Priority 1..n`) y enlaces.

## Project Structure
```
src/main/java/org/ppoole/vapeitreorder/playtest/app/
├── PlaytestApp.java
├── config/RestTemplateConfig.java
├── controller/ReorderController.java
├── domain/
│   ├── Distribuidora.java
│   ├── Producto.java
│   ├── ProductoDistribuidora.java
│   ├── ProductoPrioridades.java
│   └── ProductoRespuesta.java
├── dto/ItemDto.java
├── eciglogistica/EciglogisticaFetchPriceService.java
├── repository/
│   ├── DistribuidoraRepository.java
│   ├── ProductoDistribuidoraRepository.java
│   └── ProductoRepository.java
├── service/
│   ├── ItemApiClient.java
│   ├── PlaytestSessionException.java
│   └── ReorderExecutionService.java
└── vaperalia/VaperaliaFetchPriceService.java

src/main/resources/
├── application.yml
└── static/
    ├── app.js
    ├── index.html
    └── styles.css
```

## Important Technical Notes
- `ProductoRespuesta` mantiene métodos redundantes/incorrectos heredados:
  - `getString()` / `setString()` además de `getDistribuidora()` / `setDistribuidora()`.
- Se usan `streams` en varias partes pese a guideline previa de preferir `for` explícito.
- Logging SQL está muy verboso (`org.hibernate.SQL=debug`, binds en `trace`).
- `spring.jpa.hibernate.ddl-auto=update` (útil dev, riesgo en entornos más controlados).

## Detected Mismatches / Risks
- **README desactualizado**:
  - describe flujo CLI antiguo (`--login`, ejecución one-shot) y no documenta API/UI REST actual.
- No pude verificar compilación local porque `mvn` no está disponible en este entorno (`mvn: command not found`).

## Operational Commands (intended)
```bash
# DB
docker compose up -d

# Run app (when Maven is available)
mvn spring-boot:run
```

## Suggested Next Work
1. Actualizar `README.md` a arquitectura REST + UI actual.
2. Añadir tests de integración para `/api/reorder/run` (mock API VapeIt + BD + servicios scrape).
3. Definir persistencia/auditoría de ejecuciones (histórico de precios/prioridades).
4. Evaluar scheduler y límites/reintentos por distribuidora.
