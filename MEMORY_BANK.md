# VapeItReorder - Memory Bank

## Project Overview
Aplicación Spring Boot para consultar SKUs bajo mínimos en VapeIt (`GET /api/items`), resolver URLs de producto por distribuidora desde PostgreSQL y hacer scraping de precio con Playwright en Vaperalia y Eciglogistica.

## Current State
- Stack activo en `src/`: Spring Boot + Spring Data JPA + PostgreSQL + Playwright
- Paquete base: `org.ppoole.vapeitreorder.playtest.app`
- Ejecución one-shot: arranca, corre el `ApplicationRunner`, y cierra contexto (`context.close()` en `main`)
- Puerto configurado: `8081`
- Sin endpoints REST ni scheduler
- Modos CLI:
  - `--login`: guarda sesión de Vaperalia
  - `--login-ecig`: guarda sesión de Eciglogistica
- Flujo principal implementado: API VapeIt -> filtro de SKUs -> query de URLs en BD -> scraping por distribuidora
- Resultado actual del runner: scrapea y guarda en listas locales (`vaperaliaResults`, `ecigResults`) pero no persiste ni expone salida final

## Project Structure
```
src/main/java/org/ppoole/vapeitreorder/playtest/app/
├── PlaytestApp.java
├── config/
│   └── RestTemplateConfig.java
├── domain/
│   ├── Distribuidora.java
│   ├── Producto.java
│   ├── ProductoDistribuidora.java
│   └── ProductoRespuesta.java
├── dto/
│   └── ItemDto.java
├── repository/
│   ├── DistribuidoraRepository.java
│   ├── ProductoRepository.java
│   └── ProductoDistribuidoraRepository.java
├── service/
│   └── ItemApiClient.java
├── vaperalia/
│   └── VaperaliaPlaytestService.java
└── eciglogistica/
    └── EciglogisticaPlaytestService.java
```

## Actual Runtime Flow (`PlaytestApp` runner)
1. Si primer argumento es `--login`, ejecuta `VaperaliaPlaytestService.saveSession()` y termina.
2. Si primer argumento es `--login-ecig`, ejecuta `EciglogisticaPlaytestService.saveSession()` y termina.
3. `ItemApiClient.fetchSkusNeedingReorder()` llama `GET {vapeit.api-url}/api/items` y filtra `unidadesActuales < minimoUnidades`.
4. Si no hay SKUs, loggea y termina.
5. `ProductoDistribuidoraRepository.findSkuUrlDistribuidoraTriosBySkuIn(skus)` devuelve `sku`, `url`, `distribuidoraName`, `variante`.
6. Filtra por distribuidora con `equalsIgnoreCase("VAPERALIA")` y `equalsIgnoreCase("ECIGLOGISTICA")`.
7. Ejecuta scraping por servicio y acumula resultados en listas.

## API VapeIt
- Endpoint esperado: `GET http://localhost:8080/api/items` (por defecto)
- Configurable con `vapeit.api-url` (env: `VAPEIT_API_URL`)
- DTO usado: `ItemDto` (`sku`, `unidadesActuales`, `minimoUnidades`, `maximoUnidades`, `needsReorder()`)

## Database
- PostgreSQL 17 en Docker (`docker-compose.yml`)
- Puerto: `5433` host -> `5432` container
- DB/user/pass: `vapeit_reorder` / `vapeit` / `vapeit`
- Volumen: `./data/postgres-vapeit`
- Hibernate: `ddl-auto: update`
- Entidades/tablas: `DISTRIBUIDORA`, `PRODUCTO`, `PRODUCTO_DISTRIBUIDORA`
- Constraint único en `PRODUCTO_DISTRIBUIDORA`: `(SKU, ID_DISTRIBUIDORA, VARIANTE)`

## Scraping Details
### Vaperalia
- Fichero de sesión: `vaperalia-session.json` (ruta relativa al directorio de ejecución)
- Validación de sesión: navegar a `https://vaperalia.es/mi-cuenta` y verificar que no redirige a `autenticacion`
- Selectores: nombre `h1[itemprop='name']`, precio `#our_price_display`
- Parseo precio: elimina `€`, convierte `,` a `.` y parsea a `Double`

### Eciglogistica
- Fichero de sesión: `eciglogistica-session.json` (ruta relativa al directorio de ejecución)
- Validación de sesión: navegar a `https://nueva.eciglogistica.com/perfil/basico` y verificar que no redirige a `entrar`
- Selectores: nombre `h1`, precio `h6.product-price`
- Soporte variantes: si `variante != null`, selecciona label en `select.select-attribute-product` y espera 1500ms
- Parseo precio: elimina `€` y parsea a `Double` directamente

## Notas Técnicas Relevantes
- `ProductoRespuesta` existe como POJO de salida, pero sus accesores de distribuidora están nombrados como `getString()`/`setString()` en vez de `getDistribuidora()`/`setDistribuidora()`.
- `ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio` es proyección por interfaz (no DTO clase).
- Se usa `RestTemplate` por bean dedicado (`RestTemplateConfig`).

## Running
```bash
docker compose up -d

# Guardar sesión Vaperalia
mvn spring-boot:run -Dspring-boot.run.arguments="--login"

# Guardar sesión Eciglogistica
mvn spring-boot:run -Dspring-boot.run.arguments="--login-ecig"

# Ejecutar flujo
mvn spring-boot:run
```

## Pending Work (según código actual)
1. Orquestador de comparación final (tipo `PriceComparator`)
2. Exponer resultados por endpoint REST
3. Programación recurrente (scheduler/cron)
