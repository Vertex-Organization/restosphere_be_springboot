package com.vertex.restosphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RestosphereBeSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestosphereBeSpringbootApplication.class, args);
    }
}
