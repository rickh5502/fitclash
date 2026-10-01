// File: src/main/java/com/fitclash/FitClashApplication.java
package com.fitclash;

import com.fitclash.config.GameProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GameProperties.class)
public class FitClashApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitClashApplication.class, args);
    }
}
