package org.ppoole.vapeitreorder.playtest.app.vaperalia;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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
public class VaperaliaAddToCarritoService {

    private static final Logger log = LoggerFactory.getLogger(VaperaliaAddToCarritoService.class);

    private final Path sessionPath;

    public VaperaliaAddToCarritoService(@Value("${vapeit.sessions-dir:sessions}") String sessionsDir) {
        this.sessionPath = Path.of(sessionsDir, "vaperalia-session.json");
    }

    public AddToCarritoBatchResult addToCarritoBatch(List<AddToCarritoItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Debes enviar al menos un item para añadir al carrito.");
        }

        if (!Files.exists(sessionPath)) {
            String message = "La sesión de Vaperalia no existe. Cárgala antes de añadir al carrito.";
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
                    String message = "La sesión de Vaperalia ha caducado o no es válida. Vuelve a cargarla.";
                    log.error(message);
                    throw new PlaytestSessionException(message);
                }

                for (AddToCarritoItemRequest item : items) {
                    String url = item.url();
                    try {
                        addSingleUrlToCarrito(page, url, item.cantidad());
                        addedUrls.add(url);
                    } catch (PlaytestSessionException sessionException) {
                        throw sessionException;
                    } catch (RuntimeException exception) {
                        String message = exception.getMessage() == null ? "Fallo desconocido al añadir al carrito." : exception.getMessage();
                        log.warn("No se pudo añadir URL {} al carrito: {}", url, message);
                        failedUrls.add(new UrlFailure(url, message));
                    }
                }

                context.storageState(new BrowserContext.StorageStateOptions().setPath(sessionPath));
            }
        }

        return new AddToCarritoBatchResult(addedUrls, failedUrls);
    }

    private void addSingleUrlToCarrito(Page page, String url, int cantidad) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("La URL del producto es obligatoria.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que 0.");
        }

        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForLoadState();

        if (page.url().contains("autenticacion")) {
            String message = "Vaperalia redirigió a login. La sesión ha caducado.";
            log.error(message);
            throw new PlaytestSessionException(message);
        }

        applyQuantity(page, cantidad);
        clickAddToCartButton(page);
        verifyAddedToCart(page);
        log.info("Producto añadido al carrito de Vaperalia: {} (cantidad={})", url, cantidad);
    }

    private boolean validateSession(Page page) {
        page.navigate("https://vaperalia.es/mi-cuenta",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForLoadState();
        return !page.url().contains("autenticacion");
    }

    private void clickAddToCartButton(Page page) {
        String[] selectors = {
                "#add_to_cart button",
                "#add_to_cart button[name='Submit']",
                "button.exclusive",
                "button[name='Submit']",
                "button:has-text('Añadir al carrito')",
                "button:has-text('Agregar al carrito')",
                "button:has-text('Add to cart')"
        };

        for (String selector : selectors) {
            Locator locator = page.locator(selector).first();
            if (locator.count() == 0) {
                continue;
            }

            try {
                locator.waitFor(new Locator.WaitForOptions().setTimeout(5000));
                locator.click();
                log.info("Botón add-to-cart pulsado con selector: {}", selector);
                return;
            } catch (TimeoutError timeoutError) {
                log.debug("Selector add-to-cart no usable aún (timeout): {}", selector);
            }
        }

        throw new IllegalStateException("No se encontró el botón para añadir al carrito en la página de producto.");
    }

    private void applyQuantity(Page page, int cantidad) {
        String[] quantitySelectors = {
                "#quantity_wanted",
                "input[name='qty']",
                "input[name='quantity_wanted']"
        };

        for (String selector : quantitySelectors) {
            Locator locator = page.locator(selector).first();
            if (locator.count() == 0) {
                continue;
            }

            try {
                locator.waitFor(new Locator.WaitForOptions().setTimeout(3000));
                locator.fill(String.valueOf(cantidad));
                locator.dispatchEvent("change");
                locator.dispatchEvent("input");
                log.info("Cantidad establecida con selector {}: {}", selector, cantidad);
                return;
            } catch (RuntimeException exception) {
                log.debug("No se pudo usar selector de cantidad {}: {}", selector, exception.getMessage());
            }
        }

        if (cantidad > 1) {
            throw new IllegalStateException("No se encontró selector de cantidad para establecer " + cantidad + " unidades.");
        }
    }

    private void verifyAddedToCart(Page page) {
        String[] visibleSuccessSelectors = {
                "#layer_cart",
                ".layer_cart",
                ".alert-success",
                ".toast-success"
        };
        String[] presentSuccessSelectors = {
                ".ajax_cart_quantity",
                ".cart-products-count"
        };

        page.waitForTimeout(800);

        for (String selector : visibleSuccessSelectors) {
            try {
                page.locator(selector).first().waitFor(new Locator.WaitForOptions().setTimeout(7000));
                if (page.locator(selector).first().isVisible()) {
                    return;
                }
            } catch (TimeoutError ignored) {
                // Try next selector
            }
        }

        for (String selector : presentSuccessSelectors) {
            Locator locator = page.locator(selector).first();
            if (locator.count() > 0) {
                String text = locator.textContent();
                if (text != null && !text.trim().isEmpty()) {
                    return;
                }
            }
        }

        if (page.url().contains("carrito") || page.url().contains("controller=cart")) {
            return;
        }

        throw new IllegalStateException("No se pudo confirmar que el producto se añadiera al carrito.");
    }

    public record AddToCarritoBatchResult(List<String> addedUrls, List<UrlFailure> failedUrls) {
        public AddToCarritoBatchResult {
            addedUrls = addedUrls == null ? List.of() : List.copyOf(addedUrls);
            failedUrls = failedUrls == null ? List.of() : List.copyOf(failedUrls);
        }
    }

    public record AddToCarritoItemRequest(String url, int cantidad) {
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
