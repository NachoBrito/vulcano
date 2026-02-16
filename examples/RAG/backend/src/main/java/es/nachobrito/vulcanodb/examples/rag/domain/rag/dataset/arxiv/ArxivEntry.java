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

package es.nachobrito.vulcanodb.examples.rag.domain.rag.dataset.arxiv;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * @author nacho
 */
@Serdeable
public record ArxivEntry(
        @JsonProperty("id")
        String id,
        @JsonProperty("submitter")
        String submitter,
        @JsonProperty("authors")
        String authors,
        @JsonProperty("title")
        String title,
        @JsonProperty("comments")
        String comments,
        @JsonProperty("journal-ref")
        String journalRef,
        @JsonProperty("doi")
        String doi,
        @JsonProperty("report-no")
        String reportNo,
        @JsonProperty("categories")
        String categories,
        @JsonProperty("license")
        String license,
        @JsonProperty("abstract")
        String abstractText,
        @JsonProperty("versions")
        List<ArxivVersion> versions,
        @JsonProperty("update_date")
        String updateDate,
        @JsonProperty("authors_parsed")
        List<List<String>> authorsParsed
) {
}
