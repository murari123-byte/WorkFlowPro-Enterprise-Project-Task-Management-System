package com.workflowpro.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.workflowpro.common.client.UserDirectoryClient;

@Configuration
public class ClientConfig {

    @Bean
    UserDirectoryClient userDirectoryClient(ServiceUrlsProperties urls) {
        return new UserDirectoryClient(urls.authUrl());
    }
}
