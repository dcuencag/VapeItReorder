package org.ppoole.vapeitreorder.playtest.app;

import org.ppoole.vapeitreorder.playtest.app.vaperalia.VaperaliaPlaytestService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EntityScan("org.ppoole.vapeitreorder.playtest.app.domain")
public class PlaytestApp {

    public static void main(String[] args) {
        SpringApplication.run(PlaytestApp.class, args);
    }

    @Bean
    ApplicationRunner vaperaliaRunner(VaperaliaPlaytestService vaperaliaPlaytestService) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                vaperaliaPlaytestService.run(args.getSourceArgs());
            }
        };
    }
}
