package com.samfort.photorenamer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

// В идеале расширить базу данных и сделать ее настраиваемой
public class CropFactorDatabase {

    private static final Map<Pattern, Double> CROP_FACTORS = new LinkedHashMap<>();

    static {
        // iPhone patterns (порядок важен - от конкретного к общему)
        CROP_FACTORS.put(Pattern.compile("iPhone\\s*(1[5-6])\\s*Pro", Pattern.CASE_INSENSITIVE), 7.0);
        CROP_FACTORS.put(Pattern.compile("iPhone\\s*1[4-6]", Pattern.CASE_INSENSITIVE), 6.86);
        CROP_FACTORS.put(Pattern.compile("iPhone\\s*13\\s*Pro", Pattern.CASE_INSENSITIVE), 6.0);
        CROP_FACTORS.put(Pattern.compile("iPhone", Pattern.CASE_INSENSITIVE), 5.7);

        // Google Pixel
        CROP_FACTORS.put(Pattern.compile("Pixel\\s*8\\s*Pro", Pattern.CASE_INSENSITIVE), 6.7);
        CROP_FACTORS.put(Pattern.compile("Pixel", Pattern.CASE_INSENSITIVE), 6.0);

        // Samsung Galaxy
        CROP_FACTORS.put(Pattern.compile("Galaxy\\s*S2[34]\\s*Ultra", Pattern.CASE_INSENSITIVE), 6.6);
        CROP_FACTORS.put(Pattern.compile("Galaxy\\s*S", Pattern.CASE_INSENSITIVE), 6.0);
    }

    public static Double getCropFactor(String model) {
        if (model == null) return null;

        for (Map.Entry<Pattern, Double> entry : CROP_FACTORS.entrySet()) {
            if (entry.getKey().matcher(model).find()) {
                return entry.getValue();
            }
        }

        return null;
    }
}
