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

/**
 * Exception thrown when a file download operation fails.
 * <p>
 * This exception is used by {@link DownloadFileSupplier} and its subclasses to signal
 * errors that occur during the retrieval of remote resources, such as network timeouts,
 * connection failures, or unexpected HTTP status codes after retries are exhausted.
 * </p>
 *
 * @author nacho
 */
public class FileDownloadException extends RuntimeException {
    /**
     * Constructs a new FileDownloadException with the specified cause.
     *
     * @param cause the cause of the download failure
     */
    public FileDownloadException(Throwable cause) {
        super(cause);
    }
}
