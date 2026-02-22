# Plan: Comparador de precios + VaperaliaBot

## Contexto

El sistema actual añade productos al carrito del distribuidor pre-asignado en VapeIt.
Este plan añade la capacidad de buscar el mismo producto en múltiples distribuidoras, recopilar
precios y enviarlos a VapeIt, donde Pedro implementará un sistema de pesos y toma de decisiones
automáticas (descuentos personales, precio individual, rapel, ofertas puntuales, preferencias
de cada tienda). VapeIt devuelve la decisión y este servicio añade al carrito automáticamente.
El único paso manual es la confirmación final de compra en la web del distribuidor.

---

## Flujo completo (automático)

```
Cron / POST /trigger
  ↓
1. GET /api/items (VapeIt) → filtra needsReorder()
  ↓
2. PriceComparisonService: por cada DistributorPriceBot
     abre browser → login → busca cada item → scrapes precio
     → List<PriceComparisonDto>
  ↓
3. POST /api/price-comparison (VapeIt)
     envía precios de todas las distribuidoras por item
     VapeIt aplica algoritmo de pesos y preferencias
     → devuelve List<ItemDto> con distribuidor elegido por item
  ↓
4. BotEngine.processOrders(itemsConDecision) [código existente]
     una sesión por distribuidora → login → add-to-cart → scrape carrito
     → List<CartResultDto>
  ↓
5. OrderReporter.report(results) [código existente]
     POST /api/orders/status (VapeIt)
  ↓
[Humano] Entra a la web del distribuidor y confirma la compra
```

---

## Lo que necesita VapeIt (Pedro)

Un endpoint nuevo en VapeIt:
```
POST /api/price-comparison
Request:  List<PriceComparisonDto>   (precios encontrados por item y distribuidora)
Response: List<ItemDto>               (mismos items, campo distribuidor rellenado por el algoritmo)
```
El algoritmo de VapeIt tiene en cuenta: precio actual, descuentos negociados, rapel,
historial, preferencias de la tienda, etc. Este servicio no implementa la lógica de decisión.

---

## Nuevos archivos

### `dto/PriceOptionDto.java`
```java
private String distribuidor;
private String nombre;       // nombre tal como aparece en la web
private Double precio;
private String urlProducto;
private boolean disponible;
```

### `dto/PriceComparisonDto.java`
```java
private String sku;
private String nombre;
private int cantidadAPedir;
private List<PriceOptionDto> opciones;  // una por cada distribuidora buscada
```

### `distributor/DistributorPriceBot.java` (nueva interfaz)
```java
public interface DistributorPriceBot {
    String getDistributorName();
    Optional<PriceOptionDto> searchProduct(Page page, ItemDto item);
}
```
Separada de `DistributorBot`: no todas las distribuidoras implementarán búsqueda,
y no todas las que busquen tendrán add-to-cart.

### `service/PriceComparisonService.java`
- Inyecta `List<DistributorPriceBot>` + `boolean headless`
- Método `compare(List<ItemDto> items) → List<PriceComparisonDto>`:
  - Por cada `DistributorPriceBot`: abre un browser Playwright (mismo patrón que `BotEngine`)
  - Login, luego llama `searchProduct(page, item)` para cada item
  - Captura excepciones por item (log and continue)
  - Cierra browser al terminar esa distribuidora
  - Devuelve resultados agregados

### `service/ItemApiClient.java` (ampliado)
Añadir método:
```java
List<ItemDto> submitPriceComparison(List<PriceComparisonDto> prices)
```
Hace `POST /api/price-comparison` a VapeIt y deserializa la respuesta como `List<ItemDto>`.

---

## Archivos modificados

### `service/ReorderScheduler.java`
El flujo del cron/trigger se amplía para incluir los pasos 2 y 3:
```
1. items = itemApiClient.fetchAllItems().filter(needsReorder)
2. comparisons = priceComparisonService.compare(items)
3. itemsWithDecision = itemApiClient.submitPriceComparison(comparisons)
4. results = botEngine.processOrders(itemsWithDecision)       [ya existe]
5. orderReporter.report(results)                              [ya existe]
```
Hasta que VapeIt tenga el endpoint `/api/price-comparison`, el paso 3 puede devolver
los items originales (fallback: usa el `distribuidor` que ya traía cada item, si lo tiene).

### `distributor/VaperaliaBot.java`
Implementa **ambas** interfaces: `DistributorBot` Y `DistributorPriceBot`.
Spring la inyectará en `BotEngine` (add-to-cart) y en `PriceComparisonService` (búsqueda).

Métodos a implementar (selectores como `// TODO` hasta inspeccionar el DOM):
- `login(Page)` — navega a vaperalia.es, rellena credenciales, submit (privado, compartido)
- `searchProduct(Page, ItemDto)`:
  - Si `item.getUrlProducto()` contiene "vaperalia" → navega directo, scrapes precio
  - Si no → usa buscador de vaperalia.es con `item.getNombre()`, scrapes primer resultado
  - Devuelve `Optional<PriceOptionDto>` (vacío si no hay resultados o hay error)
- `run(Page, List<ItemDto>)`:
  - `login(page)`, luego por cada item: navega → cantidad → añadir al carrito
  - Scraping del carrito → `CartResultDto`

### `application.yml`
- Corregir `vaperalia.url` de `https://vaperalia.com` a `https://vaperalia.es`

---

## Patrón de sesión en PriceComparisonService

Un browser por distribuidora, login una vez, busca todos los items de esa sesión:
```
for each DistributorPriceBot:
  try (playwright; browser):
    page = browser.newPage()
    login(page)
    for each item:
      try: searchProduct(page, item) → acumula resultado
      catch: log + continúa
```

---

## Archivos críticos

| Archivo | Rol |
|---|---|
| `distributor/DistributorBot.java` | No cambia |
| `distributor/VaperaliaBot.java` | Modifica: implementa ambas interfaces |
| `service/BotEngine.java` | No cambia |
| `service/ReorderScheduler.java` | Modifica: añade pasos 2 y 3 al flujo |
| `service/ItemApiClient.java` | Modifica: añade submitPriceComparison() |
| `service/OrderReporter.java` | No cambia |
| `application.yml` | Corrige vaperalia.url |

---

## Orden de implementación

1. `PriceOptionDto`, `PriceComparisonDto` — POJOs sin dependencias
2. `DistributorPriceBot` — interfaz
3. `VaperaliaBot` — implementa ambas interfaces con TODOs en selectores
4. `PriceComparisonService` — orquestador de búsqueda
5. `ItemApiClient` — añade `submitPriceComparison()` (con fallback si VapeIt no tiene el endpoint aún)
6. `ReorderScheduler` — amplía el flujo con los pasos de precio y decisión
7. `application.yml` — fix URL vaperalia
8. [Siguiente sesión] Inspeccionar DOM de vaperalia.es → rellenar selectores en VaperaliaBot

---

## Verificación

```bash
# Compilar
mvn clean package -DskipTests

# Arrancar
mvn spring-boot:run

# Lanzar flujo completo manualmente
curl -X POST http://localhost:8081/trigger

# Ver logs: buscar "PriceComparisonService" y "VaperaliaBot" para ver
# qué precios se están scrapeando y qué decisión devuelve VapeIt
```

Para inspeccionar el DOM de vaperalia.es:
1. `playwright.headless: false` en application.yml
2. `curl -X POST http://localhost:8081/trigger`
3. DevTools en el browser que aparece → buscar selectores de login y búsqueda
4. Rellenar TODO en `VaperaliaBot.java`
