package org.ppoole.vapeitreorder.playtest.app;

import org.ppoole.vapeitreorder.playtest.app.domain.ProductoPrioridades;
import org.ppoole.vapeitreorder.playtest.app.domain.ProductoRespuesta;
import org.ppoole.vapeitreorder.playtest.app.eciglogistica.EciglogisticaPlaytestService;
import org.ppoole.vapeitreorder.playtest.app.repository.ProductoDistribuidoraRepository;
import org.ppoole.vapeitreorder.playtest.app.service.ItemApiClient;
import org.ppoole.vapeitreorder.playtest.app.vaperalia.VaperaliaPlaytestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SpringBootApplication
@EntityScan("org.ppoole.vapeitreorder.playtest.app.domain")
public class PlaytestApp {

    private static final Logger log = LoggerFactory.getLogger(PlaytestApp.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(PlaytestApp.class, args);
        context.close();
    }

    @Bean
    ApplicationRunner runner(VaperaliaPlaytestService vaperaliaPlaytestService,
                             EciglogisticaPlaytestService eciglogisticaPlaytestService,
                             ItemApiClient itemApiClient,
                             ProductoDistribuidoraRepository productoDistribuidoraRepository) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                if (args.getSourceArgs().length > 0 && "--login".equals(args.getSourceArgs()[0])) {
                    vaperaliaPlaytestService.saveSession();
                    return;
                }
                if (args.getSourceArgs().length > 0 && "--login-ecig".equals(args.getSourceArgs()[0])) {
                    eciglogisticaPlaytestService.saveSession();
                    return;
                }

                var skus = itemApiClient.fetchSkusNeedingReorder();
                if (skus.isEmpty()) {
                    log.info("No SKUs need reorder");
                    return;
                }
                List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> urls = productoDistribuidoraRepository.findSkuUrlDistribuidoraTriosBySkuIn(skus);
                log.info("Found {} URLs for {} SKUs needing reorder", urls.size(), skus.size());

                List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> vaperaliaTrios = urls.stream()
                        .filter(trio -> "VAPERALIA".equalsIgnoreCase(trio.getDistribuidoraName()))
                        .toList();
                List<ProductoRespuesta> vaperaliaResults = vaperaliaPlaytestService.scrape(vaperaliaTrios);

                List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> eciglogisticaTrios = urls.stream()
                        .filter(trio -> "ECIGLOGISTICA".equalsIgnoreCase(trio.getDistribuidoraName()))
                        .toList();
                List<ProductoRespuesta> ecigResults = eciglogisticaPlaytestService.scrape(eciglogisticaTrios);

                List<ProductoPrioridades> productoPrioridades = buildProductoPrioridades(List.of(vaperaliaResults, ecigResults));
                log.info("Generated {} ProductoPrioridades entries", productoPrioridades.size());
                for (ProductoPrioridades prioridad : productoPrioridades) {
                    log.info("SKU {} -> {} URLs ordered by cheapest first", prioridad.getSku(), prioridad.getUrls().size());
                }

            }
        };
    }

    private List<ProductoPrioridades> buildProductoPrioridades(List<List<ProductoRespuesta>> resultadosPorDistribuidora) {
        Map<String, List<ProductoRespuesta>> respuestasPorSku = new LinkedHashMap<>();

        for (List<ProductoRespuesta> resultadosDistribuidora : resultadosPorDistribuidora) {
            for (ProductoRespuesta respuesta : resultadosDistribuidora) {
                if (respuesta == null || respuesta.getSku() == null) {
                    continue;
                }
                respuestasPorSku
                        .computeIfAbsent(respuesta.getSku(), ignored -> new ArrayList<>())
                        .add(respuesta);
            }
        }

        List<ProductoPrioridades> prioridades = new ArrayList<>();
        for (Map.Entry<String, List<ProductoRespuesta>> entry : respuestasPorSku.entrySet()) {
            List<ProductoRespuesta> respuestasSku = new ArrayList<>(entry.getValue());
            respuestasSku.sort(Comparator.comparing(ProductoRespuesta::getPrecio, Comparator.nullsLast(Double::compareTo)));

            String nombre = respuestasSku.stream()
                    .map(ProductoRespuesta::getNombre)
                    .filter(Objects::nonNull)
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse("");

            List<String> urlsOrdenadas = respuestasSku.stream()
                    .map(ProductoRespuesta::getUrl)
                    .filter(Objects::nonNull)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();

            prioridades.add(new ProductoPrioridades(entry.getKey(), nombre, urlsOrdenadas));
        }

        return prioridades;
    }
}
