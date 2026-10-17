package com.advancedlogin.application.qr.port.out;

import com.advancedlogin.domain.qr.QrStatusUpdate;

/**
 * Tells every interested listener, on <em>any</em> backend instance, that a QR session changed.
 *
 * <p>Fire-and-forget: if delivery fails, the desktop still catches up on its next (re)connect,
 * because every stream starts with a fresh snapshot.
 */
public interface QrStatusPublisher {

    void publish(QrStatusUpdate update);
}
