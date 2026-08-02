package com.methodia.minibilling.importer;

import org.springframework.web.multipart.MultipartFile;

public record FileImportUpload(String type, MultipartFile file) {

    public String fileName() {
        return file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
    }
}
