package org.ppoole.vapeitreorder.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.ppoole.vapeitreorder.distributor.DistributorBot;
import org.ppoole.vapeitreorder.dto.CartResultDto;
import org.ppoole.vapeitreorder.dto.ItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BotEngine {

    private static final Logger log = LoggerFactory.getLogger(BotEngine.class);

    private final List<DistributorBot> bots;
    private final boolean headless;

    public BotEngine(List<DistributorBot> bots, @Value("${playwright.headless}") boolean headless) {
        this.bots = bots;
        this.headless = headless;
    }

    public List<CartResultDto> processOrders(List<ItemDto> items) {
        Map<String, List<ItemDto>> byDistributor = items.stream()
                .collect(Collectors.groupingBy(item -> {
                    if (item.getDistribuidor() != null) return item.getDistribuidor();
                    return inferDistributor(item.getSupplierUrl());
                }));

        var results = new ArrayList<CartResultDto>();

        for (Map.Entry<String, List<ItemDto>> entry : byDistributor.entrySet()) {
            String distributorName = entry.getKey();
            List<ItemDto> distributorItems = entry.getValue();

            DistributorBot bot = bots.stream()
                    .filter(b -> b.getDistributorName().equals(distributorName))
                    .findFirst()
                    .orElse(null);

            if (bot == null) {
                log.warn("No bot found for distributor: {}", distributorName);
                var error = new CartResultDto();
                error.setDistribuidor(distributorName);
                error.setTimestamp(Instant.now().toString());
                error.setStatus("error");
                error.setCarrito(List.of());
                error.setErrores(List.of("No bot configured for distributor: " + distributorName));
                results.add(error);
                continue;
            }

            log.info("Running bot for {} with {} items", distributorName, distributorItems.size());
            try (var playwright = Playwright.create()) {
                try (Browser browser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions().setHeadless(headless))) {
                    Page page = browser.newPage();
                    var result = bot.run(page, distributorItems);
                    results.add(result);
                    log.info("Cart result for {}: status={}, items={}", distributorName,
                            result.getStatus(), result.getCarrito() != null ? result.getCarrito().size() : 0);
                }
            } catch (Exception e) {
                log.error("Browser session failed for {}: {}", distributorName, e.getMessage());
                var error = new CartResultDto();
                error.setDistribuidor(distributorName);
                error.setTimestamp(Instant.now().toString());
                error.setStatus("error");
                error.setCarrito(List.of());
                error.setErrores(List.of("Browser session failed: " + e.getMessage()));
                results.add(error);
            }
        }

        return results;
    }

    private String inferDistributor(String supplierUrl) {
        if (supplierUrl == null) return "unknown";
        try {
            var uri = URI.create(supplierUrl);
            String host = uri.getHost();
            for (DistributorBot bot : bots) {
                if (host != null && host.contains(bot.getDistributorName())) {
                    return bot.getDistributorName();
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse supplier URL: {}", supplierUrl);
        }
        return "unknown";
    }
}
