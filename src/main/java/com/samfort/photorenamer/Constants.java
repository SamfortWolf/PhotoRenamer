package com.samfort.photorenamer;

import java.util.Set;

public class Constants {

    // UI
    public static final int WINDOW_WIDTH = 780;
    public static final int WINDOW_HEIGHT = 580;
    public static final int PADDING = 15;
    public static final int COMPONENT_SPACING = 10;

    // File processing
    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".tif", ".tiff", ".png", ".arw",
            ".cr2", ".nef", ".orf", ".rw2", ".dng", ".heic", ".heif"
    );

    // Date/Time formats
    public static final String FILENAME_DATE_PATTERN = "yyyyMMdd_HHmmss";

    // Text constants
    public static final String EMPTY = "";
    public static final String WINDOW_TITLE = "PhotoRenamer — переименование по EXIF";
    public static final String SELECT_FOLDER_BUTTON = "Выбрать папку";
    public static final String RECURSIVE_CHECKBOX = "Рекурсивно (включая подпапки)";
    public static final String DRY_RUN_BUTTON = "Сухой запуск (для проверки результат без переименования)";
    public static final String RENAME_BUTTON = "Переименовать!";
}
