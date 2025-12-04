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

    private static final Set<String> EXT = Set.of(".jpg",".jpeg",".tif",".tiff",".png",".arw",".cr2",".nef",".orf",".rw2",".dng", ".heic", ".heif");
    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyyMMdd_HHmmss");

    private static Path selectedFolder = null;
    private static boolean recursive = true;

    public static void main(String[] args) {
        try {
            // Фикс для macOS 15+/26: headless mode, sandbox, entitlements
            System.setProperty("java.awt.headless", "false");
            System.setProperty("apple.awt.UIElement", "false");
            System.setProperty("com.apple.security.app-sandbox.enabled", "false");

            // Включаем нативное поведение меню (About, Quit и т.д.) на macOS
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.mrj.application.apple.menu.about.name",
                    "com.samfort.photorenamer.PhotoRenamer");
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(PhotoRenamer::createGUI);
    }

    private static void createGUI() {
        JFrame frame = new JFrame("PhotoRenamer — переименование по EXIF");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(780, 580);
        frame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // ===== ВЕРХНЯЯ ПАНЕЛЬ =====
        JTextField pathField = new JTextField();
        pathField.setEditable(false);
        JButton chooseBtn = new JButton("Выбрать папку");

        JPanel topPanel = new JPanel(new BorderLayout(8, 0));
        topPanel.add(pathField, BorderLayout.CENTER);
        topPanel.add(chooseBtn, BorderLayout.EAST);

        // ===== ЧЕКБОКС РЕКУРСИВНО — ВЕРНУЛИ НА МЕСТО =====
        JCheckBox recursiveBox = new JCheckBox("Рекурсивно (включая подпапки)", true);
        recursiveBox.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel northWrapper = new JPanel(new BorderLayout());
        northWrapper.add(topPanel, BorderLayout.CENTER);
        northWrapper.add(recursiveBox, BorderLayout.SOUTH);

        panel.add(northWrapper, BorderLayout.NORTH);

        // ===== ЛОГ =====
        JTextArea log = new JTextArea();
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        log.setEditable(false);
        panel.add(new JScrollPane(log), BorderLayout.CENTER);

        // ===== КНОПКИ =====
        JButton dryRunBtn = new JButton("Сухой запуск");
        JButton renameBtn = new JButton("Переименовать!");
        renameBtn.setEnabled(false);

        JPanel buttons = new JPanel();
        buttons.add(dryRunBtn);
        buttons.add(renameBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        frame.setContentPane(panel);
        frame.setVisible(true);

        // ===== ВЫБОР ПАПКИ  =====
        chooseBtn.addActionListener(e -> {
            // 1. Создаем JFileChooser
            JFileChooser fc = new JFileChooser();

            // 2. Устанавливаем режим выбора только директорий (ОЧЕНЬ ВАЖНО для кроссплатформенности)
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            // 3. Запрещаем выбор файлов
            fc.setAcceptAllFileFilterUsed(false);

            // 4. Устанавливаем начальную директорию (опционально)
            fc.setCurrentDirectory(new File(System.getProperty("user.home")));

            // 5. Показываем диалог
            int result = fc.showOpenDialog(frame);

            // 6. Обработка результата
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fc.getSelectedFile();
                if (selectedFile != null && selectedFile.isDirectory()) {
                    Path selected = selectedFile.toPath().normalize();

                    selectedFolder = selected;
                    pathField.setText(selectedFolder.toString());
                    log.setText("");
                    renameBtn.setEnabled(true);
                }
            }
        });

        // ===== РЕКУРСИЯ =====
        recursiveBox.addActionListener(e -> recursive = recursiveBox.isSelected());

        // ===== СУХОЙ ЗАПУСК =====
        dryRunBtn.addActionListener(e -> {
            if (selectedFolder == null) {
                JOptionPane.showMessageDialog(frame, "Сначала выберите папку");
                return;
            }
            log.setText("=== СУХОЙ ЗАПУСК ===\n");
            renameBtn.setEnabled(false);
            new Thread(() -> runProcess(selectedFolder, true, log, () -> renameBtn.setEnabled(true))).start();
        });

        // ===== РЕАЛЬНОЕ ПЕРЕИМЕНОВАНИЕ =====
        renameBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(frame,
                    "Точно переименовать все файлы в папке?\n" + selectedFolder,
                    "Подтверждение", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                log.setText("=== ПЕРЕИМЕНОВАНИЕ ===\n");
                renameBtn.setEnabled(false);
                new Thread(() -> runProcess(selectedFolder, false, log, null)).start();
            }
        });
    }

    private static void runProcess(Path folder, boolean dryRun, JTextArea log, Runnable onDryFinish) {
        try {
            List<Path> files = new ArrayList<>();
            try (var s = Files.walk(folder, recursive ? Integer.MAX_VALUE : 1)) {
                s.filter(Files::isRegularFile)
                        .filter(p -> EXT.contains(p.toString().toLowerCase().substring(p.toString().lastIndexOf('.'))))
                        .forEach(files::add);
            }

            log.append("Найдено файлов: " + files.size() + "\n\n");

            ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            List<Future<String>> futures = new ArrayList<>();

            for (Path f : files) {
                futures.add(pool.submit(() -> processFile(f, dryRun)));
            }

            int renamed = 0, would = 0, skipped = 0;
            for (var fut : futures) {
                String line = fut.get();
                log.append(line + "\n");
                if (line.startsWith("→")) would++;
                else if (line.startsWith("RENAMED")) renamed++;
                else skipped++;
            }
            pool.shutdown();

            log.append("\n=== ГОТОВО ===\n");
            if (dryRun) {
                log.append("Могло бы быть переименовано: " + would + "\n");
                log.append("Пропущено: " + skipped + "\n");
                if (onDryFinish != null) SwingUtilities.invokeLater(onDryFinish);
            } else {
                log.append("Переименовано: " + renamed + "\n");
                log.append("Пропущено: " + skipped + "\n");
            }

        } catch (Exception ex) {
            log.append("ОШИБКА: " + ex.getMessage() + "\n");
        }
    }

    private static String processFile(Path file, boolean dryRun) {
        try {
            Metadata meta = ImageMetadataReader.readMetadata(file.toFile());

            Date date = null;
            var sub = meta.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (sub != null) date = sub.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            if (date == null) {
                var ifd0 = meta.getFirstDirectoryOfType(ExifIFD0Directory.class);
                if (ifd0 != null) date = ifd0.getDate(ExifIFD0Directory.TAG_DATETIME);
            }
            if (date == null) return "SKIP    " + file.getFileName();

            var photoTime = date.toInstant()
                    .atZone(ZoneId.of("UTC"));

            var prefix = photoTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            StringBuilder name = new StringBuilder(prefix);

            String model = getTag(meta, ExifIFD0Directory.class, ExifIFD0Directory.TAG_MODEL);
            String focal = getFocalLength35mm(meta) != null ? getFocalLength35mm(meta) : getTag(meta, "Focal Length");
            String fnum  = getTag(meta, "F-Number");
            String shutter = getTag(meta, "Exposure Time");
            String iso   = getTag(meta, "ISO Speed Ratings");

            if (model != null) name.append("_").append(clean(model));
            if (focal != null) name.append("_").append(clean(focal).replace(" ", ""));
            if (fnum != null)  name.append("_").append(clean(formatAperture(fnum)));
            if (shutter != null) name.append("_").append(formatShutterSpeed(clean(shutter)).replace("/", "-"));
            if (iso != null) name.append("_ISO").append(iso.trim());

            String ext = file.toString().substring(file.toString().lastIndexOf('.'));
            Path newPath = file.resolveSibling(name + ext);

            int cnt = 1;
            Path temp = newPath;
            while (Files.exists(temp) && !temp.equals(file)) {
                temp = file.resolveSibling(name + "_" + cnt++ + ext);
            }
            newPath = temp;

            if (!dryRun) {
                Files.move(file, newPath);
                return "RENAMED " + file.getFileName() + " → " + newPath.getFileName();
            } else {
                return "→       " + file.getFileName() + " → " + newPath.getFileName();
            }

        } catch (Exception e) {
            return "ERROR   " + file.getFileName() + " (" + e.getClass().getSimpleName() + ")";
        }
    }

    private static String formatShutterSpeed(String rawShutter) {
        if (rawShutter == null || rawShutter.isBlank()) return null;

        // Примеры входных значений:
        // "1/200", "0.005", "1/125", "1/1000", "8", "30", "3109601_1000000000 sec"

        try {
            // Если уже в виде дроби — 1/200
            if (rawShutter.contains("/")) {
                String[] parts = rawShutter.split("/");
                double num = Double.parseDouble(parts[0].trim());
                double den = Double.parseDouble(parts[1].trim());
                if (num == 1.0) {
                    return "1-" + (int)Math.round(den);  // → 1-200
                } else {
                    return (int)Math.round(num) + "-" + (int)Math.round(den);
                }
            }

            // Если с нижним подчеркиванием, например 3109601_1000000000 sec
            if (rawShutter.contains("_")) {
                // Сначала убираем все буквенные и пробельные символы
                String formatted = rawShutter.replaceAll("[^0-9_]", "");
                String[] parts = formatted.split("_");
                double num = Double.parseDouble(parts[0].trim());
                double den = Double.parseDouble(parts[1].trim());
                double sec = num / den;
                if (sec >= 1.0) {
                    return (int) Math.round(sec) + "s";  // 8 → 8s, 30 → 30s
                } else {
                    int denom = (int)Math.round(1.0 / sec);
                    return "1-" + denom;
                }
            }

            // Если в секундах: "0.005" → 1/200
            double sec = Double.parseDouble(rawShutter.trim());
            if (sec >= 1.0) {
                return String.valueOf((int)Math.round(sec)) + "s";  // 8 → 8s, 30 → 30s
            } else {
                int denom = (int)Math.round(1.0 / sec);
                return "1-" + denom;
            }
        } catch (Exception e) {
            return EMPTY;
        }
    }

    private static String getTag(Metadata m, Class<? extends Directory> cls, int tag) {
        Directory d = m.getFirstDirectoryOfType(cls);
        return (d != null && d.containsTag(tag)) ? d.getString(tag) : null;
    }

    private static String getTag(Metadata m, String name) {
        for (Directory d : m.getDirectories()) {
            for (com.drew.metadata.Tag t : d.getTags()) {
                if (t.getTagName().equals(name)) return t.getDescription();
            }
        }
        return null;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
    }

    private static String formatAperture(String rawAperture) {
        if (rawAperture == null || rawAperture.isBlank()) return EMPTY;

        // Убираем всё лишнее: "f/", "F", пробелы, и заменяем запятую на точку
        String cleaned = rawAperture.replaceAll("[^0-9.,]", "");

        // Заменяем запятую на точку (немецкий/русский формат → международный)
        cleaned = cleaned.replace(',', '.');


            return "F" + cleaned;
    }

    private static String getFocalLength35mm(Metadata metadata) {
        // 1. Самый надёжный: уже готовый тег "35efl"
        String efl35 = getTagByName(metadata, "Focal Length 35");
        if (efl35 != null) {
            // "26.0 mm (35 mm equivalent)" → "26"
            return efl35.replaceAll("\\s", "_").trim();
        }

        // 3. Samsung / некоторые Android
        String samsung35 = getTagByName(metadata, "FocalLengthIn35mmFormat");
        if (samsung35 != null) {
            return samsung35.trim();
        }

        // 4. Ручной расчёт через crop-factor (если знаем модель)
        String realFocal = getTag(metadata, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
        if (realFocal == null) return null;

        Double focal = parseFocalLength(realFocal);
        if (focal == null) return null;

        // Таблица самых популярных смартфонов (можно расширять)
        String model = getTag(metadata, ExifIFD0Directory.class, ExifIFD0Directory.TAG_MODEL);
        Double crop = getCropFactor(model);
        if (crop != null) {
            int equiv = (int) Math.round(focal * crop);
            return String.valueOf(equiv);
        }

        return null; // не смогли посчитать
    }

    // Вспомогательные методы
    private static Double parseFocalLength(String s) {
        try {
            return Double.parseDouble(s.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static Double getCropFactor(String model) {
        if (model == null) return null;
        String m = model.toLowerCase();

        // iPhone (примерно, Apple не публикует точные значения)
        if (m.contains("iphone")) {
            if (m.contains("15") || m.contains("16")) return 7.0;   // iPhone 15/16 Pro — примерно 1/1.28"
            if (m.contains("14 pro") || m.contains("15 pro")) return 6.86;
            if (m.contains("13 pro") || m.contains("14")) return 6.0;
            return 5.7; // старые модели
        }
        // Google Pixel
        if (m.contains("pixel 8 pro")) return 6.7;
        if (m.contains("pixel")) return 6.0;

        // Samsung
        if (m.contains("galaxy s23 ultra") || m.contains("s24 ultra")) return 6.6;
        if (m.contains("galaxy s")) return 6.0;

        return null;
    }

    // Улучшенная версия getTagByName (ищет по имени тега)
    private static String getTagByName(Metadata metadata, String tagName) {
        for (Directory directory : metadata.getDirectories()) {
            for (Tag tag : directory.getTags()) {
                if (tagName.equalsIgnoreCase(tag.getTagName())) {
                    String description = tag.getDescription();
                    return description != null ? description : tag.toString();
                }
            }
        }
        return null;
    }
}