package org.ppoole.vapeitreorder.playtest.app;

import org.ppoole.vapeitreorder.playtest.app.domain.ProductoRespuesta;
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

import java.util.List;

@SpringBootApplication
@EntityScan("org.ppoole.vapeitreorder.playtest.app.domain")
public class PlaytestApp {

    private static final Logger log = LoggerFactory.getLogger(PlaytestApp.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(PlaytestApp.class, args);
        context.close();
    }

    @Bean
    ApplicationRunner vaperaliaRunner(VaperaliaPlaytestService vaperaliaPlaytestService,
                                      ItemApiClient itemApiClient,
                                      ProductoDistribuidoraRepository productoDistribuidoraRepository) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                if (args.getSourceArgs().length > 0 && "--login".equals(args.getSourceArgs()[0])) {
                    vaperaliaPlaytestService.saveSession();
                    return;
                }

                var skus = itemApiClient.fetchSkusNeedingReorder();
                if (skus.isEmpty()) {
                    log.info("No SKUs need reorder");
                    return;
                }
                List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> urls = productoDistribuidoraRepository.findSkuUrlDistribuidoraTriosBySkuIn(skus);




                //Para todos los trio que sean de vaperalia:
                log.info("Found {} URLs for {} SKUs needing reorder", urls.size(), skus.size());
                List<ProductoDistribuidoraRepository.SkuUrlDistribuidoraTrio> vaperaliaTrios = urls.stream()
                        .filter(trio -> "VAPERALIA".equalsIgnoreCase(trio.getDistribuidoraName()))
                        .toList();

                List<ProductoRespuesta> productoRespuestas = vaperaliaPlaytestService.scrape(vaperaliaTrios);


                //Para todos los pair(trio) que sean de eciglogistica
                //List...
            }
        };
    }
}
