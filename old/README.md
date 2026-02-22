# VapeItReorder

Microservicio Spring Boot que automatiza el rellenado del carrito en las webs de los distribuidores cuando el stock baja del mínimo. Se ejecuta dos veces al día; el humano entra a la web del distribuidor y confirma la compra.

## Cómo funciona

1. El cron se dispara a las **14:30 y 21:30**
2. `ReorderScheduler` llama a `GET /api/items` del backend principal (VapeIt)
3. Filtra los ítems donde `currentStock < minimoUnidades`
4. `BotEngine` agrupa los ítems por distribuidor y abre un navegador Chromium por cada uno
5. Cada `DistributorBot` hace login, navega a los productos, establece cantidades y añade al carrito
6. Al terminar, el bot hace scraping del carrito y devuelve un `CartResultDto`
7. `OrderReporter` envía cada resultado al backend vía `POST /api/orders/status`
8. El humano revisa el carrito en la web del distribuidor y confirma la compra

También se puede disparar manualmente con `POST /trigger`.

## Estructura del proyecto

```
src/main/java/org/ppoole/vapeitreorder/
├── VapeItReorderApplication.java
├── config/
│   └── RestTemplateConfig.java
├── controller/
│   └── TriggerController.java        # POST /trigger
├── dto/
│   ├── ItemDto.java                   # Producto del backend (con needsReorder())
│   ├── CartItemDto.java               # Línea del carrito scrapeado
│   └── CartResultDto.java             # Resultado por distribuidor
├── service/
│   ├── ItemApiClient.java             # GET /api/items al backend
│   ├── BotEngine.java                 # Orquestador multi-distribuidor
│   ├── OrderReporter.java             # POST resultados al backend
│   └── ReorderScheduler.java          # Cron + coordinación
└── distributor/
    ├── DistributorBot.java            # Interfaz
    ├── EciglogisticaBot.java          # Implementación (selectores pendientes de DOM)
    └── VaperaliaBot.java              # Stub (por implementar)
```

## Configuración

Editar `src/main/resources/application.yml`:

```yaml
vapeit:
  api-url: http://localhost:8080/api/items       # Endpoint de productos
  report-url: http://localhost:8080/api/orders/status  # Endpoint de reporte

reorder:
  cron: "0 30 14,21 * * *"   # 14:30 y 21:30 cada día

eciglogistica:
  url: https://nueva.eciglogistica.com
  username: TU_USUARIO
  password: TU_PASSWORD

vaperalia:
  url: https://vaperalia.com
  username: TU_USUARIO
  password: TU_PASSWORD

playwright:
  headless: true   # false para ver el navegador durante el desarrollo
```

## Cómo ejecutar

### Requisitos
- Java 21
- Maven
- El backend VapeIt corriendo en `localhost:8080`

### Compilar

```bash
mvn clean package -DskipTests
```

### Instalar el navegador Chromium (solo la primera vez)

```bash
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

### Ejecutar

```bash
mvn spring-boot:run
```

> Alternativa con el jar (requiere que `java` esté en el PATH):
> ```bash
> java -jar target/vapeit-reorder-1.0.0-SNAPSHOT.jar
> ```

Este servicio arranca en el **puerto 8081** (el 8080 lo usa el backend VapeIt).

### Probar sin esperar al cron

Opción 1 — el cron ya está configurado a cada minuto en `application.yml` para desarrollo:
```yaml
reorder:
  cron: "0 * * * * *"
```
Volver a `"0 30 14,21 * * *"` para producción.

Opción 2 — trigger HTTP manual (la app debe estar corriendo):
```bash
curl -X POST http://localhost:8081/trigger
```

## Estado actual

| Componente | Estado |
|---|---|
| Cron + orquestación | Funcionando |
| Agrupación por distribuidor | Funcionando |
| Reporte POST al backend | Funcionando |
| Trigger HTTP manual | Funcionando |
| EciglogisticaBot — login | **Pendiente** (selectores TODO) |
| EciglogisticaBot — añadir al carrito | **Pendiente** (selectores TODO) |
| EciglogisticaBot — scraping del carrito | **Pendiente** (selectores TODO) |
| VaperaliaBot | **Stub** (por implementar) |

Para completar `EciglogisticaBot`, inspeccionar el DOM de `https://nueva.eciglogistica.com` y rellenar los selectores marcados como `// TODO` en `EciglogisticaBot.java`.

## Relación con el backend VapeIt

Este servicio es un microservicio hermano de `/Users/daniel/code/VapeIt`. No comparte código ni base de datos; la comunicación es exclusivamente HTTP:

- `GET /api/items` → obtiene todos los productos con su stock actual
- `POST /api/orders/status` → recibe el resultado del bot (qué está en el carrito)

El backend necesita que los ítems incluyan los campos `distribuidor`, `urlProducto` y opcionalmente `cantidadAPedir`. Si `cantidadAPedir` es null, el bot calcula `maximoUnidades - currentStock`.
