# VapeItReorder

App Spring Boot que compara precios de productos de vapeo entre distribuidores. Consulta el inventario de VapeIt, busca las URLs de cada distribuidor en su base de datos local (PostgreSQL) y con Playwright hace scraping del precio en cada web.

## Requisitos

- Java 21
- Docker (para PostgreSQL)
- Chromium de Playwright instalado:

```bash
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

## Setup

**1. Levantar PostgreSQL**

```bash
docker compose up -d
```

PostgreSQL queda en `localhost:5433`, base de datos `vapeit_reorder`. Las tablas se crean automáticamente (Hibernate ddl-auto: update).

**2. Guardar sesión de Vaperalia (primera vez o cuando expire)**

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--login"
```

Abre un navegador visible. Inicia sesión manualmente en Vaperalia y espera — la sesión se guarda automáticamente en `vaperalia-session.json`.

**3. Ejecutar**

```bash
mvn spring-boot:run
```

## Notas

- `vaperalia-session.json` contiene cookies reales — está en `.gitignore`
- Si el scraping redirige a la página de login, ejecuta el paso 2 de nuevo
- VapeIt (proyecto hermano) debe estar corriendo en `localhost:8080`
- El precio se extrae del elemento DOM `#our_price_display`, no de variables JS (que tienen race conditions)
