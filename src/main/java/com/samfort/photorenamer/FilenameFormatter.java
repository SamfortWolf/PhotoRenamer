package com.samfort.photorenamer;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;

public class FilenameFormatter {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(Constants.FILENAME_DATE_PATTERN);

    public String formatFilename(ZonedDateTime photoTime, Map<String, String> metadata) {
        StringBuilder name = new StringBuilder();

        // Date/time prefix
        name.append(photoTime.format(DATE_FORMATTER));

        // Camera model
        appendIfPresent(name, metadata.get("model"), this::cleanString);

        // Focal length
        appendIfPresent(name, metadata.get("focal"), s -> cleanString(s).replace(" ", ""));

        // Aperture
        appendIfPresent(name, metadata.get("aperture"), this::formatAperture);

        // Shutter speed
        appendIfPresent(name, metadata.get("shutter"), this::formatShutterSpeed);

        // ISO
        String iso = metadata.get("iso");
        if (iso != null && !iso.isBlank()) {
            name.append("_ISO").append(iso.trim());
        }

        return name.toString();
    }

    private void appendIfPresent(StringBuilder sb, String value, Function<String, String> formatter) {
        if (value != null && !value.isBlank()) {
            String formatted = formatter.apply(value);
            if (!formatted.isEmpty()) {
                sb.append("_").append(formatted);
            }
        }
    }

    private String cleanString(String s) {
        if (s == null || s.isBlank()) return Constants.EMPTY;
        // Remove filesystem-unsafe characters and control characters
        return s.replaceAll("[/\\\\:*?\"<>|\\p{Cntrl}]", "_").trim();
    }

    private String formatAperture(String raw) {
        if (raw == null || raw.isBlank()) return Constants.EMPTY;

        String cleaned = raw.replaceAll("[^0-9.,]", "").replace(',', '.');
        return cleaned.isEmpty() ? Constants.EMPTY : "F" + cleaned;
    }

    private String formatShutterSpeed(String raw) {
        if (raw == null || raw.isBlank()) return Constants.EMPTY;

        try {
            // Handle fraction format: "1/200"
            if (raw.contains("/")) {
                String cleaned = raw.replaceAll("[^0-9/]", "");
                String[] parts = cleaned.split("/");
                double num = Double.parseDouble(parts[0].trim());
                double den = Double.parseDouble(parts[1].trim());

                if (num == 1.0) {
                    return "1-" + (int) Math.round(den);
                } else {
                    double sec = num / den;
                    return formatSecondsToShutterSpeed(sec);
                }
            }

            // Handle underscore format: "3109601_1000000000 sec"
            if (raw.contains("_")) {
                String cleaned = raw.replaceAll("[^0-9_]", "");
                String[] parts = cleaned.split("_");
                double num = Double.parseDouble(parts[0]);
                double den = Double.parseDouble(parts[1]);
                double sec = num / den;

                return formatSecondsToShutterSpeed(sec);
            }

            // Handle decimal seconds: "0.005", "8", "30"
            String cleaned = raw.replaceAll("[^0-9.]", "");
            double sec = Double.parseDouble(raw.trim());
            return formatSecondsToShutterSpeed(sec);

        } catch (Exception e) {
            return Constants.EMPTY;
        }
    }

    private String formatSecondsToShutterSpeed(double seconds) {
        if (seconds >= 1.0) {
            return (int) Math.round(seconds) + "s";
        } else {
            int denominator = (int) Math.round(1.0 / seconds);
            return "1-" + denominator;
        }
    }

    public String getFileExtension(Path file) {
        String filename = file.toString();
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex) : "";
    }
}
