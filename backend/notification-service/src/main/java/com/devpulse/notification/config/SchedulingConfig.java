package com.devpulse.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled}, which the stale pull request check runs on. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
