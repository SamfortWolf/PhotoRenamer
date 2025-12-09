package com.samfort.photorenamer;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PhotoRenamerTest {

    private final FileRenameService fileRenameService = new FileRenameService();

    private final String testFileName = "IMG20251023104114_res.jpg";

    @Test
    void testProcessFile() throws Exception {
        // Создаем временный файл
        var testFile = Path.of(Objects.requireNonNull(
                getClass().getClassLoader().getResource(testFileName)
        ).toURI());

        assertThat(testFile).isNotNull();

        var expectedName = "20251023_104114_OPPO Find X9 Pro_140_mm_F2.1_1-364_ISO50.jpg";

        // Тестируем метод без перезаписи
        var result = fileRenameService.processFile(testFile, true);
        assertThat(result)
                .extracting(RenameResult::getStatus, RenameResult::getNewName)
                .containsExactlyInAnyOrder(RenameResult.Status.SUCCESS, expectedName);

        // Теперь запускаем с перезаписью
        result = fileRenameService.processFile(testFile, false);
        assertThat(result)
                .extracting(RenameResult::getStatus, RenameResult::getNewName)
                .containsExactlyInAnyOrder(RenameResult.Status.SUCCESS, expectedName);

        // Проверяем, что файл был переименован
        var renamedFile = testFile.getParent().resolve(expectedName);
        assertTrue(Files.exists(renamedFile));
        assertFalse(Files.exists(testFile));

        // Возвращаем файл в исходное состояние
        Files.move(renamedFile, testFile);
        assertTrue(Files.exists(testFile));
    }

}