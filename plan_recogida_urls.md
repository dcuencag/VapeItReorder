# Plan: Descubrimiento automático de URLs de distribuidores

## Contexto
VapeItReorder necesita tener en su BD (`producto_distribuidora`) la URL de cada producto en cada distribuidor. Actualmente esto se hace manualmente. Con 100+ productos y 2 distribuidores, es inviable. Ambos distribuidores tienen buscador web.

**Objetivo**: Nuevo comando `--discover` que, para cada producto de VapeIt, busque en el buscador de cada distribuidor, proponga el match más probable y guarde la URL automáticamente.

## Fase 1: Exploración de buscadores (browser)
Antes de escribir código, necesitamos explorar con el navegador:
- **Vaperalia** (`vaperalia.es`): URL del buscador, estructura del HTML de resultados, selectores para nombre y URL de cada resultado
- **Eciglogistica** (`nueva.eciglogistica.com`): lo mismo

Esto determinará los selectores CSS y la lógica de paginación.

## Fase 2: Sync de productos desde VapeIt API

### Nuevo método en `ItemApiClient`
- `fetchAllItems()` → `GET /api/items` → devuelve todos los `ItemDto` (no solo los bajo mínimos)
- Añadir campo `nombre` a `ItemDto` (actualmente no lo tiene)

### Nuevo servicio `ProductoSyncService`
- Para cada item de VapeIt, insertar/actualizar en tabla `PRODUCTO` (sku + nombre)
- Usar `ProductoRepository.saveAll()`

## Fase 3: Descubrimiento de URLs por distribuidor

### Nuevo servicio `UrlDiscoveryService`
Para cada producto en PRODUCTO que **no tenga** URL para un distribuidor dado:
1. Buscar el nombre del producto en el buscador del distribuidor (Playwright)
2. Parsear los resultados de búsqueda (nombre + URL de cada resultado)
3. Si hay un solo resultado → match automático, guardar en `producto_distribuidora`
4. Si hay varios resultados → loguear los candidatos para revisión manual
5. Si no hay resultados → loguear como "no encontrado"

### Por distribuidor
- `VaperaliaUrlDiscoveryService` — busca en vaperalia.es, extrae URLs de resultados
- `EciglogisticaUrlDiscoveryService` — busca en eciglogistica.com, extrae URLs de resultados

### Gestión de variantes
- Si un resultado de Eciglogistica tiene `select.select-attribute-product`, extraer las opciones como variantes
- Crear una entrada en `producto_distribuidora` por cada variante (misma URL, distinta variante)

## Fase 4: Orquestación en `PlaytestApp`

### Nuevo modo `--discover`
```
mvn spring-boot:run -Dspring-boot.run.arguments="--discover"
```
Flujo:
1. Sync productos desde VapeIt API → tabla PRODUCTO
2. Para cada distribuidor, ejecutar descubrimiento de URLs
3. Mostrar resumen: X productos mapeados, Y sin match, Z con múltiples candidatos

## Ficheros a crear/modificar

| Fichero | Acción |
|---------|--------|
| `dto/ItemDto.java` | Añadir campo `nombre` |
| `service/ItemApiClient.java` | Nuevo método `fetchAllItems()` |
| `service/ProductoSyncService.java` | **Nuevo** — sync VapeIt → PRODUCTO |
| `vaperalia/VaperaliaUrlDiscoveryService.java` | **Nuevo** — búsqueda en Vaperalia |
| `eciglogistica/EciglogisticaUrlDiscoveryService.java` | **Nuevo** — búsqueda en Eciglogistica |
| `PlaytestApp.java` | Nuevo modo `--discover` |

## Verificación
1. `mvn clean compile`
2. Explorar buscadores de cada distribuidor con el navegador (MCP tools) para obtener selectores
3. `mvn spring-boot:run -Dspring-boot.run.arguments="--discover"` — debe sincronizar productos y descubrir URLs
4. `mvn spring-boot:run` — el scraper normal debe funcionar con las URLs descubiertas
