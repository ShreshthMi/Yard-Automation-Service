package com.frauscher.yard;

/**
 * Computes a zone's color purely from its distance from the boundary - never
 * configured per zone, so it re-adjusts automatically whenever a yard's track
 * sections are added or removed.
 *
 * Rule (rail-signaling style, "option B"): the last zone is always red, the
 * one before it single-yellow, the one before that double-yellow, and every
 * zone earlier than that is green. Works for any zone count >= 1 - a yard
 * with fewer than 4 zones just never reaches green (e.g. 3 zones: double-
 * yellow, single-yellow, red; 1 zone: red).
 */
public class ZoneColors {

    public static final String GREEN = "#639922";
    public static final String DOUBLE_YELLOW = "#FDD835";
    public static final String SINGLE_YELLOW = "#F9A825";
    public static final String RED = "#A32D2D";

    /** Used when no zone in a yard is currently occupied - not one of the hazard colors. */
    public static final String NEUTRAL = "#B4B2A9";

    /**
     * @param index 0-based position of this zone within its yard's track-sections list
     * @param total how many track sections that yard has
     */
    public static String forPosition(int index, int total) {
        int distanceFromEnd = total - 1 - index;
        return switch (distanceFromEnd) {
            case 0 -> RED;
            case 1 -> SINGLE_YELLOW;
            case 2 -> DOUBLE_YELLOW;
            default -> GREEN;
        };
    }
}