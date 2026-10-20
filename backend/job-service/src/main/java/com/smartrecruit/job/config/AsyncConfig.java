package com.smartrecruit.job.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables {@code @Async}, used for best-effort calls that must not hold the request thread. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
