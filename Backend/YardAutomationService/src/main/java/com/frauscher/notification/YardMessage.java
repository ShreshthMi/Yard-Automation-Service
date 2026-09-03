package com.frauscher.notification;

import java.util.Map;

/**
 * {
 *   "yardName": "YARD1",
 *   "payload": { ...decoded packet's payload, forwarded as-is... },
 *   "currentMessage": { "message": "...", "warning": "...", "error": null, "color": "#F9A825" }
 * }
 *
 * "payload" is the raw decoded packet payload, unmodified - the frontend
 * combines it with the static /api/yards/{id}/zones list (identifiers,
 * order, colors) to render a full per-zone view. "currentMessage" is the
 * single most-advanced occupied zone's message/warning/color, precomputed
 * server-side so every client shows the same thing without re-deriving it.
 */
public record YardMessage(String yardName, Map<String, Object> payload, CurrentMessage currentMessage) {}