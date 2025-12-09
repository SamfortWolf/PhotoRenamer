package com.samfort.photorenamer;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ExifMetadataService {

    public Optional<ZonedDateTime> extractPhotoDateTime(Path file) {
        try {
            var metadata = ImageMetadataReader.readMetadata(file.toFile());
            var date = extractDateFromMetadata(metadata);

            if (date == null) {
                return Optional.empty();
            }

            return Optional.of(date.toInstant().atZone(ZoneId.of("UTC")));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Date extractDateFromMetadata(Metadata metadata) {
        // Try EXIF SubIFD first (most reliable)
        var subDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (subDir != null) {
            Date date = subDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            if (date != null) return date;
        }

        // Fallback to IFD0
        var ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (ifd0 != null) {
            return ifd0.getDate(ExifIFD0Directory.TAG_DATETIME);
        }

        return null;
    }

    public Map<String, String> extractPhotoMetadata(Path file) {
        Map<String, String> result = new HashMap<>();

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());

            result.put("model", getTag(metadata, ExifIFD0Directory.class, ExifIFD0Directory.TAG_MODEL));
            result.put("focal", extractFocalLength(metadata));
            result.put("aperture", findTagByName(metadata, "F-Number"));
            result.put("shutter", findTagByName(metadata, "Exposure Time"));
            result.put("iso", findTagByName(metadata, "ISO Speed Ratings"));

        } catch (Exception e) {
            // Return empty map on error
        }

        return result;
    }

    private String getTag(Metadata metadata, Class<? extends Directory> dirClass, int tagId) {
        Directory dir = metadata.getFirstDirectoryOfType(dirClass);
        return (dir != null && dir.containsTag(tagId)) ? dir.getString(tagId) : null;
    }

    private String findTagByName(Metadata metadata, String tagName) {
        for (Directory dir : metadata.getDirectories()) {
            for (Tag tag : dir.getTags()) {
                if (tagName.equalsIgnoreCase(tag.getTagName())) {
                    return tag.getDescription();
                }
            }
        }
        return null;
    }

    private String extractFocalLength(Metadata metadata) {
        // Try 35mm equivalent first
        String focal35 = findTagByName(metadata, "Focal Length 35");
        if (focal35 != null) {
            return focal35.replaceAll("\\s+", "_");
        }

        // Try Samsung format
        String samsung = findTagByName(metadata, "FocalLengthIn35mmFormat");
        if (samsung != null) {
            return samsung.trim();
        }

        // Calculate from crop factor
        String realFocal = getTag(metadata, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
        String model = getTag(metadata, ExifIFD0Directory.class, ExifIFD0Directory.TAG_MODEL);

        return calculateEquivalentFocalLength(realFocal, model);
    }

    private String calculateEquivalentFocalLength(String focalStr, String model) {
        if (focalStr == null || model == null) return null;

        Double focal = parseDouble(focalStr);
        Double cropFactor = CropFactorDatabase.getCropFactor(model);

        if (focal != null && cropFactor != null) {
            return String.valueOf((int) Math.round(focal * cropFactor));
        }

        return null;
    }

    private Double parseDouble(String str) {
        try {
            return Double.parseDouble(str.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
