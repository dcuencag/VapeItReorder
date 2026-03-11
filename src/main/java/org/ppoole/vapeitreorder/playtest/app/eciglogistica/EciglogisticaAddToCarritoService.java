package org.ppoole.vapeitreorder.playtest.app.eciglogistica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import org.ppoole.vapeitreorder.playtest.app.service.PlaytestSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class EciglogisticaAddToCarritoService {

    private static final Logger log = LoggerFactory.getLogger(EciglogisticaAddToCarritoService.class);

    private final Path sessionPath;
    private final ObjectMapper objectMapper;

    public EciglogisticaAddToCarritoService(@Value("${vapeit.sessions-dir:sessions}") String sessionsDir,
                                            ObjectMapper objectMapper) {
        this.sessionPath = Path.of(sessionsDir, "eciglogistica-session.json");
        this.objectMapper = objectMapper;
    }

    public AddToCarritoBatchResult addToCarritoBatch(List<AddToCarritoItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Debes enviar al menos un item para añadir al carrito.");
        }

        if (!Files.exists(sessionPath)) {
            String message = "La sesión de Eciglogistica no existe. Cárgala antes de añadir al carrito.";
            log.error(message);
            throw new PlaytestSessionException(message);
        }

        List<String> addedUrls = new ArrayList<>();
        List<UrlFailure> failedUrls = new ArrayList<>();

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true))) {

            var contextOptions = new Browser.NewContextOptions().setStorageStatePath(sessionPath);
            try (BrowserContext context = browser.newContext(contextOptions)) {
                Page page = context.newPage();
                if (!validateSession(page)) {
                    String message = "La sesión de Eciglogistica ha caducado o no es válida. Vuelve a cargarla.";
                    log.error(message);
                    throw new PlaytestSessionException(message);
                }

                for (AddToCarritoItemRequest item : items) {
                    String url = item.url();
                    try {
                        addSingleUrlToCarrito(page, item);
                        addedUrls.add(url);
                    } catch (PlaytestSessionException sessionException) {
                        throw sessionException;
                    } catch (RuntimeException exception) {
                        String message = exception.getMessage() == null ? "Fallo desconocido al añadir al carrito." : exception.getMessage();
                        log.warn("No se pudo añadir URL {} al carrito de Eciglogistica: {}", url, message);
                        failedUrls.add(new UrlFailure(url, message));
                    }
                }

                context.storageState(new BrowserContext.StorageStateOptions().setPath(sessionPath));
            }
        }

        return new AddToCarritoBatchResult(addedUrls, failedUrls);
    }

    private void addSingleUrlToCarrito(Page page, AddToCarritoItemRequest item) {
        String url = item.url();
        int cantidad = item.cantidad();
        String variante = item.variante();

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("La URL del producto es obligatoria.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que 0.");
        }

        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForLoadState();

        if (page.url().contains("entrar")) {
            String message = "Eciglogistica redirigió a login. La sesión ha caducado.";
            log.error(message);
            throw new PlaytestSessionException(message);
        }

        if (variante != null && !variante.isBlank()) {
            selectVariante(page, variante);
        }

        applyQuantity(page, cantidad);
        Response response = clickAddToCartButton(page);
        verifyAddToCartResponse(response);
        verifyCartUpdated(page);
        log.info("Producto añadido al carrito de Eciglogistica: {} (cantidad={}, variante={})", url, cantidad, variante);
    }

    private boolean validateSession(Page page) {
        page.navigate("https://nueva.eciglogistica.com/perfil/basico",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForLoadState();
        return !page.url().contains("entrar");
    }

    private void selectVariante(Page page, String variante) {
        Locator select = page.locator("select.select-attribute-product").first();
        if (select.count() == 0) {
            throw new IllegalStateException("No se encontró selector de variante para el producto.");
        }

        try {
            select.waitFor(new Locator.WaitForOptions().setTimeout(5000));
            page.waitForResponse(
                    response -> response.url().equals(page.url()) && "POST".equalsIgnoreCase(response.request().method()),
                    () -> select.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(variante))
            );
        } catch (TimeoutError timeoutError) {
            select.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(variante));
        }

        log.info("Variante seleccionada en Eciglogistica: {}", variante);
        page.waitForTimeout(1500);
    }

    private void applyQuantity(Page page, int cantidad) {
        String[] quantitySelectors = {
                ".range-input",
                "input[name='quantity']",
                "input[type='number']"
        };

        for (String selector : quantitySelectors) {
            Locator locator = page.locator(selector).first();
            if (locator.count() == 0) {
                continue;
            }

            try {
                locator.waitFor(new Locator.WaitForOptions().setTimeout(3000));
                locator.fill(String.valueOf(cantidad));
                locator.dispatchEvent("input");
                locator.dispatchEvent("change");
                log.info("Cantidad establecida con selector {}: {}", selector, cantidad);
                return;
            } catch (RuntimeException exception) {
                log.debug("No se pudo usar selector de cantidad {}: {}", selector, exception.getMessage());
            }
        }

        throw new IllegalStateException("No se encontró selector de cantidad para establecer " + cantidad + " unidades.");
    }

    private Response clickAddToCartButton(Page page) {
        String[] selectors = {
                ".button-add-chart",
                "button.button-add-chart",
                "button:has-text('AÑADIR AL CARRITO')",
                "button:has-text('Añadir al carrito')"
        };

        for (String selector : selectors) {
            Locator locator = page.locator(selector).first();
            if (locator.count() == 0) {
                continue;
            }

            try {
                locator.waitFor(new Locator.WaitForOptions().setTimeout(5000));
                Response response = page.waitForResponse(
                        candidate -> candidate.url().contains("/api/cart/set")
                                && "POST".equalsIgnoreCase(candidate.request().method()),
                        locator::click
                );
                log.info("Botón add-to-cart Eciglogistica pulsado con selector: {}", selector);
                return response;
            } catch (TimeoutError timeoutError) {
                log.debug("Selector add-to-cart no usable aún (timeout): {}", selector);
            }
        }

        throw new IllegalStateException("No se encontró el botón para añadir al carrito en la página de producto.");
    }

    private void verifyAddToCartResponse(Response response) {
        if (response == null) {
            throw new IllegalStateException("No se recibió respuesta de Eciglogistica al añadir al carrito.");
        }

        try {
            JsonNode root = objectMapper.readTree(response.text());
            JsonNode result = root.path("result");
            if (result.isMissingNode() || result.isNull()) {
                return;
            }

            String error = result.path("error").asText("");
            String status = result.path("status").asText("");
            String code = result.path("code").asText("");

            if ("restriction_article".equals(error)) {
                throw new IllegalStateException("El producto está restringido para la cuenta o país actual.");
            }
            if ("qty_fail".equals(error)) {
                throw new IllegalStateException("No hay stock o el stock es insuficiente para la cantidad solicitada.");
            }
            if ("error".equalsIgnoreCase(status) || "404".equals(code)) {
                String productStock = result.path("product_stock").asText("");
                if (!productStock.isBlank()) {
                    throw new IllegalStateException("No hay stock suficiente. Stock disponible: " + productStock + ".");
                }
                throw new IllegalStateException("Eciglogistica devolvió un error al añadir el producto al carrito.");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo interpretar la respuesta de Eciglogistica al añadir al carrito.", exception);
        }
    }

    private void verifyCartUpdated(Page page) {
        page.waitForTimeout(1000);

        Locator ajaxCart = page.locator("#ajax-cart");
        if (ajaxCart.count() > 0) {
            try {
                ajaxCart.waitFor(new Locator.WaitForOptions().setTimeout(5000));
                String content = ajaxCart.textContent();
                if (content != null && !content.isBlank()) {
                    return;
                }
            } catch (TimeoutError ignored) {
                // Fallback to cart value
            }
        }

        Locator cartValue = page.locator(".cart-value").first();
        if (cartValue.count() > 0) {
            String text = cartValue.textContent();
            if (text != null && !text.trim().isEmpty()) {
                return;
            }
        }

        throw new IllegalStateException("No se pudo confirmar que el producto se añadiera al carrito de Eciglogistica.");
    }

    public record AddToCarritoBatchResult(List<String> addedUrls, List<UrlFailure> failedUrls) {
        public AddToCarritoBatchResult {
            addedUrls = addedUrls == null ? List.of() : List.copyOf(addedUrls);
            failedUrls = failedUrls == null ? List.of() : List.copyOf(failedUrls);
        }
    }

    public record AddToCarritoItemRequest(String url, int cantidad, String variante) {
        public AddToCarritoItemRequest {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("La URL del producto es obligatoria.");
            }
            if (cantidad <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor que 0.");
            }
        }
    }

    public record UrlFailure(String url, String message) {}
}
