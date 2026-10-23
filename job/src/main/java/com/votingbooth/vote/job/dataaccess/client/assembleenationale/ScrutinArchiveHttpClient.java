package com.votingbooth.vote.job.dataaccess.client.assembleenationale;

import com.votingbooth.vote.job.model.scrutin.ArchiveDownload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class ScrutinArchiveHttpClient implements ScrutinArchiveClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // Streamed straight to disk: the archive is never held in memory.
    @Override
    public ArchiveDownload download(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).GET().build();
        Path file = null;
        try {
            file = Files.createTempFile("scrutins-", ".zip");
            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(file));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Downloading " + url + " returned HTTP " + response.statusCode());
            }
            LocalDateTime lastModified = response.headers().firstValue("Last-Modified")
                    .map(ScrutinArchiveHttpClient::parseHttpDate)
                    .orElse(null);
            return new ArchiveDownload(file, lastModified);
        } catch (IOException e) {
            deleteQuietly(file);
            throw new UncheckedIOException("Cannot download " + url, e);
        } catch (InterruptedException e) {
            deleteQuietly(file);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading " + url, e);
        } catch (RuntimeException e) {
            deleteQuietly(file);
            throw e;
        }
    }

    private static LocalDateTime parseHttpDate(String value) {
        return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A leftover temp file isn't worth hiding the original error.
        }
    }

}
