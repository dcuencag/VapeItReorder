# Plan: Microservicio Playwright - Automatización de Compras

## Contexto

Proyecto de automatización de stock y pedidos para tiendas de vapeo (Vape It).
Equipo: Sonri (backend Spring/Java), Mechanics (negocio/distribuidoras), Daniel (automatización de pedidos).

El sistema principal (Sonri) gestiona el stock y detecta cuándo un producto baja del mínimo.
Este microservicio se encarga de **dos veces al día (14:30 y 21:30) hacer una petición al backend de Sonri, obtener una lista de productos que estén bajo el mínimo aceptable, ir a las webs de los distribuidores y añadir al carrito automáticamente los productos hasta que lleguen al máximo aceptable**. El humano revisa y confirma la compra.

---

## Arquitectura

```
[Spring Backend + DB]  ──HTTP──>  [Playwright Service]  ──Browser──>  [Web Distribuidor]
      (Sonri)                        (Daniel)                     (Vaperalia, Eciglogistica, etc.)
```

- **Spring Backend**: Java, gestiona stock, productos, distribuidores, base de datos SQL
- **Playwright Service**: Microservicio Spring Boot + Playwright Java (mismo stack que Sonri para simplificar)
- **Comunicación**: HTTP REST entre ambos servicios

---

## Flujo de ejecución

### Triggers: 14:30 y 21:30 (cron)

1. **Cron dispara** el servicio a las horas programadas
2. **GET al backend de Sonri** → recibe lista de productos con stock < mínimo:
   ```json
   [
     {
       "sku": "123",
       "nombre": "IVG Iced Melonade 10mg",
       "cantidadAPedir": 10,
       "distribuidor": "vaperalia",
       "urlProducto": "https://vaperalia.com/producto/123"
     }
   ]
   ```
3. **Agrupa por distribuidor** (un login por distribuidor, eficiente)
4. **Por cada distribuidor**:
   - Abre navegador con Playwright (headless)
   - Login con credenciales de la tienda cliente
   - Por cada producto: navega a URL → selecciona cantidad → añade al carrito
   - **Al terminar todos los productos**: navega al carrito y hace scraping
   - Extrae del HTML del carrito: productos, cantidades, precios
   - Devuelve JSON estructurado con el contenido real del carrito
5. **POST al backend** → reporta resultado:
   ```json
   {
     "distribuidor": "vaperalia",
     "timestamp": "2026-02-18T14:30:00Z",
     "status": "ok",
     "carrito": [
       { "sku": "123", "nombre": "Hugo Boss 50ml", "cantidad": 10, "precioUnitario": 8.50 }
     ],
     "errores": []
   }
   ```
6. **El humano** entra a la web del distribuidor, ve el carrito preparado y confirma la compra

También disponible: **POST /trigger** para disparar manualmente sin esperar al cron.

---

## Componentes implementados

| Componente | Clase | Estado |
|---|---|---|
| Cron/Scheduler | `ReorderScheduler` | ✅ Implementado (14:30 y 21:30) |
| API Client | `ItemApiClient` | ✅ Implementado |
| Bot Engine | `BotEngine` | ✅ Implementado (agrupa por distribuidor, un browser por grupo) |
| Cart Scraper | dentro de cada `DistributorBot` | ⏳ TODOs de selectores |
| Reporter | `OrderReporter` | ✅ Implementado (POST al backend) |
| Trigger manual | `TriggerController` | ✅ POST /trigger |
| EciglogisticaBot | `EciglogisticaBot` | ⏳ Estructura lista, selectores pendientes |
| VaperaliaBot | `VaperaliaBot` | 🔲 Stub |

---

## Lo que Daniel necesita de Sonri (endpoints)

1. `GET /api/items` → Lista de todos los productos con `currentStock`, `minimoUnidades`, `maximoUnidades`, `distribuidor`, `urlProducto`, `cantidadAPedir` (este último opcional; si null, el bot calcula `maximoUnidades - currentStock`)
2. `POST /api/orders/status` → Recibe el resultado del bot (qué se añadió al carrito, qué falló)

