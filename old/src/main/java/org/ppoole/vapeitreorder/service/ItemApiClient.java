package org.ppoole.vapeitreorder.service;

import org.ppoole.vapeitreorder.dto.ItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
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

    public List<ItemDto> fetchAllItems() {
        try {
            log.info("Fetching items from {}", apiUrl);
            var response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<ItemDto>>() {}
            );
            List<ItemDto> items = response.getBody();
            log.info("Fetched {} items", items != null ? items.size() : 0);
            return items != null ? items : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch items from VapeIt API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
