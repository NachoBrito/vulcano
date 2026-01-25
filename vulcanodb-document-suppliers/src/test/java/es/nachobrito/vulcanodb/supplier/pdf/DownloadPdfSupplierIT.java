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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author nacho
 */
class DownloadPdfSupplierIT {

    @Test
    void expectPdfsProcessed() throws IOException {

        byte[] data = this.getClass().getClassLoader()
                .getResourceAsStream("1603.09320v4.pdf").readAllBytes();
        var supplier = new DownloadPdfSupplier(null, _ -> new float[]{});
        var documents = supplier.generateDocuments(data);

        assertNotNull(documents);
        assertEquals(13, documents.size());
    }

}