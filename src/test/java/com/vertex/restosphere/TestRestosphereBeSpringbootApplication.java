package com.vertex.restosphere;

import org.springframework.boot.SpringApplication;

public class TestRestosphereBeSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.from(RestosphereBeSpringbootApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
