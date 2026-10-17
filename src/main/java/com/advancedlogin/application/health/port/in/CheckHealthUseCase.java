package com.advancedlogin.application.health.port.in;

import com.advancedlogin.domain.health.SystemHealth;

public interface CheckHealthUseCase {

    SystemHealth check();
}
