package org.ppoole.vapeitreorder.playtest.app.eciglogistica;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.ppoole.vapeitreorder.playtest.app.domain.ProductoRespuesta;
import org.ppoole.vapeitreorder.playtest.app.repository.ProductoDistribuidoraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class EciglogisticaPlaytestService {

    private static final Logger log = LoggerFactory.getLogger(EciglogisticaPlaytestService.class);
    private static final String SESSION_FILE = "eciglogistica-session.json";

    public void saveSession() {
        log.info("Abriendo navegador visible — inicia sesión en Eciglogistica y espera...");
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(false))) {

            try (BrowserContext context = browser.newContext()) {
                Page page = context.newPage();
                page.navigate("https://nueva.eciglogistica.com/entrar");
                log.info("Esperando hasta 5 minutos para que inicies sesión...");
                page.waitForURL(url -> !url.contains("entrar"), new Page.WaitForURLOptions().setTimeout(300_000));
                context.storageState(new BrowserContext.StorageStateOptions().setPath(Path.of(SESSION_FILE)));
                log.info("Sesión guardada en {}", Path.of(SESSION_FILE).toAbsolutePath());
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
        var sessionPath = Path.of(SESSION_FILE);

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true))) {

            var contextOptions = new Browser.NewContextOptions();
            if (!Files.exists(sessionPath)) {
                log.error("\n\n!!!! ERROR: No se encontró el fichero de sesión. Ejecuta 'mvn spring-boot:run -Dspring-boot.run.arguments=\"--login-ecig\"\n' para guardar la sesión primero !!!!\n");
                return productoRespuestas;
            }
            log.info("Cargando sesión desde {}", sessionPath.toAbsolutePath());
            contextOptions.setStorageStatePath(sessionPath);

            try (BrowserContext context = browser.newContext(contextOptions)) {
                Page page = context.newPage();
                if (!validateSession(page)) {
                    log.error("\n\n!!!! ERROR: La sesión de Eciglogistica no es válida o ha caducado. Ejecuta 'mvn spring-boot:run -Dspring-boot.run.arguments=\"--login-ecig\"\n' para renovarla !!!!\n");
                    return productoRespuestas;
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
        log.info("Scraping {} ({})", skuUrl.getSku(), url);

        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForLoadState();

        if (page.url().contains("entrar")) {
            log.error("Redirigido a login — la sesión no es válida o no se cargó");
            return null;
        }

        try {
            String nombre = page.locator("h1").first().textContent().trim();
            String precioText = page.locator("h6.product-price").textContent().trim().replace("€", "").trim();
            Double precioValue = Double.parseDouble(precioText);

            log.info("\n────────────────────────────────────────────\n  Producto:  {}\n  Precio:    {} €\n────────────────────────────────────────────", nombre, precioValue);
            return new ProductoRespuesta(skuUrl.getSku(), nombre, precioValue, skuUrl.getUrl(), skuUrl.getDistribuidoraName());
        } catch (Exception e) {
            log.error("Error scraping {}: {}", url, e.getMessage());
            return null;
        }
    }
}
