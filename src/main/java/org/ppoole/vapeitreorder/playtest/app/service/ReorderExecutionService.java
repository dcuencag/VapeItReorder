package org.ppoole.vapeitreorder.playtest.app.service;

import org.ppoole.vapeitreorder.playtest.app.domain.ProductoPrioridades;
import org.ppoole.vapeitreorder.playtest.app.domain.ProductoRespuesta;
import org.ppoole.vapeitreorder.playtest.app.eciglogistica.EciglogisticaAddToCarritoService;
import org.ppoole.vapeitreorder.playtest.app.eciglogistica.EciglogisticaFetchPriceService;
import org.ppoole.vapeitreorder.playtest.app.repository.ProductoDistribuidoraRepository;
import org.ppoole.vapeitreorder.playtest.app.vaperalia.VaperaliaAddToCarritoService;
import org.ppoole.vapeitreorder.playtest.app.vaperalia.VaperaliaFetchPriceService;
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

    private final VaperaliaFetchPriceService vaperaliaFetchPriceService;
    private final VaperaliaAddToCarritoService vaperaliaAddToCarritoService;
    private final EciglogisticaAddToCarritoService eciglogisticaAddToCarritoService;
    private final EciglogisticaFetchPriceService eciglogisticaFetchPriceService;
    private final ItemApiClient itemApiClient;
    private final ProductoDistribuidoraRepository productoDistribuidoraRepository;

    public ReorderExecutionService(VaperaliaFetchPriceService vaperaliaFetchPriceService,
                                   VaperaliaAddToCarritoService vaperaliaAddToCarritoService,
                                   EciglogisticaAddToCarritoService eciglogisticaAddToCarritoService,
                                   EciglogisticaFetchPriceService eciglogisticaFetchPriceService,
                                   ItemApiClient itemApiClient,
                                   ProductoDistribuidoraRepository productoDistribuidoraRepository) {
        this.vaperaliaFetchPriceService = vaperaliaFetchPriceService;
        this.vaperaliaAddToCarritoService = vaperaliaAddToCarritoService;
        this.eciglogisticaAddToCarritoService = eciglogisticaAddToCarritoService;
        this.eciglogisticaFetchPriceService = eciglogisticaFetchPriceService;
        this.itemApiClient = itemApiClient;
        this.productoDistribuidoraRepository = productoDistribuidoraRepository;
    }

    public void saveVaperaliaSession() {
        vaperaliaFetchPriceService.saveSession();
    }

    public void saveEciglogisticaSession() {
        eciglogisticaFetchPriceService.saveSession();
    }

    public AddToCarritoBatchResult addVaperaliaUrlsToCarrito(List<AddToCarritoItemRequest> items) {
        List<VaperaliaAddToCarritoService.AddToCarritoItemRequest> mappedItems = items == null
                ? List.of()
                : items.stream()
                .map(item -> new VaperaliaAddToCarritoService.AddToCarritoItemRequest(item.url(), item.cantidad()))
                .toList();
        VaperaliaAddToCarritoService.AddToCarritoBatchResult result =
                vaperaliaAddToCarritoService.addToCarritoBatch(mappedItems);
        List<UrlFailure> failedUrls = result.failedUrls().stream()
                .map(failure -> new UrlFailure(failure.url(), failure.message()))
                .toList();
        return new AddToCarritoBatchResult(result.addedUrls(), failedUrls);
    }

    public AddToCarritoBatchResult addEciglogisticaUrlsToCarrito(List<AddToCarritoItemRequest> items) {
        List<EciglogisticaAddToCarritoService.AddToCarritoItemRequest> mappedItems = items == null
                ? List.of()
                : items.stream()
                .map(item -> new EciglogisticaAddToCarritoService.AddToCarritoItemRequest(
                        item.url(),
                        item.cantidad(),
                        item.variante()))
                .toList();
        EciglogisticaAddToCarritoService.AddToCarritoBatchResult result =
                eciglogisticaAddToCarritoService.addToCarritoBatch(mappedItems);
        List<UrlFailure> failedUrls = result.failedUrls().stream()
                .map(failure -> new UrlFailure(failure.url(), failure.message()))
                .toList();
        return new AddToCarritoBatchResult(result.addedUrls(), failedUrls);
    }

    public List<ProductoPrioridades> generatePrioridades() {
        Map<String, Integer> reorderUnitsBySku = itemApiClient.fetchReorderUnitsBySku();
        if (reorderUnitsBySku.isEmpty()) {
            log.info("No SKUs need reorder");
            return List.of();
        }

        List<String> skus = new ArrayList<>(reorderUnitsBySku.keySet());
        List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> candidateUrls = fetchCandidateUrls(skus);

        List<ProductoRespuesta> scrapedResults = new ArrayList<>();
        List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> vaperaliaUrls = candidateUrls.stream()
                .filter(trio -> VAPERALIA.equalsIgnoreCase(trio.getDistribuidoraName()))
                .toList();
        scrapedResults.addAll(vaperaliaFetchPriceService.scrape(vaperaliaUrls));

        List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> eciglogisticaUrls = candidateUrls.stream()
                .filter(trio -> ECIGLOGISTICA.equalsIgnoreCase(trio.getDistribuidoraName()))
                .toList();
        scrapedResults.addAll(eciglogisticaFetchPriceService.scrape(eciglogisticaUrls));

        List<ProductoPrioridades> prioridades = buildProductoPrioridades(scrapedResults, reorderUnitsBySku);
        logPrioridades(prioridades);
        return prioridades;
    }

    private List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> fetchCandidateUrls(List<String> skus) {
        List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> candidateUrls =
                productoDistribuidoraRepository.findSkuUrlDistribuidoraTriosBySkuIn(skus);
        log.info("Found {} URLs for {} SKUs needing reorder", candidateUrls.size(), skus.size());
        return candidateUrls;
    }

    private List<ProductoPrioridades> buildProductoPrioridades(List<ProductoRespuesta> respuestas,
                                                               Map<String, Integer> reorderUnitsBySku) {
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
            prioridades.add(buildPrioridad(entry.getKey(), entry.getValue(), reorderUnitsBySku));
        }

        return prioridades;
    }

    private ProductoPrioridades buildPrioridad(String sku,
                                               List<ProductoRespuesta> respuestas,
                                               Map<String, Integer> reorderUnitsBySku) {
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

        int cantidadComprar = reorderUnitsBySku.getOrDefault(sku, 0);
        return new ProductoPrioridades(sku, nombre, cantidadComprar, urlsOrdenadas, distribuidorasOrdenadas);
    }

    private void logPrioridades(List<ProductoPrioridades> prioridades) {
        log.info("Generated {} ProductoPrioridades entries", prioridades.size());
        for (ProductoPrioridades prioridad : prioridades) {
            log.info("SKU {} -> {} URLs ordered by cheapest first", prioridad.getSku(), prioridad.getUrls().size());
        }
    }

    public record AddToCarritoBatchResult(List<String> addedUrls, List<UrlFailure> failedUrls) {
        public AddToCarritoBatchResult {
            addedUrls = addedUrls == null ? List.of() : List.copyOf(addedUrls);
            failedUrls = failedUrls == null ? List.of() : List.copyOf(failedUrls);
        }
    }

    public record AddToCarritoItemRequest(String url, int cantidad, String variante) {}

    public record UrlFailure(String url, String message) {}
}
