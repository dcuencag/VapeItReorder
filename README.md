# VapeItReorder

Microservicio Spring Boot que automatiza el reabastecimiento de inventario con los distribuidores.

---

## playtest/

Proyecto Maven standalone (sin Spring) para inspeccionar el DOM de webs de distribuidores con Playwright. Úsalo para descubrir selectores antes de implementarlos en los bots.

### Requisitos

- Java 21
- Chromium de Playwright instalado:

```bash
cd playtest
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

### Uso

Desde el directorio `playtest/`:

**1. Guardar sesión (primera vez o cuando expire)**

```bash
mvn compile exec:java -Dexec.args="--login"
```

Abre un navegador visible. Inicia sesión manualmente y espera — la sesión se guarda automáticamente en `vaperalia-session.json`.

**2. Scraping con sesión guardada**

```bash
mvn compile exec:java
```

Navega a las URLs de producto definidas en `PRODUCT_URLS` y extrae nombre y precio.

### Qué extrae

Para cada URL de producto de Vaperalia:

| Campo | Fuente |
|---|---|
| Nombre | `h1[itemprop='name']` |
| Precio (con impuesto especial) | `window.productPrice` — JS global, no está en el DOM |

### Añadir productos a inspeccionar

Edita la lista `PRODUCT_URLS` en `src/main/java/VaperaliaPlaytest.java`:

```java
private static final List<String> PRODUCT_URLS = List.of(
    "https://vaperalia.es/tu-producto-aqui.html"
);
```

### Notas

- `vaperalia-session.json` contiene cookies reales — está en `.gitignore` y no se commitea
- Si el scraping redirige a la página de login, ejecuta `--login` de nuevo para renovar la sesión
- El precio solo aparece como JS global (`window.productPrice`), no en el HTML — hay que usar `page.evaluate()`
