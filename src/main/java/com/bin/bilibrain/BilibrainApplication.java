package com.bin.bilibrain;

import com.bin.bilibrain.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class BilibrainApplication {

    public static void main(String[] args) {
        SpringApplication.run(BilibrainApplication.class, args);
    }

}
