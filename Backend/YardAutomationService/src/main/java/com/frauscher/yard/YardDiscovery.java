package com.frauscher.yard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.frauscher.protocol.Row;

/**
 * Builds the yard / track-section list straight from the payload-definition
 * sheet, instead of a separate properties file. Any identifier row with a
 * Yard Name filled in becomes one track section, grouped into yards by that
 * name, in the order the rows appear in the sheet - the same "sheet order
 * is zone order" convention used everywhere else in this project.
 *
 * A row with no Yard Name simply isn't part of any yard. It still decodes
 * normally; it's just not surfaced to the yard-automation/notification
 * layer.
 */
public class YardDiscovery {

    public static List<YardConfig> discover(List<Row> payloadRows) {
        Map<String, List<TrackSectionConfig>> sectionsByYard = new LinkedHashMap<>();

        for (Row row : payloadRows) {
            if (row.identifier == null || row.yardName == null) {
                continue;
            }
            TrackSectionConfig section = new TrackSectionConfig(row.identifier, row.occupiedMessage1, row.occupiedMessage2);
            sectionsByYard.computeIfAbsent(row.yardName, name -> new ArrayList<>()).add(section);
        }

        List<YardConfig> yards = new ArrayList<>();
        for (Map.Entry<String, List<TrackSectionConfig>> entry : sectionsByYard.entrySet()) {
            yards.add(new YardConfig(entry.getKey(), entry.getValue()));
        }
        return yards;
    }
}