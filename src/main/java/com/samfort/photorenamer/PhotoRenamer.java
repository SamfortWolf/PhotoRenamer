package com.samfort.photorenamer;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.apache.commons.lang3.StringUtils.EMPTY;

public class PhotoRenamer {

    public static void main(String[] args) {
        configureMacOS();
        SwingUtilities.invokeLater(() -> new PhotoRenamerGUI().show());
    }

    private static void configureMacOS() {
        try {
            System.setProperty("java.awt.headless", "false");
            System.setProperty("apple.awt.UIElement", "false");
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "PhotoRenamer");
        } catch (Exception e) {
            // Silently ignore on non-macOS platforms
        }
    }
}