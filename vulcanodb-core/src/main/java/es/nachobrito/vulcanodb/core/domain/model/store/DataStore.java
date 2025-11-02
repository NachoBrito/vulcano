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

package es.nachobrito.vulcanodb.core.domain.model.store;

import es.nachobrito.vulcanodb.core.domain.model.document.Document;
import es.nachobrito.vulcanodb.core.domain.model.query.Query;
import es.nachobrito.vulcanodb.core.domain.model.result.Result;

/**
 * @author nacho
 */
public interface DataStore {

    /**
     * Adds a new document to the store. If a document with the same id exists, it will be replaced by this one.
     *
     * @param document the document to add
     */
    void add(Document document);

    /**
     * Finds documents matching the provided query.
     *
     * @param query the query
     * @return the result containing documents matching the query
     */
    Result search(Query query);
}
