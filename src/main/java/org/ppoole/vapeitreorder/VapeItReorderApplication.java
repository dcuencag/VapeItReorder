package org.ppoole.vapeitreorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VapeItReorderApplication {

    public static void main(String[] args) {
        SpringApplication.run(VapeItReorderApplication.class, args);
    }
}
