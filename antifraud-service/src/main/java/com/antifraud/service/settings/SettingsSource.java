package com.antifraud.service.settings;

/** Where the decision engine takes the settings from; a lambda is enough in tests. */
@FunctionalInterface
public interface SettingsSource {

    SettingsSnapshot current();
}
