package org.ppoole.vapeitreorder.playtest.app.service;

import org.ppoole.vapeitreorder.playtest.app.domain.ProductoPrioridades;
import org.ppoole.vapeitreorder.playtest.app.domain.ProductoRespuesta;
import org.ppoole.vapeitreorder.playtest.app.eciglogistica.EciglogisticaPlaytestService;
import org.ppoole.vapeitreorder.playtest.app.repository.ProductoDistribuidoraRepository;
import org.ppoole.vapeitreorder.playtest.app.vaperalia.VaperaliaPlaytestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ReorderExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ReorderExecutionService.class);
    private static final String VAPERALIA = "VAPERALIA";
    private static final String ECIGLOGISTICA = "ECIGLOGISTICA";

    private final VaperaliaPlaytestService vaperaliaPlaytestService;
    private final EciglogisticaPlaytestService eciglogisticaPlaytestService;
    private final ItemApiClient itemApiClient;
    private final ProductoDistribuidoraRepository productoDistribuidoraRepository;

    public ReorderExecutionService(VaperaliaPlaytestService vaperaliaPlaytestService,
                                   EciglogisticaPlaytestService eciglogisticaPlaytestService,
                                   ItemApiClient itemApiClient,
                                   ProductoDistribuidoraRepository productoDistribuidoraRepository) {
        this.vaperaliaPlaytestService = vaperaliaPlaytestService;
        this.eciglogisticaPlaytestService = eciglogisticaPlaytestService;
        this.itemApiClient = itemApiClient;
        this.productoDistribuidoraRepository = productoDistribuidoraRepository;
    }

    public void saveVaperaliaSession() {
        vaperaliaPlaytestService.saveSession();
    }

    public void saveEciglogisticaSession() {
        eciglogisticaPlaytestService.saveSession();
    }

    public List<ProductoPrioridades> generatePrioridades() {
        var skus = itemApiClient.fetchSkusNeedingReorder();
        if (skus.isEmpty()) {
            log.info("No SKUs need reorder");
            return List.of();
        }

        List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> candidateUrls = fetchCandidateUrls(skus);

        List<ProductoRespuesta> scrapedResults = new ArrayList<>();
        List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> vaperaliaUrls = candidateUrls.stream()
                .filter(trio -> VAPERALIA.equalsIgnoreCase(trio.getDistribuidoraName()))
                .toList();
        scrapedResults.addAll(vaperaliaPlaytestService.scrape(vaperaliaUrls));

        List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> eciglogisticaUrls = candidateUrls.stream()
                .filter(trio -> ECIGLOGISTICA.equalsIgnoreCase(trio.getDistribuidoraName()))
                .toList();
        scrapedResults.addAll(eciglogisticaPlaytestService.scrape(eciglogisticaUrls));

        List<ProductoPrioridades> prioridades = buildProductoPrioridades(scrapedResults);
        logPrioridades(prioridades);
        return prioridades;
    }

    private List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> fetchCandidateUrls(List<String> skus) {
        List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> candidateUrls =
                productoDistribuidoraRepository.findSkuUrlDistribuidoraTriosBySkuIn(skus);
        log.info("Found {} URLs for {} SKUs needing reorder", candidateUrls.size(), skus.size());
        return candidateUrls;
    }

    private List<ProductoPrioridades> buildProductoPrioridades(List<ProductoRespuesta> respuestas) {
        Map<String, List<ProductoRespuesta>> respuestasPorSku = new LinkedHashMap<>();

        for (ProductoRespuesta respuesta : respuestas) {
            if (respuesta == null || respuesta.getSku() == null) {
                continue;
            }
            respuestasPorSku
                    .computeIfAbsent(respuesta.getSku(), ignored -> new ArrayList<>())
                    .add(respuesta);
        }

        List<ProductoPrioridades> prioridades = new ArrayList<>();
        for (Map.Entry<String, List<ProductoRespuesta>> entry : respuestasPorSku.entrySet()) {
            prioridades.add(buildPrioridad(entry.getKey(), entry.getValue()));
        }

        return prioridades;
    }

    private ProductoPrioridades buildPrioridad(String sku, List<ProductoRespuesta> respuestas) {
        List<ProductoRespuesta> respuestasOrdenadas = new ArrayList<>(respuestas);
        respuestasOrdenadas.sort(Comparator.comparing(ProductoRespuesta::getPrecio, Comparator.nullsLast(Double::compareTo)));

        String nombre = respuestasOrdenadas.stream()
                .map(ProductoRespuesta::getNombre)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");

        List<String> urlsOrdenadas = new ArrayList<>();
        List<String> distribuidorasOrdenadas = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        for (ProductoRespuesta respuesta : respuestasOrdenadas) {
            String url = respuesta.getUrl();
            if (url == null || url.isBlank() || !seenUrls.add(url)) {
                continue;
            }
            urlsOrdenadas.add(url);
            distribuidorasOrdenadas.add(respuesta.getDistribuidora());
        }

        return new ProductoPrioridades(sku, nombre, urlsOrdenadas, distribuidorasOrdenadas);
    }

    private void logPrioridades(List<ProductoPrioridades> prioridades) {
        log.info("Generated {} ProductoPrioridades entries", prioridades.size());
        for (ProductoPrioridades prioridad : prioridades) {
            log.info("SKU {} -> {} URLs ordered by cheapest first", prioridad.getSku(), prioridad.getUrls().size());
        }
    }
}
