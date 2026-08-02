package com.methodia.minibilling.controller;

import com.methodia.minibilling.importer.FileImportUpload;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MultipartImportRequestMapper {

    private static final Pattern INDEXED_FILE_FIELD = Pattern.compile("files\\[(\\d+)]\\.file");

    public List<FileImportUpload> toUploads(
            MultiValueMap<String, String> fields,
            MultiValueMap<String, MultipartFile> multipartFiles
    ) {
        TreeSet<Integer> indexes = new TreeSet<>();
        for (String key : multipartFiles.keySet()) {
            Matcher matcher = INDEXED_FILE_FIELD.matcher(key);
            if (matcher.matches()) {
                indexes.add(Integer.parseInt(matcher.group(1)));
            }
        }

        List<FileImportUpload> uploads = new ArrayList<>();
        for (Integer index : indexes) {
            MultipartFile file = firstFile(multipartFiles, "files[%d].file".formatted(index));
            String type = fields.getFirst("files[%d].type".formatted(index));
            uploads.add(new FileImportUpload(type, file));
        }
        return uploads;
    }

    private MultipartFile firstFile(MultiValueMap<String, MultipartFile> files, String key) {
        List<MultipartFile> values = files.get(key);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
}