### Formato del ítem que devuelve el backend

```json
{
  "id": 1,
  "sku": "IVG-001",
  "nombre": "IVG Iced Melonade 10mg",
  "currentStock": 3,
  "minimoUnidades": 10,
  "maximoUnidades": 50,
  "distribuidor": "eciglogistica",
  "urlProducto": "https://nueva.eciglogistica.com/producto/ivg-001",
  "cantidadAPedir": null
}
```

Si `distribuidor` es null, el bot intenta inferirlo del dominio de `urlProducto`.

---

## Stack técnico

- **Runtime**: Java 21 / Spring Boot 3.5.0
- **Playwright Java** 1.49.0: automatización de navegador
- **Spring Scheduler**: cron integrado en Spring (`@Scheduled`)
- **spring-boot-starter-web**: expone POST /trigger
- **RestTemplate**: llamadas HTTP al backend Sonri
- **Sin base de datos**: los datos vienen del backend de Sonri

---

## Estructura de archivos

```
src/main/java/org/ppoole/vapeitreorder/
├── VapeItReorderApplication.java
├── config/RestTemplateConfig.java
├── controller/TriggerController.java       # POST /trigger
├── dto/
│   ├── ItemDto.java                        # Producto + needsReorder()
│   ├── CartItemDto.java                    # Línea del carrito scrapeado
│   └── CartResultDto.java                  # Resultado por distribuidor
├── service/
│   ├── ItemApiClient.java                  # GET /api/items
│   ├── BotEngine.java                      # Agrupa por distribuidor, lanza bots
│   ├── OrderReporter.java                  # POST /api/orders/status
│   └── ReorderScheduler.java              # Cron + coordinación
└── distributor/
    ├── DistributorBot.java                 # Interfaz
    ├── EciglogisticaBot.java               # ⏳ Selectores pendientes de DOM
    └── VaperaliaBot.java                   # 🔲 Stub
```

---

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Cada web de distribuidor es diferente | Un `DistributorBot` por distribuidor en `distributor/` |
| Si la web cambia el HTML, el bot se rompe | El scraping del carrito detecta discrepancias → errores en `CartResultDto.errores` |
| Captchas o protección anti-bot | Empezar por las webs más simples; `playwright.headless: false` si hace falta |
| Credenciales expuestas | Variables en `application.yml` (fuera del repo en producción) |
| Sesiones que expiran | Login fresco en cada ejecución (solo 2 veces al día) |
| Un distribuidor falla | `BotEngine` continúa con los demás; error queda en `CartResultDto` |

---

## Orden de implementación — estado actual

- [x] Setup Spring Boot + Playwright
- [x] Cron básico (14:30 y 21:30)
- [x] API Client: conectar con backend de Sonri
- [x] DTOs: `ItemDto`, `CartItemDto`, `CartResultDto`
- [x] Interfaz `DistributorBot` + estructura multi-distribuidor
- [x] `BotEngine`: agrupación por distribuidor, browser por grupo
- [x] `OrderReporter`: POST de resultados al backend
- [x] `TriggerController`: POST /trigger para disparo manual
- [ ] **EciglogisticaBot**: inspeccionar DOM de eciglogistica e implementar selectores de login, añadir al carrito y scraping
- [ ] **VaperaliaBot**: implementar (después de eciglogistica)
- [ ] Gestión de errores avanzada / reintentos (si necesario)

---

## Próximos pasos

1. **Inspeccionar el DOM de `https://nueva.eciglogistica.com`** con `playwright.headless: false` y rellenar los selectores en `EciglogisticaBot.java` (login, add-to-cart, scrape carrito)
2. **Pedir a Sonri** que incluya `distribuidor`, `urlProducto` y `cantidadAPedir` en `GET /api/items`
3. **Decidir primer MVP**: ¿empezamos con eciglogistica o vaperalia?
4. Añadir `server.port: 8081` en `application.yml` para evitar conflicto de puertos con el backend VapeIt si corren en la misma máquina
