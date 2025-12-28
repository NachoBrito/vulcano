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

package es.nachobrito.vulcanodb.core.store.axon;

import java.util.Collections;
import java.util.List;

/**
 * @author nacho
 */
public record DocumentWriteResult(long internalId, boolean success, Throwable error,
                                  List<FieldWriteResult> fieldResults) {


    public static DocumentWriteResult ofError(Throwable error) {
        return new DocumentWriteResult(-1, false, error, Collections.emptyList());
    }

    public static DocumentWriteResult ofFieldResults(long internalId, List<FieldWriteResult> fieldResults) {
        return new DocumentWriteResult(
                internalId,
                fieldResults.stream().allMatch(FieldWriteResult::success),
                null,
                fieldResults
        );
    }
}
