package com.workflowpro.auth.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A Clock bean lets tests control "now" (e.g. to test token expiry). */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
