package org.ppoole.vapeitreorder.controller;

import org.ppoole.vapeitreorder.dto.CartResultDto;
import org.ppoole.vapeitreorder.dto.ItemDto;
import org.ppoole.vapeitreorder.dto.OrderSelectionDto;
import org.ppoole.vapeitreorder.dto.PriceComparisonDto;
import org.ppoole.vapeitreorder.service.BotEngine;
import org.ppoole.vapeitreorder.service.ItemApiClient;
import org.ppoole.vapeitreorder.service.PriceComparisonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
public class ComparisonController {

    private static final Logger log = LoggerFactory.getLogger(ComparisonController.class);

    private final ItemApiClient itemApiClient;
    private final PriceComparisonService priceComparisonService;
    private final BotEngine botEngine;

    public ComparisonController(
            ItemApiClient itemApiClient,
            PriceComparisonService priceComparisonService,
            BotEngine botEngine) {
        this.itemApiClient = itemApiClient;
        this.priceComparisonService = priceComparisonService;
        this.botEngine = botEngine;
    }

    @PostMapping("/compare")
    public ResponseEntity<List<PriceComparisonDto>> compare() {
        var allItems = itemApiClient.fetchAllItems();
        var itemsNeedingReorder = allItems.stream().filter(ItemDto::needsReorder).toList();
        log.info("Comparing prices for {} items needing reorder", itemsNeedingReorder.size());
        var comparisons = priceComparisonService.compare(itemsNeedingReorder);
        return ResponseEntity.ok(comparisons);
    }

    @PostMapping("/order")
    public ResponseEntity<List<CartResultDto>> order(@RequestBody List<OrderSelectionDto> selections) {
        var allItems = itemApiClient.fetchAllItems();
        Map<String, ItemDto> itemsBySku = allItems.stream()
                .collect(Collectors.toMap(ItemDto::getSku, Function.identity(), (a, b) -> a));

        var itemsToOrder = selections.stream()
                .map(sel -> {
                    ItemDto item = itemsBySku.get(sel.getSku());
                    if (item == null) {
                        log.warn("SKU not found in VapeIt API: {}", sel.getSku());
                        return null;
                    }
                    item.setDistribuidor(sel.getDistribuidor());
                    if (sel.getCantidadAPedir() != null) {
                        item.setCantidadAPedir(sel.getCantidadAPedir());
                    }
                    return item;
                })
                .filter(item -> item != null)
                .toList();

        log.info("Processing orders for {} items", itemsToOrder.size());
        var results = botEngine.processOrders(itemsToOrder);
        return ResponseEntity.ok(results);
    }
}
