package com.frauscher.yard;

/**
 * One track section within a yard: which identifier it maps to (e.g. "1T",
 * matching an identifier from the payload-definition sheet), and the
 * message/warning to show when that identifier's CLR bit is 0 (occupied).
 */
public record TrackSectionConfig(String name, String clrMessage, String clrWarning) {}
