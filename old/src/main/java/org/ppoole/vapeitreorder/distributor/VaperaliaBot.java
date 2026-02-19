package org.ppoole.vapeitreorder.distributor;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.ppoole.vapeitreorder.dto.CartItemDto;
import org.ppoole.vapeitreorder.dto.CartResultDto;
import org.ppoole.vapeitreorder.dto.ItemDto;
import org.ppoole.vapeitreorder.dto.PriceOptionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class VaperaliaBot implements DistributorBot, DistributorPriceBot {

    private static final Logger log = LoggerFactory.getLogger(VaperaliaBot.class);

    private final String url;
    private final String username;
    private final String password;
    private final String sessionFilePath;

    public VaperaliaBot(
            @Value("${vaperalia.url:}") String url,
            @Value("${vaperalia.username:}") String username,
            @Value("${vaperalia.password:}") String password,
            @Value("${vaperalia.session-file:vaperalia-session.json}") String sessionFilePath) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.sessionFilePath = sessionFilePath;
    }

    @Override
    public Browser.NewContextOptions newContextOptions() {
        var options = new Browser.NewContextOptions();
        var sessionPath = Paths.get(sessionFilePath);
        if (Files.exists(sessionPath)) {
            log.info("VaperaliaBot: loading saved session from {}", sessionFilePath);
            options.setStorageStatePath(sessionPath);
        }
        return options;
    }

    @Override
    public String getDistributorName() {
        return "vaperalia";
    }

    @Override
    public Optional<PriceOptionDto> searchProduct(Page page, ItemDto item) {
        try {
            if (item.getUrlProducto() != null && item.getUrlProducto().contains("vaperalia")) {
                page.navigate(item.getUrlProducto());
            } else {
                String query = URLEncoder.encode(item.getNombre() != null ? item.getNombre() : item.getSku(), StandardCharsets.UTF_8);
                // TODO: replace with actual vaperalia search URL after inspecting DOM
                page.navigate(url + "/buscar?q=" + query);
            }
            page.waitForLoadState();

            if (page.url().contains("autenticacion")) {
                login(page);
                page.navigate(item.getUrlProducto() != null ? item.getUrlProducto()
                        : url + "/buscar?q=" + URLEncoder.encode(item.getNombre() != null ? item.getNombre() : item.getSku(), StandardCharsets.UTF_8));
                page.waitForLoadState();
            }

            var option = new PriceOptionDto();
            option.setDistribuidor(getDistributorName());
            option.setNombre(page.locator("h1[itemprop='name']").textContent().trim());
            option.setPrecio(Double.parseDouble(page.locator("meta[itemprop='price']").getAttribute("content")));
            option.setUrlProducto(page.url());
            var availability = page.locator("link[itemprop='availability']").getAttribute("href");
            option.setDisponible(availability != null && availability.contains("InStock"));
            log.info("VaperaliaBot.searchProduct sku={} precio={} disponible={}", item.getSku(), option.getPrecio(), option.isDisponible());
            return Optional.of(option);
        } catch (Exception e) {
            log.error("Failed to search product sku={} on vaperalia: {}", item.getSku(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public CartResultDto run(Page page, List<ItemDto> items) {
        var result = new CartResultDto();
        result.setDistribuidor(getDistributorName());
        result.setTimestamp(Instant.now().toString());

        var errores = new ArrayList<String>();
        var carrito = new ArrayList<CartItemDto>();

        login(page);

        for (ItemDto item : items) {
            try {
                addToCart(page, item);
            } catch (Exception e) {
                log.error("Failed to add sku={} to vaperalia cart: {}", item.getSku(), e.getMessage());
                errores.add("sku=" + item.getSku() + ": " + e.getMessage());
            }
        }

        try {
            carrito.addAll(scrapeCart(page));
        } catch (Exception e) {
            log.error("Failed to scrape vaperalia cart: {}", e.getMessage());
            errores.add("scrapeCart: " + e.getMessage());
        }

        result.setCarrito(carrito);
        result.setErrores(errores);
        result.setStatus(errores.isEmpty() ? "ok" : "partial");
        return result;
    }

    private void login(Page page) {
        page.navigate(url);
        page.waitForLoadState();

        if (isLoggedIn(page)) {
            log.info("VaperaliaBot: session valid, already logged in");
            return;
        }

        log.info("VaperaliaBot: not logged in, filling credentials");
        page.navigate(url + "/autenticacion?back=my-account");
        page.waitForLoadState();

        page.locator("input[name='email']").fill(username);
        page.locator("input[name='passwd']").fill(password);
        // reCAPTCHA requires headless=false on first login — user must check "No soy un robot"
        page.locator("button#SubmitLogin").click();
        // Wait up to 2 min for post-login redirect (reCAPTCHA may need manual interaction)
        page.waitForURL("**/mi-cuenta**", new Page.WaitForURLOptions().setTimeout(120_000));
        log.info("VaperaliaBot: login successful");
        saveSession(page.context());
    }

    private boolean isLoggedIn(Page page) {
        return page.locator("a.account[href*='mi-cuenta']").count() > 0;
    }

    private void saveSession(BrowserContext context) {
        if (sessionFilePath.isEmpty()) return;
        try {
            context.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get(sessionFilePath)));
            log.info("VaperaliaBot: session saved to {}", sessionFilePath);
        } catch (Exception e) {
            log.warn("VaperaliaBot: failed to save session: {}", e.getMessage());
        }
    }

    private void addToCart(Page page, ItemDto item) {
        int cantidad = calculateQuantity(item);
        if (item.getUrlProducto() != null) {
            page.navigate(item.getUrlProducto());
        } else {
            // TODO: search for product and navigate to its page
            log.warn("No urlProducto for sku={}, skipping", item.getSku());
            return;
        }
        page.waitForLoadState();
        // TODO: fill in after inspecting vaperalia.es product page DOM
        // page.locator("TODO_quantity_selector").fill(String.valueOf(cantidad));
        // page.locator("TODO_add_to_cart_selector").click();
        // page.waitForLoadState();
        log.warn("VaperaliaBot.addToCart selectors not yet implemented for sku={}, cantidad={}", item.getSku(), cantidad);
    }

    private List<CartItemDto> scrapeCart(Page page) {
        // TODO: navigate to cart and scrape items after inspecting DOM
        // page.navigate(url + "/TODO_cart_path");
        // page.waitForLoadState();
        log.warn("VaperaliaBot.scrapeCart selectors not yet implemented");
        return List.of();
    }

    private int calculateQuantity(ItemDto item) {
        if (item.getCantidadAPedir() != null) return item.getCantidadAPedir();
        if (item.getMaximoUnidades() != null && item.getCurrentStock() != null) {
            return item.getMaximoUnidades() - item.getCurrentStock();
        }
        return 0;
    }
}
