package pl.edu.pk.pkampus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PKampusApplication {

    public static void main(String[] args) {
        SpringApplication.run(PKampusApplication.class, args);
    }
}
