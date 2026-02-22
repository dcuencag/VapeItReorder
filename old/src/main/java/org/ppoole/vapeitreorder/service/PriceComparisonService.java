package org.ppoole.vapeitreorder.service;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.ppoole.vapeitreorder.distributor.DistributorPriceBot;
import org.ppoole.vapeitreorder.dto.ItemDto;
import org.ppoole.vapeitreorder.dto.PriceComparisonDto;
import org.ppoole.vapeitreorder.dto.PriceOptionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PriceComparisonService {

    private static final Logger log = LoggerFactory.getLogger(PriceComparisonService.class);

    private final List<DistributorPriceBot> priceBots;
    private final boolean headless;

    public PriceComparisonService(
            List<DistributorPriceBot> priceBots,
            @Value("${playwright.headless}") boolean headless) {
        this.priceBots = priceBots;
        this.headless = headless;
    }

    public List<PriceComparisonDto> compare(List<ItemDto> items) {
        Map<String, PriceComparisonDto> bysku = new HashMap<>();
        for (ItemDto item : items) {
            var dto = new PriceComparisonDto();
            dto.setSku(item.getSku());
            dto.setNombre(item.getNombre());
            dto.setCantidadAPedir(calculateQuantity(item));
            dto.setOpciones(new ArrayList<>());
            bysku.put(item.getSku(), dto);
        }

        for (DistributorPriceBot bot : priceBots) {
            log.info("Searching prices on {} for {} items", bot.getDistributorName(), items.size());
            try (var playwright = Playwright.create()) {
                try (var browser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions().setHeadless(headless))) {
                    try (var context = browser.newContext(bot.newContextOptions())) {
                        Page page = context.newPage();
                        for (ItemDto item : items) {
                            try {
                                Optional<PriceOptionDto> option = bot.searchProduct(page, item);
                                option.ifPresent(o -> bysku.get(item.getSku()).getOpciones().add(o));
                            } catch (Exception e) {
                                log.error("Price search failed for sku={} on {}: {}", item.getSku(), bot.getDistributorName(), e.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Browser session failed for {}: {}", bot.getDistributorName(), e.getMessage());
            }
        }

        return new ArrayList<>(bysku.values());
    }

    private int calculateQuantity(ItemDto item) {
        if (item.getCantidadAPedir() != null) return item.getCantidadAPedir();
        if (item.getMaximoUnidades() != null && item.getCurrentStock() != null) {
            return item.getMaximoUnidades() - item.getCurrentStock();
        }
        return 0;
    }
}
