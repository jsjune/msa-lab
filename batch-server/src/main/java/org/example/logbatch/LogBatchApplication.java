package org.example.logbatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LogBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogBatchApplication.class, args);
    }
}
