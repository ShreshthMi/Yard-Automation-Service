package com.frauscher.ingest;

import com.frauscher.notification.NotificationService;
import com.frauscher.protocol.CoreLayout;
import com.frauscher.protocol.PacketDecoder;
import com.frauscher.protocol.ProtocolRegistry;
import com.frauscher.yard.AppProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Map;

/**
 * Starts a background UDP receive loop on application startup (one port,
 * shared by every configured yard - the same sender reports on multiple
 * yards' track sections in one packet). Every datagram is padded with the
 * header-length worth of zero bytes the core-definition sheet expects
 * before "Protocol Version" - a real UDP payload never actually contains
 * Ethernet/IP/UDP header bytes (the OS adds those, not the application),
 * but the config's offsets assume a full captured-frame layout. Padding
 * keeps those offsets correct with no changes to the sheet, matching the
 * exact convention used by the CLI parser's "--listen" mode.
 *
 * Reads the current protocol definition fresh from ProtocolRegistry for
 * every single packet (not cached at thread start), so a runtime reload
 * via ProtocolReloadController takes effect on the very next packet with
 * no restart needed.
 */
@Component
public class UdpListener implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UdpListener.class);

    private final AppProperties appProperties;
    private final ProtocolRegistry protocolRegistry;
    private final PacketValidator validator;
    private final RejectedPacketLogger rejectedPacketLogger;
    private final NotificationService notificationService;

    public UdpListener(AppProperties appProperties, ProtocolRegistry protocolRegistry,
                        PacketValidator validator, RejectedPacketLogger rejectedPacketLogger,
                        NotificationService notificationService) {
        this.appProperties = appProperties;
        this.protocolRegistry = protocolRegistry;
        this.validator = validator;
        this.rejectedPacketLogger = rejectedPacketLogger;
        this.notificationService = notificationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread thread = new Thread(this::listen, "udp-listener");
        thread.setDaemon(true);
        thread.start();
    }

    private void listen() {
        int port = appProperties.udp().port();

        try (DatagramSocket socket = new DatagramSocket(port)) {
            log.info("Listening for UDP packets on port {}", port);
            byte[] buffer = new byte[4096];
            while (true) {
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
                socket.receive(datagram);

                ProtocolRegistry.Snapshot snapshot = protocolRegistry.current();
                int headerPrefixLength = CoreLayout.findOffset(snapshot.coreRows(), "Protocol Version");

                byte[] framed = new byte[headerPrefixLength + datagram.getLength()];
                System.arraycopy(datagram.getData(), datagram.getOffset(), framed, headerPrefixLength, datagram.getLength());

                try {
                    ValidationResult result = validator.validate(framed);
                    if (!result.valid()) {
                        rejectedPacketLogger.log(framed, result.reason());
                        continue;
                    }
                    Map<String, Object> decoded = PacketDecoder.decode(snapshot.packetTree(), framed);
                    notificationService.processPacket(decoded);
                } catch (Exception e) {
                    log.warn("Failed to process a received packet, skipping it", e);
                }
            }
        } catch (Exception e) {
            log.error("UDP listener stopped unexpectedly", e);
        }
    }
}