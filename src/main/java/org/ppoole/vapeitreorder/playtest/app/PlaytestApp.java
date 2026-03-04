package org.ppoole.vapeitreorder.playtest.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan("org.ppoole.vapeitreorder.playtest.app.domain")
public class PlaytestApp {

    public static void main(String[] args) {
        SpringApplication.run(PlaytestApp.class, args);
    }
}
