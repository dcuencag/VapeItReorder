# Plan de Implementación: Sistema de Comparación de Precios

## Visión General

El servicio consulta a Pedro qué productos están por debajo de mínimos, busca en una base de datos local las URLs de cada distribuidor para esos SKUs, y con Playwright obtiene el precio en cada distribuidor. El resultado es una lista ordenada de más barato a más caro por producto.

```
API Pedro → SKUs bajo mínimos → DB (sku → URLs por distribuidor)
    → Playwright scraping → precios por distribuidor → ordenado
```

---

## Fase 1: Base de datos (EMPEZAMOS AQUÍ)

### Infraestructura
- **PostgreSQL en Docker** con un volumen local para persistencia de datos
- `docker-compose.yml` en la raíz del proyecto
- Volumen montado en `./data/postgres` para que los datos sobrevivan al contenedor

### Esquema

```sql
-- Distribuidoras conocidas
CREATE TABLE distributors (
    id   SERIAL PRIMARY KEY,
    name VARCHAR NOT NULL UNIQUE  -- 'vaperalia', 'eciglogistica'
);

-- Productos (SKUs del catálogo de Pedro)
CREATE TABLE products (
    sku    VARCHAR PRIMARY KEY,
    nombre VARCHAR
);

-- URLs de cada producto en cada distribuidor
CREATE TABLE product_urls (
    id             SERIAL PRIMARY KEY,
    sku            VARCHAR NOT NULL REFERENCES products(sku),
    distributor_id INT     NOT NULL REFERENCES distributors(id),
    url            VARCHAR NOT NULL,
    UNIQUE (sku, distributor_id)
);
```

### Datos iniciales (manual)
- Insertar `vaperalia` y `eciglogistica` en `distributors`
- Insertar SKUs y sus URLs en `products` y `product_urls` a mano al principio

---

## Fase 2: Integración en playtest

- Añadir driver JDBC de PostgreSQL al `pom.xml` de playtest
- Clase `DatabaseClient` que abre conexión y expone:
  - `getUrlsForSku(sku)` → `Map<String, String>` (distribuidor → url)
  - `getUrlsForSkus(List<String>)` → `Map<String, Map<String, String>>`
- Sin ORM, JDBC plano para mantenerlo simple

---

## Fase 3: Flujo de comparación de precios

### Nuevas clases en playtest

```
src/
├── ApiClient.java              # Llama a Pedro: GET /api/items → filtra needsReorder()
├── PriceComparator.java        # Orquesta todo el flujo
├── dto/
│   └── PriceResultDto.java     # { sku, nombre, opciones: [{ distribuidor, precio, url }] }
└── distributor/
    ├── VaperaliaBot.java        # Ya existe, searchProduct()
    └── EciglogisticaBot.java    # Nuevo, misma interfaz
```

### Flujo en `PriceComparator`

1. `ApiClient` llama a Pedro → lista de `ItemDto` con `needsReorder() == true`
2. Extrae los SKUs → consulta `DatabaseClient.getUrlsForSkus(skus)`
3. Para cada SKU, lanza `searchProduct(page, item)` en cada distribuidor que tenga URL
4. Agrega resultados en `List<PriceOptionDto>`, ordena por precio ascendente
5. Devuelve `List<PriceResultDto>` — un elemento por SKU

### Resultado ejemplo

```json
[
  {
    "sku": "ABC123",
    "nombre": "Don Juan Supra Reserve 10ml",
    "opciones": [
      { "distribuidor": "vaperalia",      "precio": 3.20, "url": "https://..." },
      { "distribuidor": "eciglogistica",  "precio": 3.75, "url": "https://..." }
    ]
  }
]
```

---

## Fase 4: Integración en VapeItReorder (producción)

- Añadir el mismo `DatabaseClient` al módulo principal
- `ReorderScheduler` usa `PriceComparator` para elegir la distribuidora más barata por SKU
- `PlaywrightOrderService` recibe la URL ganadora y hace el pedido

---

## Orden de trabajo

| # | Tarea | Estado |
|---|-------|--------|
| 1 | `docker-compose.yml` + volumen local | pendiente |
| 2 | Script SQL de esquema e inicialización | pendiente |
| 3 | `DatabaseClient.java` en playtest | pendiente |
| 4 | `EciglogisticaBot.java` con `searchProduct()` | pendiente |
| 5 | `ApiClient.java` (llamada a Pedro) | pendiente |
| 6 | `PriceComparator.java` orquestador | pendiente |
| 7 | Integrar en VapeItReorder prod | pendiente |
