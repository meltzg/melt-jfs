package org.meltzg.fs.mtp;

import java.io.IOException;

/**
 * Signals that the device rejected a native MTP operation (e.g. MoveObject) that it does not
 * implement. Callers may catch this to fall back to an emulated equivalent.
 */
class MTPOperationUnsupportedException extends IOException {
    MTPOperationUnsupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}
