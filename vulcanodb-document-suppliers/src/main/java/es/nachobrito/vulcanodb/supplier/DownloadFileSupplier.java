/*
 *    Copyright 2025 Nacho Brito
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package es.nachobrito.vulcanodb.supplier;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.core.ingestion.DocumentSupplier;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;

/**
 * Base class for {@link DocumentSupplier}s that obtain content by downloading files from a remote URL.
 * <p>
 * This class provides a robust implementation of the file download process, including:
 * <ul>
 *     <li>Automatic retry mechanism for transient server errors (HTTP 5xx) and network issues.</li>
 *     <li>Configurable timeouts for connection and requests.</li>
 *     <li>Automatic redirection following.</li>
 *     <li>Standard HTTP status code validation.</li>
 * </ul>
 * </p>
 *
 * @author nacho
 */
public abstract class DownloadFileSupplier extends EmbeddingSupplier {
    /**
     * The maximum number of retry attempts for transient errors.
     */
    private static final int MAX_RETRIES = 3;

    /**
     * The timeout duration for both connection and request execution.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * The remote URL from which the file will be downloaded.
     */
    private final URL url;

    /**
     * The HTTP client used to perform the download.
     */
    private final HttpClient httpClient;

    /**
     * Constructs a new {@code DownloadFileSupplier} with the specified URL and embedding function.
     *
     * @param url               the URL of the file to download.
     * @param embeddingFunction the function used to generate embeddings from extracted text.
     */
    public DownloadFileSupplier(URL url, EmbeddingFunction embeddingFunction, HttpClient httpClient) {
        super(embeddingFunction);
        this.url = url;
        this.httpClient = httpClient;
    }

    /**
     * Downloads the file and builds a {@link Document} from its content.
     *
     * @return the built document.
     * @throws FileDownloadException if the file cannot be downloaded after the maximum number of retries,
     *                               or if a non-retryable error occurs.
     */
    @Override
    public Collection<Document> get() {
        try {
            var bytes = downloadFile();
            return generateDocuments(bytes);
        } catch (IOException | InterruptedException | URISyntaxException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new FileDownloadException(e);
        }
    }

    /**
     * Performs the actual file download using the internal HTTP client.
     * <p>
     * This method implements a retry loop. If an {@link IOException} occurs or an HTTP 5xx
     * status code is returned, it will retry the operation after a short delay, up to {@link #MAX_RETRIES} times.
     * </p>
     *
     * @return the content of the downloaded file as a byte array.
     * @throws IOException          if an I/O error occurs during the download process and retries are exhausted.
     * @throws InterruptedException if the current thread is interrupted while waiting between retries.
     * @throws URISyntaxException   if the provided URL cannot be converted to a URI.
     */
    private byte[] downloadFile() throws IOException, InterruptedException, URISyntaxException {
        var request = HttpRequest.newBuilder()
                .uri(url.toURI())
                .timeout(TIMEOUT)
                .GET()
                .build();

        for (int attempt = 0; ; attempt++) {
            try {
                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    return response.body();
                }

                if (status >= 400 && status < 500 || attempt >= MAX_RETRIES) {
                    throw new IOException("HTTP error: " + status);
                }
            } catch (IOException e) {
                if (attempt >= MAX_RETRIES || e.getMessage() != null && e.getMessage().startsWith("HTTP error: 4")) {
                    throw e;
                }
            }
            Thread.sleep(1000L * (attempt + 1));
        }
    }

    /**
     * Returns the URL of the remote file.
     *
     * @return the file URL.
     */
    protected URL getUrl() {
        return url;
    }

    /**
     * Builds a collection of {@link Document}s from the raw bytes of the downloaded file.
     * <p>
     * Subclasses must implement this method to process the specific file format (e.g., PDF, TXT)
     * and extract the necessary metadata and content.
     * </p>
     *
     * @param bytes the raw content of the downloaded file.
     * @return the constructed document.
     */
    protected abstract Collection<Document> generateDocuments(byte[] bytes);
}
