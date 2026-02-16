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

package es.nachobrito.vulcanodb.examples.rag.application.micronaut;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * @author nacho
 */
@Serdeable
public enum UserMessageType {
    @JsonProperty("reindex") REINDEX("reindex"),
    @JsonProperty("chat") CHAT("chat"),
    @JsonProperty("status") STATUS("status");

    private final String value;

    UserMessageType(String value) {
        this.value = value;
    }


    @Override
    public String toString() {
        return value;
    }
}
