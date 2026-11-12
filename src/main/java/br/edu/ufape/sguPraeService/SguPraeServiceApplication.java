package br.edu.ufape.sguPraeService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@SpringBootApplication
@EnableAsync
@EnableWebSecurity
@EnableScheduling
@EnableMethodSecurity(jsr250Enabled = true, securedEnabled = true)
public class SguPraeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SguPraeServiceApplication.class, args);
    }

}
