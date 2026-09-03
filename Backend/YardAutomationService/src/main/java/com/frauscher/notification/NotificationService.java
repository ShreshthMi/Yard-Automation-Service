package com.frauscher.notification;

import com.frauscher.audit.TrackEventLogger;
import com.frauscher.protocol.ProtocolRegistry;
import com.frauscher.websocket.YardBroadcaster;
import com.frauscher.yard.TrackSectionConfig;
import com.frauscher.yard.YardConfig;
import com.frauscher.yard.ZoneColors;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * For every yard defined in the protocol definition, forwards the decoded
 * packet's payload as-is (no rebuilding/flattening) plus a precomputed
 * CurrentMessage describing the single most-advanced occupied zone - "most
 * advanced" meaning the highest-index configured track section that's currently
 * occupied, since track sections are ordered by proximity to the boundary.
 *
 * Also feeds every track section's decoded state to TrackEventLogger, which
 * writes an audit-trail CSV line whenever a track's occupied/clear or ERR/CE
 * state actually changes (never once per packet).
 */
@Component
public class NotificationService {

	private final ProtocolRegistry protocolRegistry;
	private final YardBroadcaster broadcaster;
	private final TrackEventLogger trackEventLogger;

	public NotificationService(ProtocolRegistry protocolRegistry, YardBroadcaster broadcaster,
			TrackEventLogger trackEventLogger) {
		this.protocolRegistry = protocolRegistry;
		this.broadcaster = broadcaster;
		this.trackEventLogger = trackEventLogger;
	}

	@SuppressWarnings("unchecked")
	public void processPacket(Map<String, Object> decodedPacket) {
		Object rawPayload = decodedPacket.get("payload");
		if (!(rawPayload instanceof Map)) {
			return; // nothing to report if this packet has no payload section
		}
		Map<String, Object> payload = (Map<String, Object>) rawPayload;

		for (YardConfig yard : protocolRegistry.current().yards()) {
			for (TrackSectionConfig ts : yard.trackSections()) {
				trackEventLogger.record(yard.name(), ts.name(), findByKey(payload, ts.name()));
			}
			CurrentMessage currentMessage = buildCurrentMessage(yard, payload);
			broadcaster.broadcast(new YardMessage(yard.name(), payload, currentMessage));
		}
	}

	private CurrentMessage buildCurrentMessage(YardConfig yard, Map<String, Object> payload) {
		List<TrackSectionConfig> sections = yard.trackSections();
		int total = sections.size();
		List<String> missing = new ArrayList<>();
		int activeIndex = -1; // highest index found occupied so far

		for (int i = 0; i < total; i++) {
			TrackSectionConfig ts = sections.get(i);
			Object identifierData = findByKey(payload, ts.name());
			if (identifierData == null) {
				missing.add(ts.name());
				continue;
			}
			Integer clr = extractClr(identifierData);
			if (clr != null && clr == 0) {
				activeIndex = i;
			}
		}

		if (!missing.isEmpty()) {
			// Conservative on purpose: if any configured zone's status can't be verified,
			// don't guess a "current position" from the rest - the train could genuinely
			// be in the zone that's missing data.
			return new CurrentMessage(null, null,
					"Identifier(s) not found in this packet: " + String.join(", ", missing), null);
		}
		if (activeIndex == -1) {
			return new CurrentMessage(null, null, null, ZoneColors.NEUTRAL);
		}
		TrackSectionConfig active = sections.get(activeIndex);
		return new CurrentMessage(active.clrMessage(), active.clrWarning(), null,
				ZoneColors.forPosition(activeIndex, total));
	}

	@SuppressWarnings("unchecked")
	private Integer extractClr(Object identifierData) {
		if (!(identifierData instanceof Map))
			return null;
		Object status = ((Map<String, Object>) identifierData).get("status");
		if (!(status instanceof Map))
			return null;
		Object clr = ((Map<String, Object>) status).get("CLR");
		return (clr instanceof Number) ? ((Number) clr).intValue() : null;
	}

	/**
	 * Recursively searches a decoded payload's Map/List tree for the given key and
	 * returns its value.
	 */
	@SuppressWarnings("unchecked")
	private Object findByKey(Object node, String key) {
		if (node instanceof Map) {
			Map<String, Object> map = (Map<String, Object>) node;
			if (map.containsKey(key))
				return map.get(key);
			for (Object value : map.values()) {
				Object found = findByKey(value, key);
				if (found != null)
					return found;
			}
		} else if (node instanceof List) {
			for (Object item : (List<Object>) node) {
				Object found = findByKey(item, key);
				if (found != null)
					return found;
			}
		}
		return null;
	}
}