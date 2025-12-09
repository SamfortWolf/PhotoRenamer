package com.samfort.photorenamer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.Objects;

@Data
@Builder
@AllArgsConstructor
public class RenameConfig {

    private final Path targetFolder;
    private final boolean recursive;
    private final boolean dryRun;

}
