package com.strawing.fuckdevicepolicy;

import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RestrictionOutputParser {

    private static final Pattern RESTRICTION_PATTERN =
            Pattern.compile("userRestriction_([A-Za-z0-9_]+)");

    private RestrictionOutputParser() {}

    /**
     * Scans raw dumpsys output for every "userRestriction_<name>" token,
     * strips the "userRestriction_" prefix, and returns the remaining
     * names one per line, in order of first appearance with duplicates
     * removed (the same restriction key often appears once per admin
     * component in the raw dump).
     */
    public static String extractRestrictionNames(String rawOutput) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Matcher matcher = RESTRICTION_PATTERN.matcher(rawOutput);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }

        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            sb.append(name).append('\n');
        }
        return sb.toString().trim();
    }
}
