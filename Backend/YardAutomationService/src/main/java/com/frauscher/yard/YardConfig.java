package com.frauscher.yard;

import java.util.List;

/**
 * One yard: its name (used as the WebSocket topic name, the REST API's
 * yardId path segment, and "yardName" in output) and its track sections.
 *
 * Built directly from protocol_definition.xlsx by YardDiscovery - the sheet
 * only gives a Yard Name, so that name is now the sole identifier; there is
 * no separate short id anymore.
 */
public record YardConfig(String name, List<TrackSectionConfig> trackSections) {}