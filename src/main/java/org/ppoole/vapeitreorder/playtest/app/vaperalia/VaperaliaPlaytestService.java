package org.ppoole.vapeitreorder.playtest.app.vaperalia;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class VaperaliaPlaytestService {

    private static final Logger log = LoggerFactory.getLogger(VaperaliaPlaytestService.class);
    private static final String SESSION_FILE = "vaperalia-session.json";

    public void saveSession() {
        log.info("Abriendo navegador visible — inicia sesión en Vaperalia y espera...");
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(false))) {

            try (BrowserContext context = browser.newContext()) {
                Page page = context.newPage();
                page.navigate("https://vaperalia.es/autenticacion?back=my-account");
                log.info("Esperando hasta 3 minutos para que inicies sesión...");
                page.waitForURL("**/mi-cuenta**", new Page.WaitForURLOptions().setTimeout(180_000));
                context.storageState(new BrowserContext.StorageStateOptions().setPath(Path.of(SESSION_FILE)));
                log.info("Sesión guardada en {}", Path.of(SESSION_FILE).toAbsolutePath());
            }
        }
    }

    public void scrape(List<String> urls) {
        if (urls.isEmpty()) {
            log.info("No URLs to scrape");
            return;
        }

        var sessionPath = Path.of(SESSION_FILE);

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true))) {

            var contextOptions = new Browser.NewContextOptions();
            if (!Files.exists(sessionPath)) {
                log.error("\n\n!!!! ERROR: No se encontró el fichero de sesión. Ejecuta 'mvn spring-boot:run -Dspring-boot.run.arguments=\"--login\"\n' para guardar la sesión primero !!!!\n");
                return;
            }
            log.info("Cargando sesión desde {}", sessionPath.toAbsolutePath());
            contextOptions.setStorageStatePath(sessionPath);

            try (BrowserContext context = browser.newContext(contextOptions)) {
                Page page = context.newPage();
                if (!validateSession(page)) {
                    log.error("\n\n!!!! ERROR: La sesión de Vaperalia no es válida o ha caducado. Ejecuta 'mvn spring-boot:run -Dspring-boot.run.arguments=\"--login\"\n' para renovarla !!!!\n");
                    return;
                }
                for (String url : urls) {
                    scrapeProduct(page, url);
                }
            }
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

    private void scrapeProduct(Page page, String url) {
        log.info("Scraping {}", url);

        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForLoadState();

        if (page.url().contains("autenticacion")) {
            log.error("Redirigido a login — la sesión no es válida o no se cargó");
            return;
        }

        try {
            String nombre = page.locator("h1[itemprop='name']").textContent().trim();
            Object precio = page.evaluate("() => window.productPrice ?? null");

            log.info("\n────────────────────────────────────────────\n  Producto:  {}\n  Precio:    {} €\n────────────────────────────────────────────", nombre, precio);
        } catch (Exception e) {
            log.error("Error scraping {}: {}", url, e.getMessage());
        }
    }
}
