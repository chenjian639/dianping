package com.dianping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DianpingApplication {
    public static void main(String[] args) {
        SpringApplication.run(DianpingApplication.class, args);
    }
}