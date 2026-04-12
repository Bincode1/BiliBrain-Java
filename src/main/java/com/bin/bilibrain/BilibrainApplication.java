package com.bin.bilibrain;

import com.bin.bilibrain.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.Map;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class BilibrainApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BilibrainApplication.class);
        application.setDefaultProperties(Map.of("spring.profiles.default", "local"));
        application.run(args);
    }

}
