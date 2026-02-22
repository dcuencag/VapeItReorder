package org.ppoole.vapeitreorder.playtest.app.service;

import org.ppoole.vapeitreorder.playtest.app.dto.ItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class ItemApiClient {

    private static final Logger log = LoggerFactory.getLogger(ItemApiClient.class);

    private final RestTemplate restTemplate;
    private final String apiUrl;

    public ItemApiClient(RestTemplate restTemplate, @Value("${vapeit.api-url}") String apiUrl) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
    }

    public List<String> fetchSkusNeedingReorder() {
        log.info("Fetching items from {}/api/items", apiUrl);
        var response = restTemplate.exchange(
                apiUrl + "/api/items",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ItemDto>>() {}
        );
        var items = response.getBody();
        if (items == null) {
            log.warn("No items returned from API");
            return List.of();
        }
        var skus = items.stream()
                .filter(ItemDto::needsReorder)
                .map(ItemDto::getSku)
                .toList();
        log.info("Found {} SKUs needing reorder out of {} total items", skus.size(), items.size());
        return skus;
    }
}
