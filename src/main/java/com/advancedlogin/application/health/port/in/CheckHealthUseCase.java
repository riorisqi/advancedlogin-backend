package com.advancedlogin.application.health.port.in;

import com.advancedlogin.domain.health.SystemHealth;

/**
 * Inbound port: what the outside world (here, a REST controller) may ask the application to do.
 */
public interface CheckHealthUseCase {

    SystemHealth check();
}
