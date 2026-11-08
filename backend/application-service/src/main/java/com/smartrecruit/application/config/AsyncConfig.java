package com.smartrecruit.application.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Background work (match scoring, audit events) runs on Spring Boot's application task executor. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
