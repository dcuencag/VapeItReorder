package org.ppoole.vapeitreorder.service;

import org.ppoole.vapeitreorder.dto.CartResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class OrderReporter {

    private static final Logger log = LoggerFactory.getLogger(OrderReporter.class);

    private final RestTemplate restTemplate;
    private final String reportUrl;

    public OrderReporter(RestTemplate restTemplate, @Value("${vapeit.report-url}") String reportUrl) {
        this.restTemplate = restTemplate;
        this.reportUrl = reportUrl;
    }

    public void reportResults(List<CartResultDto> results) {
        for (CartResultDto result : results) {
            try {
                restTemplate.postForEntity(reportUrl, result, Void.class);
                log.info("Reported result for {}: {}", result.getDistribuidor(), result.getStatus());
            } catch (Exception e) {
                log.error("Failed to report result for {}: {}", result.getDistribuidor(), e.getMessage());
            }
        }
    }
}
