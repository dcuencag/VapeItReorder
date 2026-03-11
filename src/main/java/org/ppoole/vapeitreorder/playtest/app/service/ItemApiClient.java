package org.ppoole.vapeitreorder.playtest.app.service;

import org.ppoole.vapeitreorder.playtest.app.dto.ItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ItemApiClient {

    private static final Logger log = LoggerFactory.getLogger(ItemApiClient.class);

    private final RestTemplate restTemplate;
    private final String apiUrl;

    public ItemApiClient(RestTemplate restTemplate, @Value("${vapeit.api-url}") String apiUrl) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
    }

    public Map<String, Integer> fetchReorderUnitsBySku() {
        log.info("Fetching reorder candidates from {}/api/items/idBajoMinimos", apiUrl);
        var response = restTemplate.exchange(
                apiUrl + "/api/items/idBajoMinimos",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ItemDto>>() {}
        );
        var items = response.getBody();
        if (items == null) {
            log.warn("No items returned from API");
            return Map.of();
        }

        Map<String, Integer> reorderUnitsBySku = new LinkedHashMap<>();
        for (ItemDto item : items) {
            String sku = item.getSku();
            if (sku == null || sku.isBlank()) {
                continue;
            }

            int unitsToBuy = Math.max(0, item.getMaximoUnidades() - item.getUnidadesActuales());
            Integer existingValue = reorderUnitsBySku.get(sku);
            if (existingValue != null) {
                int mergedValue = Math.max(existingValue, unitsToBuy);
                if (mergedValue != existingValue) {
                    log.warn("Duplicate SKU {} in reorder payload. Using max unitsToBuy={} (previous={})",
                            sku, mergedValue, existingValue);
                }
                reorderUnitsBySku.put(sku, mergedValue);
                continue;
            }

            reorderUnitsBySku.put(sku, unitsToBuy);
        }

        log.info("Received {} SKU reorder candidates", reorderUnitsBySku.size());
        return reorderUnitsBySku;
    }
}
