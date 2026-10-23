package com.votingbooth.vote.job.utils;

import com.votingbooth.vote.job.model.scrutin.ArchiveEntry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class ScrutinArchiveReader {

    private static final String PREFIX = "json/";
    private static final String EXTENSION = ".json";

    // Reads every json/<uid>.json straight from the zip, one file at a time, without extracting it.
    public void forEach(Path archive, Consumer<ArchiveEntry> consumer) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(PREFIX) || !name.endsWith(EXTENSION)) {
                    continue;
                }
                String externalId = name.substring(PREFIX.length(), name.length() - EXTENSION.length());
                if (externalId.contains("/")) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    consumer.accept(new ArchiveEntry(externalId, in.readAllBytes()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read archive " + archive, e);
        }
    }

}
