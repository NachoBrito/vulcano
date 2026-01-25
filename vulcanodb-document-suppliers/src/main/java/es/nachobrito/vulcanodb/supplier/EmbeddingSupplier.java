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

import es.nachobrito.vulcanodb.core.ingestion.DocumentSupplier;

/**
 * Base class for {@link DocumentSupplier}s that require text-to-vector embedding capabilities.
 * <p>
 * This abstract class provides a common foundation for suppliers that need to transform
 * extracted text into embeddings using an {@link EmbeddingFunction}.
 * </p>
 *
 * @author nacho
 */
public abstract class EmbeddingSupplier implements DocumentSupplier {

    /**
     * The function used to generate embeddings from text.
     */
    private final EmbeddingFunction embeddingFunction;

    /**
     * Constructs an {@code EmbeddingSupplier} with the given embedding function.
     *
     * @param embeddingFunction the function to use for text embedding.
     */
    protected EmbeddingSupplier(EmbeddingFunction embeddingFunction) {
        this.embeddingFunction = embeddingFunction;
    }

    /**
     * Transforms the given text into a vector embedding.
     *
     * @param text the text to embed.
     * @return the resulting vector embedding as a float array.
     */
    protected float[] embed(String text) {
        return embeddingFunction.apply(text);
    }
}
