package org.ppoole.vapeitreorder.service;

import org.ppoole.vapeitreorder.dto.CartResultDto;
import org.ppoole.vapeitreorder.dto.ItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReorderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReorderScheduler.class);

    private final ItemApiClient itemApiClient;
    private final BotEngine botEngine;
    private final OrderReporter orderReporter;

    public ReorderScheduler(ItemApiClient itemApiClient, BotEngine botEngine, OrderReporter orderReporter) {
        this.itemApiClient = itemApiClient;
        this.botEngine = botEngine;
        this.orderReporter = orderReporter;
    }

    @Scheduled(cron = "${reorder.cron}")
    public void checkAndReorder() {
        log.info("Reorder check started");

        List<ItemDto> allItems = itemApiClient.fetchAllItems();
        if (allItems.isEmpty()) {
            log.info("No items fetched, skipping reorder check");
            return;
        }

        List<ItemDto> itemsToReorder = allItems.stream()
                .filter(ItemDto::needsReorder)
                .toList();

        log.info("Found {} items needing reorder out of {} total", itemsToReorder.size(), allItems.size());

        if (itemsToReorder.isEmpty()) {
            log.info("No items need reordering");
            return;
        }

        List<CartResultDto> results = botEngine.processOrders(itemsToReorder);
        orderReporter.reportResults(results);

        log.info("Reorder check completed");
    }
}
