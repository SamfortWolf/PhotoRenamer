package com.samfort.photorenamer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;

public class PhotoRenamerGUI {

    private JFrame frame;
    private JTextField pathField;
    private JCheckBox recursiveCheckbox;
    private JTextArea logArea;
    private JButton dryRunButton;
    private JButton renameButton;

    private Path selectedFolder;
    private FileRenameService renameService;

    public PhotoRenamerGUI() {
        this.renameService = new FileRenameService();
        createUI();
    }

    private void createUI() {
        frame = new JFrame(Constants.WINDOW_TITLE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(
                Constants.COMPONENT_SPACING,
                Constants.COMPONENT_SPACING
        ));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(
                Constants.PADDING, Constants.PADDING,
                Constants.PADDING, Constants.PADDING
        ));

        mainPanel.add(createTopPanel(), BorderLayout.NORTH);
        mainPanel.add(createLogPanel(), BorderLayout.CENTER);
        mainPanel.add(createButtonPanel(), BorderLayout.SOUTH);

        frame.setContentPane(mainPanel);

        // Window closing handler
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                renameService.shutdown();
            }
        });
    }

    private JPanel createTopPanel() {
        pathField = new JTextField();
        pathField.setEditable(false);

        JButton chooseButton = new JButton(Constants.SELECT_FOLDER_BUTTON);
        chooseButton.addActionListener(e -> selectFolder());

        JPanel pathPanel = new JPanel(new BorderLayout(8, 0));
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(chooseButton, BorderLayout.EAST);

        recursiveCheckbox = new JCheckBox(Constants.RECURSIVE_CHECKBOX, true);
        recursiveCheckbox.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(pathPanel, BorderLayout.CENTER);
        topPanel.add(recursiveCheckbox, BorderLayout.SOUTH);

        return topPanel;
    }

    private JScrollPane createLogPanel() {
        logArea = new JTextArea();
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logArea.setEditable(false);

        return new JScrollPane(logArea);
    }

    private JPanel createButtonPanel() {
        dryRunButton = new JButton(Constants.DRY_RUN_BUTTON);
        dryRunButton.addActionListener(e -> performDryRun());

        renameButton = new JButton(Constants.RENAME_BUTTON);
        renameButton.setEnabled(false);
        renameButton.addActionListener(e -> performRename());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(dryRunButton);
        buttonPanel.add(renameButton);

        return buttonPanel;
    }

    private void selectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        int result = chooser.showOpenDialog(frame);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null && selected.isDirectory()) {
                selectedFolder = selected.toPath().normalize();
                pathField.setText(selectedFolder.toString());
                logArea.setText("");
                renameButton.setEnabled(true);
            }
        }
    }

    private void performDryRun() {
        if (selectedFolder == null) {
            JOptionPane.showMessageDialog(frame,
                    "Сначала выберите папку",
                    "Ошибка",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        logArea.setText("=== СУХОЙ ЗАПУСК ===\n");
        setButtonsEnabled(false);

        RenameConfig config = new RenameConfig(
                selectedFolder,
                recursiveCheckbox.isSelected(),
                true
        );

        new Thread(() -> {
            renameService.renamePhotos(config, new GUIProgressListener(true));
        }).start();
    }

    private void performRename() {
        if (selectedFolder == null) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(frame,
                "Точно переименовать все файлы в папке?\n" + selectedFolder,
                "Подтверждение",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        logArea.setText("=== ПЕРЕИМЕНОВАНИЕ ===\n");
        setButtonsEnabled(false);

        RenameConfig config = new RenameConfig(
                selectedFolder,
                recursiveCheckbox.isSelected(),
                false
        );

        new Thread(() -> {
            renameService.renamePhotos(config, new GUIProgressListener(false));
        }).start();
    }

    private void setButtonsEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            dryRunButton.setEnabled(enabled);
            renameButton.setEnabled(enabled);
            recursiveCheckbox.setEnabled(enabled);
        });
    }

    public void show() {
        frame.setVisible(true);
    }

    private class GUIProgressListener implements ProgressListener {
        private final boolean isDryRun;

        public GUIProgressListener(boolean isDryRun) {
            this.isDryRun = isDryRun;
        }

        @Override
        public void onStart(int totalFiles) {
            SwingUtilities.invokeLater(() -> {
                logArea.append("Найдено файлов: " + totalFiles + "\n\n");
            });
        }

        @Override
        public void onProgress(RenameResult result) {
            SwingUtilities.invokeLater(() -> {
                String line = result.toLogString();

                // Adjust formatting for dry run
                if (isDryRun && result.getStatus() == RenameResult.Status.SUCCESS) {
                    line = "→       " + result.getOriginalName() + " → " + result.getNewName();
                }

                logArea.append(line + "\n");
            });
        }

        @Override
        public void onComplete(int renamed, int skipped, int errors) {
            SwingUtilities.invokeLater(() -> {
                logArea.append("\n=== ГОТОВО ===\n");

                if (isDryRun) {
                    logArea.append("Могло бы быть переименовано: " + renamed + "\n");
                    logArea.append("Пропущено: " + (skipped + errors) + "\n");
                    setButtonsEnabled(true);
                } else {
                    logArea.append("Переименовано: " + renamed + "\n");
                    logArea.append("Пропущено: " + skipped + "\n");
                    if (errors > 0) {
                        logArea.append("Ошибок: " + errors + "\n");
                    }
                    setButtonsEnabled(true);
                }

                // Auto-scroll to bottom
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
    }
}
