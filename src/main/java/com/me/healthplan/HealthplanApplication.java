package com.me.healthplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.me.healthplan*" )
public class HealthplanApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthplanApplication.class, args);
    }
}
