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

import com.sun.net.httpserver.HttpServer;
import es.nachobrito.vulcanodb.core.document.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author nacho
 */
class DownloadFileSupplierTest {

    private HttpServer server;
    private int port;
    private final AtomicInteger requestCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.setExecutor(null);
        server.start();
        requestCount.set(0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testSuccessfulDownload() throws Exception {
        byte[] expectedBytes = "hello world".getBytes(StandardCharsets.UTF_8);
        server.createContext("/test", exchange -> {
            exchange.sendResponseHeaders(200, expectedBytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(expectedBytes);
            }
        });

        URL url = new URI("http://localhost:" + port + "/test").toURL();
        TestDownloadFileSupplier supplier = new TestDownloadFileSupplier(url);
        supplier.initialize();
        Collection<Document> doc = supplier.getDocuments().map(Supplier::get).collect(Collectors.toSet());
        assertNotNull(doc);
        assertArrayEquals(expectedBytes, supplier.getLastBytes());
    }

    @Test
    void testRetryOnServerError() throws Exception {
        byte[] expectedBytes = "success after retry".getBytes(StandardCharsets.UTF_8);
        server.createContext("/retry", exchange -> {
            int count = requestCount.incrementAndGet();
            if (count < 3) {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            } else {
                exchange.sendResponseHeaders(200, expectedBytes.length);
                try (var os = exchange.getResponseBody()) {
                    os.write(expectedBytes);
                }
            }
        });

        URL url = new URI("http://localhost:" + port + "/retry").toURL();
        TestDownloadFileSupplier supplier = new TestDownloadFileSupplier(url);
        supplier.initialize();
        Collection<Document> doc = supplier.getDocuments().map(Supplier::get).collect(Collectors.toSet());
        assertNotNull(doc);
        assertEquals(3, requestCount.get());
        assertArrayEquals(expectedBytes, supplier.getLastBytes());
    }

    @Test
    void testFailAfterMaxRetries() throws Exception {
        server.createContext("/fail", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(503, 0);
            exchange.close();
        });

        URL url = new URI("http://localhost:" + port + "/fail").toURL();
        TestDownloadFileSupplier supplier = new TestDownloadFileSupplier(url);
        assertThrows(FileDownloadException.class, supplier::initialize);
        // Initial attempt + 3 retries = 4 total attempts
        assertEquals(4, requestCount.get());
    }

    @Test
    void testFailIfNotInitialized() throws Exception {
        URL url = new URI("http://localhost:" + port + "/fail").toURL();
        TestDownloadFileSupplier supplier = new TestDownloadFileSupplier(url);
        assertThrows(IllegalStateException.class, supplier::getDocuments);

    }

    @Test
    void testNonRetryableError() throws Exception {
        server.createContext("/404", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        });

        URL url = new URL("http://localhost:" + port + "/404");
        TestDownloadFileSupplier supplier = new TestDownloadFileSupplier(url);

        assertThrows(FileDownloadException.class, supplier::initialize);
        assertEquals(1, requestCount.get()); // Should not retry 4xx
    }

    @Test
    void testFollowRedirects() throws Exception {
        byte[] expectedBytes = "redirected content".getBytes(StandardCharsets.UTF_8);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/target");
            exchange.sendResponseHeaders(302, 0);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            exchange.sendResponseHeaders(200, expectedBytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(expectedBytes);
            }
        });

        URL url = new URI("http://localhost:" + port + "/redirect").toURL();
        TestDownloadFileSupplier supplier = new TestDownloadFileSupplier(url);
        supplier.initialize();
        Collection<Document> doc = supplier.getDocuments().map(Supplier::get).collect(Collectors.toSet());
        assertNotNull(doc);
        assertArrayEquals(expectedBytes, supplier.getLastBytes());
    }

    private static class TestDownloadFileSupplier extends DownloadFileSupplier {
        private byte[] lastBytes;

        public TestDownloadFileSupplier(URL url) {
            super(url, _ -> new float[]{}, HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build());
        }

        @Override
        protected Stream<Supplier<Document>> generateDocuments(byte[] bytes) {
            this.lastBytes = bytes;
            return Stream.of(() -> Document.builder().build());
        }

        public byte[] getLastBytes() {
            return lastBytes;
        }
    }
}
