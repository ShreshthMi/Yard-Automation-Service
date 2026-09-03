package com.frauscher.controller;

import com.frauscher.protocol.ProtocolRegistry;
import com.frauscher.yard.TrackSectionConfig;
import com.frauscher.yard.YardConfig;
import com.frauscher.yard.ZoneColors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/yards ->
 * [ { "id": "Test Yard 1", "name": "Test Yard 1" }, ... ]
 *
 * GET /api/yards/{yardId}/zones ->
 * [ { "identifier": "1T", "zoneNumber": 1, "color": "#639922" }, ... ]
 *
 * Both derived straight from the currently-loaded protocol_definition.xlsx
 * (via ProtocolRegistry - reload it and these follow automatically, no
 * restart needed). "id" and "name" are the same value: the sheet only
 * gives a Yard Name, so that name is the yard's sole identifier - both
 * keys are kept in the response for compatibility with existing clients.
 * A frontend fetches the yard list once to populate a picker, then fetches
 * one yard's zones once it's selected, then combines that with the live
 * WebSocket payload to render a full per-zone track diagram: these
 * endpoints supply identifier/order/color, the WebSocket payload supplies
 * live CLR/OCC/etc per identifier.
 */
@RestController
@RequestMapping("/api/yards")
@CrossOrigin(origins = "*") // local dev/test convenience; tighten for production
public class YardZonesController {

    private final ProtocolRegistry protocolRegistry;

    public YardZonesController(ProtocolRegistry protocolRegistry) {
        this.protocolRegistry = protocolRegistry;
    }

    @GetMapping
    public List<Map<String, Object>> listYards() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (YardConfig yard : protocolRegistry.current().yards()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", yard.name());
            entry.put("name", yard.name());
            result.add(entry);
        }
        return result;
    }

    @GetMapping("/{yardId}/zones")
    public List<Map<String, Object>> zones(@PathVariable String yardId) {
        YardConfig yard = protocolRegistry.current().yards().stream()
                .filter(y -> y.name().equalsIgnoreCase(yardId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown yard: " + yardId));

        List<TrackSectionConfig> sections = yard.trackSections();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("identifier", sections.get(i).name());
            entry.put("zoneNumber", i + 1);
            entry.put("color", ZoneColors.forPosition(i, sections.size()));
            result.add(entry);
        }
        return result;
    }
}