package com.frauscher.yard;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds application configuration, e.g.:
 *   app.protocol-definition-path=protocol_definition.xlsx
 *   app.udp.port=9000
 *
 * Both have working defaults in src/main/resources/application.properties,
 * so the application can run with no external properties file at all.
 * Override either one at the command line, or with
 * --spring.config.additional-location if you want a properties file for it.
 *
 * Yard and track-section configuration no longer lives here - it comes
 * directly from protocol_definition.xlsx (see YardDiscovery), so the two
 * stay in sync automatically instead of needing to be kept in step by hand.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String protocolDefinitionPath, Udp udp) {
    public record Udp(int port) {}
}