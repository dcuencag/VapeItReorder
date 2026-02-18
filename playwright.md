# Cómo funciona Playwright

## Qué es Playwright

Playwright es una librería que **controla un navegador real** (Chromium, Firefox o Safari) desde código. No simula un navegador — abre uno de verdad, igual que cuando tú abres Chrome, pero sin que lo veas (modo headless) o visualmente si lo configuras.

---

## Por qué funciona (y por qué es difícil de bloquear)

Los sitios web normalmente intentan detectar bots mirando señales como:
- ¿Hay ratón moviéndose?
- ¿Hay eventos de teclado reales?
- ¿El navegador tiene plugins instalados?
- ¿El JavaScript se ejecuta correctamente?

Playwright pasa casi todos estos checks porque **es un navegador real**. No es un scraper que lee HTML estático — ejecuta el JavaScript de la página, renderiza CSS, maneja cookies y sesiones exactamente igual que un usuario humano. La diferencia es que en vez de un humano haciendo clic, lo hace tu código.

---

## Cómo funciona paso a paso

```
Tu código Java
    │
    ▼
Playwright (librería)
    │  habla con el navegador por el protocolo CDP (Chrome DevTools Protocol)
    ▼
Chromium (proceso separado corriendo en tu máquina)
    │  hace peticiones HTTP normales
    ▼
Web del distribuidor (ve una visita normal de Chrome)
```

Cuando escribes `page.navigate("https://eciglogistica.com")`, Playwright le dice a Chromium "ve a esa URL". Chromium descarga el HTML, ejecuta el JavaScript, renderiza la página — exactamente igual que tú con el navegador.

---

## Cómo funciona el scraping

Una vez la página está cargada, Playwright puede:

**Leer el DOM (Document Object Model)** — extraer texto, atributos, valores de cualquier elemento:
```java
// obtener el texto de un elemento
String precio = page.locator(".precio").textContent();

// obtener todos los productos del carrito
var filas = page.locator("tr.cart-item").all();
for (var fila : filas) {
    String nombre = fila.locator(".nombre").textContent();
    String cantidad = fila.locator("input.qty").inputValue();
}
```

**Interactuar** — clicar, escribir, seleccionar:
```java
page.fill("input[name='username']", "miusuario");
page.click("button[type='submit']");
page.waitForLoadState(); // espera a que cargue la siguiente página
```

El scraping en nuestro caso sería: después de añadir todos los productos al carrito, navegar a la página del carrito y extraer la lista de lo que hay dentro — nombre, cantidad y precio de cada línea — para enviárselo al backend de VapeIt.

---

## Por qué `waitForLoadState()` es importante

Las webs modernas cargan contenido de forma asíncrona (AJAX, React, Vue...). Si lees el DOM inmediatamente después de hacer clic, puede que el contenido aún no haya aparecido. `waitForLoadState()` pausa la ejecución hasta que el navegador termina de cargar, garantizando que el HTML que vas a leer ya está completo.

---

## Cómo sabe Playwright qué tiene que hacer para meter un producto al carrito

No lo sabe. **Tú se lo tienes que decir.**

Playwright es una herramienta, no inteligencia artificial. No entiende "añade este producto al carrito" — solo entiende instrucciones exactas como:

- "Ve a esta URL"
- "Escribe esto en este input"
- "Haz clic en este botón"

El trabajo pendiente en `EciglogisticaBot` es precisamente ese: entrar a la web de eciglogistica manualmente, buscar un producto, inspeccionar el HTML con F12 para ver cómo se llaman los elementos, y luego traducir esos pasos a instrucciones de Playwright.

Por ejemplo, si para añadir un producto al carrito tú haces:
1. Vas a la URL del producto
2. Cambias el número en un campo de cantidad
3. Haces clic en "Añadir al carrito"

Playwright tiene que hacer exactamente lo mismo, paso a paso:
```java
page.navigate("https://nueva.eciglogistica.com/producto/ivg-001");
page.fill("#cantidad", "10");
page.click(".btn-anadir-carrito");
page.waitForLoadState();
```

Los valores `#cantidad` y `.btn-anadir-carrito` no se pueden inventar — son los que aparecen en el HTML real de esa web cuando inspeccionas con F12.

---

## Resumen en una línea

Playwright = tú, pero en código. Abre el navegador, navega, hace clic, escribe, lee lo que ve en pantalla. La web no sabe que no es un humano.
