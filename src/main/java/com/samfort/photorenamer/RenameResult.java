package com.samfort.photorenamer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public class RenameResult {

    public enum Status { SUCCESS, SKIPPED, ERROR }

    private final Status status;
    private final String originalName;
    private final String newName;
    private final String errorMessage;

    public static RenameResult success(@NonNull String original, @NonNull String newName) {
        return new RenameResult(Status.SUCCESS, original, newName, null);
    }

    public static RenameResult skipped(@NonNull String original, @NonNull String reason) {
        return new RenameResult(Status.SKIPPED, original, null, reason);
    }

    public static RenameResult error(@NonNull String original, @NonNull String error) {
        return new RenameResult(Status.ERROR, original, null, error);
    }

    public String toLogString() {
        return switch (status) {
            case SUCCESS -> String.format("RENAMED %s → %s", originalName, newName);
            case SKIPPED -> String.format("SKIP    %s (%s)", originalName, errorMessage);
            case ERROR -> String.format("ERROR   %s (%s)", originalName, errorMessage);
        };
    }
}
