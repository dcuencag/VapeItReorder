package org.ppoole.vapeitreorder.playtest.app.vaperalia;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class VaperaliaPlaytestService {

    private static final String SESSION_FILE = "vaperalia-session.json";

    // Añade aquí las URLs de productos que quieras inspeccionar
    private static final List<String> PRODUCT_URLS = List.of(
            "https://vaperalia.es/bombo/don-juan-supra-reserve-10ml-kings-crest-salts-bombo-12689.html",
            "https://vaperalia.es/vaporesso/xros-5-mini-1500mah-vaporesso-14652.html",
            "https://vaperalia.es/pilas-para-mods/pila-golisi-l35-imr-18650-3500-mah-10a-10409.html"
    );

    public void run(String[] args) {
        boolean loginMode = args.length > 0 && "--login".equals(args[0]);
        if (loginMode) {
            saveSession();
            return;
        }
        scrape();
    }

    public void saveSession() {
        System.out.println("[login] Abriendo navegador visible — inicia sesión en Vaperalia y espera...");
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(false))) {

            try (BrowserContext context = browser.newContext()) {
                Page page = context.newPage();
                page.navigate("https://vaperalia.es/autenticacion?back=my-account");
                System.out.println("[login] Esperando hasta 3 minutos para que inicies sesión...");
                page.waitForURL("**/mi-cuenta**", new Page.WaitForURLOptions().setTimeout(180_000));
                context.storageState(new BrowserContext.StorageStateOptions().setPath(Path.of(SESSION_FILE)));
                System.out.println("[login] Sesión guardada en " + Path.of(SESSION_FILE).toAbsolutePath());
            }
        }
    }

    public void scrape() {
        var sessionPath = Path.of(SESSION_FILE);

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true))) {

            var contextOptions = new Browser.NewContextOptions();
            if (Files.exists(sessionPath)) {
                System.out.println("[session] Cargando sesión desde " + sessionPath.toAbsolutePath());
                contextOptions.setStorageStatePath(sessionPath);
            } else {
                System.out.println("[session] No se encontró " + sessionPath.toAbsolutePath());
                System.out.println("[session] Ejecuta con --login para guardar la sesión primero");
            }

            try (BrowserContext context = browser.newContext(contextOptions)) {
                Page page = context.newPage();
                for (String url : PRODUCT_URLS) {
                    scrapeProduct(page, url);
                }
            }
        }
    }

    private void scrapeProduct(Page page, String url) {
        System.out.println("\n========================================");
        System.out.println("URL: " + url);
        System.out.println("========================================");

        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForLoadState();

        if (page.url().contains("autenticacion")) {
            System.out.println("[error] Redirigido a login — la sesión no es válida o no se cargó");
            return;
        }

        try {
            // PrestaShop guarda el precio en variables JS globales, no en el DOM
            String nombre = page.locator("h1[itemprop='name']").textContent().trim();
            Object precio = page.evaluate("() => window.productPrice ?? null");


            // Hay que contemplar como aplicamos los impuestos especiales, que no se muestran en el precio final
            // pero sí afectan al precio base
            System.out.println("Nombre:  " + nombre);
            System.out.println("Precio solo con impuesto especial:  " + precio + " €");
        } catch (Exception e) {
            System.out.println("[error] " + e.getMessage());
        }
    }

}
