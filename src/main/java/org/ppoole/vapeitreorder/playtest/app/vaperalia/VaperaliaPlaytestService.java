package org.ppoole.vapeitreorder.playtest.app.vaperalia;

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

@Service
public class VaperaliaPlaytestService {

    private static final Logger log = LoggerFactory.getLogger(VaperaliaPlaytestService.class);

    private final Path sessionPath;

    public VaperaliaPlaytestService(@Value("${vapeit.sessions-dir:sessions}") String sessionsDir) {
        this.sessionPath = Path.of(sessionsDir, "vaperalia-session.json");
    }

    public void saveSession() {
        log.info("Abriendo navegador visible — inicia sesión en Vaperalia y espera...");
        ensureSessionDirectoryExists();

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(false))) {

            try (BrowserContext context = browser.newContext()) {
                Page page = context.newPage();
                page.navigate("https://vaperalia.es/autenticacion?back=my-account");
                log.info("Esperando hasta 5 minutos para que inicies sesión...");
                page.waitForURL("**/mi-cuenta**", new Page.WaitForURLOptions().setTimeout(300_000));
                context.storageState(new BrowserContext.StorageStateOptions().setPath(sessionPath));
                log.info("Sesión guardada en {}", sessionPath.toAbsolutePath());
            }
        }
    }

    public List<ProductoRespuesta> scrape(List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> urls) {

        List<ProductoRespuesta> productoRespuestas = new ArrayList<>();
        if (urls.isEmpty()) {
            log.info("No URLs to scrape");
            return productoRespuestas;
        }

        List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> failedTrios = new java.util.ArrayList<>();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true))) {

            var contextOptions = new Browser.NewContextOptions();
            if (!Files.exists(sessionPath)) {
                String message = "La sesión de Vaperalia no existe. Carga la sesión desde la interfaz antes de ejecutar el reorder.";
                log.error(message);
                throw new PlaytestSessionException(message);
            }
            log.info("Cargando sesión desde {}", sessionPath.toAbsolutePath());
            contextOptions.setStorageStatePath(sessionPath);

            try (BrowserContext context = browser.newContext(contextOptions)) {
                Page page = context.newPage();
                if (!validateSession(page)) {
                    String message = "La sesión de Vaperalia ha caducado o no es válida. Vuelve a cargarla desde la interfaz.";
                    log.error(message);
                    throw new PlaytestSessionException(message);
                }
                for (ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio url : urls) {
                    try {
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
                    log.error("Fallaron {} productos", failedTrios.size());
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
        page.navigate("https://vaperalia.es/mi-cuenta", new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForLoadState();
        if (page.url().contains("autenticacion")) {
            return false;
        }
        log.info("Sesión de Vaperalia válida");
        return true;
    }

    private ProductoRespuesta scrapeProduct(Page page, ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio skuUrl) {
        String url = skuUrl.getUrl();
        log.info("Scraping {} ({})", skuUrl.getSku(), url);

        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForLoadState();

        if (page.url().contains("autenticacion")) {
            String message = "Vaperalia redirigió a login durante el scraping. La sesión ha caducado o no se cargó correctamente.";
            log.error(message);
            throw new PlaytestSessionException(message);
        }

        try {
            String nombre = page.locator("h1[itemprop='name']").textContent().trim();
            var precioLocator = page.locator("#our_price_display");
            precioLocator.waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5000));
            String precioText = precioLocator.textContent().trim().replace("€", "").replace(",", ".").trim();
            Double precioValue = Double.parseDouble(precioText);

            log.info("\n────────────────────────────────────────────\n  Producto:  {}\n  Precio:    {} €\n────────────────────────────────────────────", nombre, precioValue);
            return new ProductoRespuesta(skuUrl.getSku(), nombre, precioValue, skuUrl.getUrl(), skuUrl.getDistribuidoraName());
        } catch (Exception e) {
            log.error("Error scraping {}: {}", url, e.getMessage());
            return null;
        }
    }
}
