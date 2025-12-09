package com.samfort.photorenamer;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PhotoRenamerTest {

    private final PhotoRenamer photoRenamer = new PhotoRenamer();

    private final String testFileName = "IMG20251023104114_res.jpg";

    @Test
    void testProcessFile() throws Exception {
        // Создаем временный файл
        var testFile = Path.of(Objects.requireNonNull(
                getClass().getClassLoader().getResource(testFileName)
        ).toURI());

        assertThat(testFile).isNotNull();

        var expectedName = "20251023_104114_OPPO Find X9 Pro_140_mm_F2.1_1-364_ISO50.jpg";
        var expectedOutputDry = "→       " + testFileName + " → " + expectedName;

        // Тестируем метод без перезаписи
        var result = PhotoRenamer.processFile(testFile, true);
        assertThat(result).isEqualTo(expectedOutputDry);

        // Теперь запускаем с перезаписью
        var expectedOutput = "RENAMED " + testFileName + " → " + expectedName;
        result = PhotoRenamer.processFile(testFile, false);
        assertThat(result).isEqualTo(expectedOutput);

        // Проверяем, что файл был переименован
        var renamedFile = testFile.getParent().resolve(expectedName);
        assertTrue(Files.exists(renamedFile));
        assertFalse(Files.exists(testFile));

        // Возвращаем файл в исходное состояние
        Files.move(renamedFile, testFile);
        assertTrue(Files.exists(testFile));
    }

}