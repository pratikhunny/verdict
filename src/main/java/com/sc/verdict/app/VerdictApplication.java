package com.sc.verdict.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Spring Boot entry point — the L9 experience shell and nothing more. All business logic lives
 * in the framework-free domain core; this package wraps it in a REST API and serves the two-screen
 * UI. The core does not know Spring exists.
 */
@SpringBootApplication
public class VerdictApplication {

    public static void main(String[] args) {
        SpringApplication.run(VerdictApplication.class, args);
    }
}
