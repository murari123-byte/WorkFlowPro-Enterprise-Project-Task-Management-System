package com.workflowpro.task.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.workflowpro.common.client.UserDirectoryClient;

@Configuration
public class ClientConfig {

    @Bean
    UserDirectoryClient userDirectoryClient(ServiceUrlsProperties urls) {
        return new UserDirectoryClient(urls.authUrl());
    }

    /** "Today" for overdue checks is the UTC date. A Clock bean lets tests fix the date. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
