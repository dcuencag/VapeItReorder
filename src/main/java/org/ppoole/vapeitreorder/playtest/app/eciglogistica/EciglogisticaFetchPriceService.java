package org.ppoole.vapeitreorder.playtest.app.eciglogistica;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.ppoole.vapeitreorder.playtest.app.domain.ProductoRespuesta;
import org.ppoole.vapeitreorder.playtest.app.repository.ProductoDistribuidoraRepository;
import org.ppoole.vapeitreorder.playtest.app.service.PlaytestSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EciglogisticaFetchPriceService {

    private static final Logger log = LoggerFactory.getLogger(EciglogisticaFetchPriceService.class);
    private static final double REQUEST_DELAY_MS = 2500;
    private static final double REQUEST_DELAY_JITTER_MS = 1000;

    private final Path sessionPath;

    public EciglogisticaFetchPriceService(@Value("${vapeit.sessions-dir:sessions}") String sessionsDir) {
        this.sessionPath = Path.of(sessionsDir, "eciglogistica-session.json");
    }

    public void saveSession() {
        log.info("Abriendo navegador visible — inicia sesión en Eciglogistica y espera...");
        ensureSessionDirectoryExists();

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(false))) {

            try (BrowserContext context = browser.newContext()) {
                Page page = context.newPage();
                page.navigate("https://nueva.eciglogistica.com/entrar");
                log.info("Esperando hasta 5 minutos para que inicies sesión...");
                page.waitForURL(url -> !url.contains("entrar"), new Page.WaitForURLOptions().setTimeout(300_000));
                context.storageState(new BrowserContext.StorageStateOptions().setPath(sessionPath));
                log.info("Sesión guardada en {}", sessionPath.toAbsolutePath());
            }
        }
    }

    public List<ProductoRespuesta> scrape(List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> urls) {

        List<ProductoRespuesta> productoRespuestas = new ArrayList<>();
        if (urls.isEmpty()) {
            log.info("No Eciglogistica URLs to scrape");
            return productoRespuestas;
        }

        List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> failedTrios = new ArrayList<>();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true))) {

            var contextOptions = new Browser.NewContextOptions();
            if (!Files.exists(sessionPath)) {
                String message = "La sesión de Eciglogistica no existe. Carga la sesión desde la interfaz antes de ejecutar el reorder.";
                log.error(message);
                throw new PlaytestSessionException(message);
            }
            log.info("Cargando sesión desde {}", sessionPath.toAbsolutePath());
            contextOptions.setStorageStatePath(sessionPath);

            try (BrowserContext context = browser.newContext(contextOptions)) {
                Page page = context.newPage();
                if (!validateSession(page)) {
                    String message = "La sesión de Eciglogistica ha caducado o no es válida. Vuelve a cargarla desde la interfaz.";
                    log.error(message);
                    throw new PlaytestSessionException(message);
                }
                boolean firstRequest = true;
                for (ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio url : urls) {
                    try {
                        if (!firstRequest) {
                            waitBeforeNextRequest(page);
                        }
                        firstRequest = false;
                        ProductoRespuesta productoRespuesta = scrapeProduct(page, url);
                        if (productoRespuesta == null) {
                            failedTrios.add(url);
                            log.error("No se pudo obtener precio para SKU {} ({})", url.getSku(), url.getUrl());
                        } else {
                            productoRespuestas.add(productoRespuesta);
                        }
                    } catch (PlaytestSessionException e) {
                        throw e;
                    } catch (Exception e) {
                        failedTrios.add(url);
                        log.error("Error procesando SKU {} ({}): {}", url.getSku(), url.getUrl(), e.getMessage());
                    }
                }

                if (!failedTrios.isEmpty()) {
                    log.error("Fallaron {} productos de Eciglogistica", failedTrios.size());
                    for (ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio failedTrio : failedTrios) {
                        log.error("FAILED -> SKU {} | {}", failedTrio.getSku(), failedTrio.getUrl());
                    }
                }
            }
        }

        return productoRespuestas;
    }

    private void ensureSessionDirectoryExists() {
        Path parent = sessionPath.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear el directorio de sesiones: " + parent.toAbsolutePath(), e);
        }
    }

    private boolean validateSession(Page page) {
        page.navigate("https://nueva.eciglogistica.com/perfil/basico", new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForLoadState();
        if (page.url().contains("entrar")) {
            return false;
        }
        log.info("Sesión de Eciglogistica válida");
        return true;
    }

    private ProductoRespuesta scrapeProduct(Page page, ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio skuUrl) {
        String url = skuUrl.getUrl();
        String variante = skuUrl.getVariante();
        log.info("Scraping {} ({}){}",
                skuUrl.getSku(), url, variante != null ? " variante=" + variante : "");

        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForLoadState();

        if (page.url().contains("entrar")) {
            String message = "Eciglogistica redirigió a login durante el scraping. La sesión ha caducado o no se cargó correctamente.";
            log.error(message);
            throw new PlaytestSessionException(message);
        }

        try {
            if (variante != null) {
                selectVariante(page, variante);
            }

            String nombre = page.locator("h1").first().textContent().trim();
            String precioText = page.locator("h6.product-price").textContent().trim().replace("€", "").trim();
            Double precioValue = Double.parseDouble(precioText);

            String displayName = variante != null ? nombre + " (" + variante + ")" : nombre;
            log.info("\n────────────────────────────────────────────\n  Producto:  {}\n  Precio:    {} €\n────────────────────────────────────────────", displayName, precioValue);
            return new ProductoRespuesta(skuUrl.getSku(), displayName, precioValue, skuUrl.getUrl(), skuUrl.getDistribuidoraName());
        } catch (Exception e) {
            log.error("Error scraping {}: {}", url, e.getMessage());
            return null;
        }
    }

    private void waitBeforeNextRequest(Page page) {
        double delayMs = REQUEST_DELAY_MS + ThreadLocalRandom.current().nextDouble(REQUEST_DELAY_JITTER_MS);
        log.info("Esperando {} ms antes de la siguiente petición a Eciglogistica", Math.round(delayMs));
        page.waitForTimeout(delayMs);
    }

    private void selectVariante(Page page, String variante) {
        var select = page.locator("select.select-attribute-product");
        select.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(variante));
        log.info("Variante seleccionada: {}", variante);
        page.waitForTimeout(1500);
    }
}
