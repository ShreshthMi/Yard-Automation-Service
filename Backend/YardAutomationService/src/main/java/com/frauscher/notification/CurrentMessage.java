package com.frauscher.notification;

/**
 * Three possible states:
 *  - A zone is occupied: message/warning from that zone's config, color from
 *    ZoneColors, error is null.
 *  - No zone occupied anywhere in the yard (train simply isn't there right
 *    now - normal, not a problem): message/warning are null, color is
 *    ZoneColors.NEUTRAL, error is null.
 *  - A configured track section's identifier wasn't found anywhere in this
 *    packet (a real config/data problem, not a train-position state):
 *    message/warning/color are all null, error describes what's missing.
 *    Deliberately conservative - if even one configured zone's status can't
 *    be verified, this doesn't guess at "current position" from the rest,
 *    since the train could actually be in the zone that's missing data.
 */
public record CurrentMessage(String message, String warning, String error, String color) {}