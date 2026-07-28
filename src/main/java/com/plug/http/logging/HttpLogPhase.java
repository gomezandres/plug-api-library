package com.plug.http.logging;

/**
 * Which side of one HTTP attempt an {@link HttpLogEvent} describes. The client emits one
 * {@code OUTBOUND} event right before the request is sent, and one {@code INBOUND} event once
 * the response (or failure) for that same attempt is known — mirroring how a wire-level logging
 * interceptor reports a call as two distinct, independently-timestamped log lines.
 */
public enum HttpLogPhase {
    OUTBOUND, INBOUND
}
