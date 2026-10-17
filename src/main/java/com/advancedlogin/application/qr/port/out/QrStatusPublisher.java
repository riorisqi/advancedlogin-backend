package com.advancedlogin.application.qr.port.out;

import com.advancedlogin.domain.qr.QrStatusUpdate;

/** Fire-and-forget: a lost update is recovered on the next (re)connect, which starts with a snapshot. */
public interface QrStatusPublisher {

    void publish(QrStatusUpdate update);
}
