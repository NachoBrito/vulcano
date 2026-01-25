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

package es.nachobrito.vulcanodb.supplier.pdf;

import es.nachobrito.vulcanodb.core.document.Document;
import es.nachobrito.vulcanodb.supplier.DownloadFileSupplier;
import es.nachobrito.vulcanodb.supplier.EmbeddingFunction;
import es.nachobrito.vulcanodb.supplier.FileProcessException;
import org.apache.pdfbox.Loader;

import java.io.IOException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A concrete implementation of {@link DownloadFileSupplier} for PDF files.
 * <p>
 * This supplier downloads a PDF from a remote URL, extracts its text content page by page,
 * converts the text into Markdown format using {@link PDFToMarkdownStripper},
 * and generates a {@link Document} for each page including extracted metadata.
 * </p>
 *
 * @author nacho
 */
public class DownloadPdfSupplier extends DownloadFileSupplier {

    /**
     * Constructs a new {@code DownloadPdfSupplier} with the specified URL and embedding function.
     *
     * @param url               the URL of the PDF file to download.
     * @param embeddingFunction the function used to generate embeddings from the PDF text.
     */
    public DownloadPdfSupplier(URL url, EmbeddingFunction embeddingFunction) {
        super(url, embeddingFunction);
    }

    /**
     * Extracts content and metadata from the provided PDF byte array and generates documents.
     * <p>
     * This implementation uses PDFBox to load the PDF and iterate through its pages.
     * For each page, it:
     * <ol>
     *     <li>Extracts text and formats it as Markdown.</li>
     *     <li>Generates a vector embedding for the page content.</li>
     *     <li>Extracts document metadata (Title, Author, etc.) and page-specific information.</li>
     *     <li>Builds a {@link Document} object.</li>
     * </ol>
     * </p>
     *
     * @param bytes the raw content of the downloaded PDF file.
     * @return a collection of {@link Document}s, one per PDF page.
     * @throws FileProcessException if an error occurs while parsing the PDF.
     */
    @Override
    protected Collection<Document> generateDocuments(byte[] bytes) {
        List<Document> documents = new ArrayList<>();
        try (var pdf = Loader.loadPDF(bytes)) {
            var information = pdf.getDocumentInformation();
            var stripper = new PDFToMarkdownStripper();
            int totalPages = pdf.getNumberOfPages();
            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                var pageText = stripper.getText(pdf);
                var embedding = embed(pageText);
                var builder = Document
                        .builder()
                        .withStringField("metadata.created", ZonedDateTime.now().toString())

                        .withStringField("metadata.page", String.valueOf(i))
                        .withStringField("metadata.totalpages", String.valueOf(totalPages));

                if (this.getUrl() != null) {
                    builder.withStringField("metadata.url", this.getUrl().toString());
                }
                if (information.getTitle() != null && !information.getTitle().isEmpty()) {
                    builder.withStringField("metadata.title", information.getTitle());
                }
                if (information.getAuthor() != null && !information.getAuthor().isEmpty()) {
                    builder.withStringField("metadata.author", information.getAuthor());
                }
                if (information.getSubject() != null && !information.getSubject().isEmpty()) {
                    builder.withStringField("metadata.subject", information.getSubject());
                }
                if (information.getKeywords() != null && !information.getKeywords().isEmpty()) {
                    builder.withStringField("metadata.keywords", information.getKeywords());
                }
                if (information.getCreator() != null && !information.getCreator().isEmpty()) {
                    builder.withStringField("metadata.creator", information.getCreator());
                }
                if (information.getProducer() != null && !information.getProducer().isEmpty()) {
                    builder.withStringField("metadata.producer", information.getProducer());
                }
                if (information.getCreationDate() != null) {
                    builder.withStringField("metadata.creationDate", information.getCreationDate().toString());
                }
                if (information.getModificationDate() != null) {
                    builder.withStringField("metadata.modificationDate", information.getModificationDate().toString());
                }
                if (information.getTrapped() != null) {
                    builder.withStringField("metadata.trapped", information.getTrapped());
                }

                for (String key : information.getMetadataKeys()) {
                    String value = information.getCustomMetadataValue(key);
                    if (value != null && !value.isEmpty()) {
                        builder.withStringField("metadata." + key, value);
                    }
                }

                documents.add(builder
                        .withVectorField("embedding", embedding)
                        .build());
            }
        } catch (IOException e) {
            throw new FileProcessException(e);
        }
        return documents;
    }
}
