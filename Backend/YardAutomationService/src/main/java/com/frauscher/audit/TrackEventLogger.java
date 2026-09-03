package com.frauscher.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes one CSV line to a dedicated SLF4J logger named "TRACK_EVENTS" -
 * logback-spring.xml routes that logger (and only that logger) to its own
 * daily-rotated file, separate from the normal application log, following
 * the exact same pattern as RejectedPacketLogger/rejected-packets.log.
 *
 * One checksum per (yardName, trackName): the combination of yardName,
 * trackName, status, and CATS. Logged only when that checksum differs from
 * the last one written for that track - so every distinct CATS value gets
 * its own line while a track stays occupied (1, 2, 3, ... as axles
 * arrive), and a repeated identical reading never produces a duplicate
 * line.
 *
 * status is one value per track, not several independent ones - if
 * multiple conditions are true at once, ERR takes priority over CE, which
 * takes priority over occupied/clear:
 *   ERR       - the ERR bit is 1 (a general error)
 *   CE        - the CE bit is 1 (a communication error) and ERR is not
 *   OCCUPIED  - CLR = 0 and neither ERR nor CE is active
 *   CLEAR     - CLR = 1 and neither ERR nor CE is active
 *
 * Because status is a single value, there's no separate "cleared" label
 * for ERR/CE - when an error ends, the checksum still changes (so it's
 * still logged), it just shows up as a transition to whatever the track's
 * status is then (OCCUPIED, CLEAR, or the other error type).
 *
 * The very first observation of a track is logged only if it starts out
 * OCCUPIED, ERR, or CE - not for the ordinary default (CLEAR), which
 * isn't worth a log line just because this service happened to start
 * watching then.
 */
@Component
public class TrackEventLogger {

    private static final Logger log = LoggerFactory.getLogger("TRACK_EVENTS");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Map<String, String> lastChecksum = new ConcurrentHashMap<>(); // "yard|track" -> "status|CATS"

    // No header line, deliberately - this is written once per service startup, and this
    // service gets restarted multiple times a day during normal use/testing. A header
    // written from a constructor would reappear every restart, scattered through the
    // middle of that day's file. Column order is fixed and documented in the README
    // instead: dateTime,yardName,trackName,status,CATS

    /**
     * @param identifierData the decoded value for this track's identifier (e.g. the "1T" node
     *                        from the payload - {"status": {...}, "CATS": n, "TL": n}), or null
     *                        if it wasn't found in this packet at all
     */
    @SuppressWarnings("unchecked")
    public void record(String yardName, String trackName, Object identifierData) {
        if (!(identifierData instanceof Map)) {
            return;
        }
        Map<String, Object> data = (Map<String, Object>) identifierData;
        Object statusObj = data.get("status");
        if (!(statusObj instanceof Map)) {
            return;
        }
        Map<String, Object> statusMap = (Map<String, Object>) statusObj;
        Integer cats = asInt(data.get("CATS")); // FMA.CATS, straight off this identifier's own decoded node

        Integer clr = asInt(statusMap.get("CLR"));
        Integer err = asInt(statusMap.get("ERR"));
        Integer ce = asInt(statusMap.get("CE"));

        String status = currentStatus(clr, err, ce);
        if (status == null) {
            return; // nothing determinable for this track from this packet
        }

        String key = yardName + "|" + trackName;
        String checksum = status + "|" + (cats == null ? "" : cats);
        String previousChecksum = lastChecksum.put(key, checksum);

        boolean firstObservation = (previousChecksum == null);
        if (firstObservation) {
            if (!"CLEAR".equals(status)) {
                writeEvent(yardName, trackName, status, cats);
            }
        } else if (!checksum.equals(previousChecksum)) {
            writeEvent(yardName, trackName, status, cats);
        }
    }

    private String currentStatus(Integer clr, Integer err, Integer ce) {
        if (err != null && err == 1) return "ERR";
        if (ce != null && ce == 1) return "CE";
        if (clr != null) return (clr == 0) ? "OCCUPIED" : "CLEAR";
        return null;
    }

    private void writeEvent(String yardName, String trackName, String status, Integer cats) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        log.info("{},{},{},{},{}", timestamp, csvEscape(yardName), csvEscape(trackName), status,
                cats == null ? "" : cats);
    }

    private Integer asInt(Object value) {
        return (value instanceof Number) ? ((Number) value).intValue() : null;
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}