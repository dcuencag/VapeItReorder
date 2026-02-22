package org.ppoole.vapeitreorder.playtest.app;

import org.ppoole.vapeitreorder.playtest.app.vaperalia.VaperaliaPlaytestService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class PlaytestApp {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(PlaytestApp.class, args);
        context.getBean(VaperaliaPlaytestService.class).run(args);
    }
}
