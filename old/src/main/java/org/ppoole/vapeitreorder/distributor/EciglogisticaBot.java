package org.ppoole.vapeitreorder.distributor;

import com.microsoft.playwright.Page;
import org.ppoole.vapeitreorder.dto.CartItemDto;
import org.ppoole.vapeitreorder.dto.CartResultDto;
import org.ppoole.vapeitreorder.dto.ItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class EciglogisticaBot implements DistributorBot {

    private static final Logger log = LoggerFactory.getLogger(EciglogisticaBot.class);

    private final String url;
    private final String username;
    private final String password;

    public EciglogisticaBot(
            @Value("${eciglogistica.url}") String url,
            @Value("${eciglogistica.username:}") String username,
            @Value("${eciglogistica.password:}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public String getDistributorName() {
        return "eciglogistica";
    }

    @Override
    public CartResultDto run(Page page, List<ItemDto> items) {
        var result = new CartResultDto();
        result.setDistribuidor("eciglogistica");
        result.setTimestamp(Instant.now().toString());
        var errores = new ArrayList<String>();

        try {
            login(page);
        } catch (Exception e) {
            log.error("Login failed for eciglogistica: {}", e.getMessage());
            result.setStatus("error");
            errores.add("Login failed: " + e.getMessage());
            result.setErrores(errores);
            result.setCarrito(List.of());
            return result;
        }

        for (ItemDto item : items) {
            try {
                addToCart(page, item);
            } catch (Exception e) {
                log.error("Failed to add item {} to cart: {}", item.getSku(), e.getMessage());
                errores.add("Item " + item.getSku() + ": " + e.getMessage());
            }
        }

        List<CartItemDto> carrito = List.of();
        try {
            carrito = scrapeCart(page);
        } catch (Exception e) {
            log.error("Failed to scrape cart: {}", e.getMessage());
            errores.add("Cart scrape failed: " + e.getMessage());
        }

        result.setCarrito(carrito);
        result.setErrores(errores);
        result.setStatus(errores.isEmpty() ? "ok" : "error");
        return result;
    }

    private void login(Page page) {
        log.info("Logging in to {}", url);
        page.navigate(url);

        // TODO: Fill in actual login selectors once the site structure is known
        // page.fill("input[name='username']", username);
        // page.fill("input[name='password']", password);
        // page.click("button[type='submit']");V
        // page.waitForLoadState();

        log.warn("Login is a placeholder - selectors need to be configured");
    }

    private void addToCart(Page page, ItemDto item) {
        int quantity = item.getCantidadAPedir() != null
                ? item.getCantidadAPedir()
                : item.getMaximoUnidades() - item.getCurrentStock();
        log.info("Adding {} units of {} (SKU: {}) to cart", quantity, item.getNombre(), item.getSku());

        if (item.getUrlProducto() != null) {
            page.navigate(item.getUrlProducto());
        }

        // TODO: Set quantity and add to cart
        // page.fill("input.quantity", String.valueOf(quantity));
        // page.click("button.add-to-cart");
        // page.waitForLoadState();

        log.warn("Add to cart is a placeholder - selectors need to be configured");
    }

    private List<CartItemDto> scrapeCart(Page page) {
        // TODO: Navigate to cart page and scrape items
        // page.navigate(url + "/carrito");
        // var rows = page.querySelectorAll("tr.cart-item");
        // return rows.stream().map(row -> { ... }).toList();

        log.warn("Cart scraping is a placeholder - selectors need to be configured");
        return new ArrayList<>();
    }
}
