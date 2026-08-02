package com.methodia.minibilling.controller.dto.importing;

import java.util.List;

public record FileImportResponse(List<FileImportResult> results) {

    public boolean allSuccessful() {
        for (FileImportResult result : results) {
            if (!result.success()) {
                return false;
            }
        }
        return true;
    }
}
