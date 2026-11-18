package com.antifraud.service.settings;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the periodic refresh of settings. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SettingsConfiguration {
}
