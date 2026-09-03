package com.frauscher.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HexFormat;

/**
 * Writes rejected packets to a dedicated SLF4J logger named "REJECTED_PACKETS".
 * logback-spring.xml routes that logger (and only that logger) to
 * rejected-packets.log, separate from the normal application log.
 */
@Component
public class RejectedPacketLogger {

    private static final Logger log = LoggerFactory.getLogger("REJECTED_PACKETS");

    public void log(byte[] packet, String reason) {
        log.info("Rejected packet ({} bytes) - {} - bytes={}", packet.length, reason, HexFormat.of().formatHex(packet));
    }
}
