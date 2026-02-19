package org.ppoole.vapeitreorder.playtest;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;

public class VaperaliaPlaytest {

    public static void main(String[] args) {
        String url = "https://vaperalia.es/alquimia-para-vapers/aroma-delirio-10ml-alquimia-para-vapers-14726.html";

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            String[] htmls = new String[]{page.content()};
            printDisplayPrices(browser, htmls);
        }
    }

    private static void printDisplayPrices(Browser browser, String[] htmls) {
        for (String html : htmls) {
            Page htmlPage = browser.newPage();
            try {
                htmlPage.setContent(html);
                Object displayPrice = htmlPage.evaluate("() => globalThis.displayPrice ?? null");
                System.out.println(displayPrice);
            } finally {
                htmlPage.close();
            }
        }
    }
}
