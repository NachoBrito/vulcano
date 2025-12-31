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

package es.nachobrito.vulcanodb.core.util;

import java.io.File;

/**
 * @author nacho
 */
public interface FileUtils {
    static String toLegalFileName(String string) {
        return string.replaceAll("[^a-zA-Z0-9\\._]+", "_");
    }

    static void deleteRecursively(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            var files = fileOrDirectory.listFiles();
            if (files != null) {
                // Recursively delete contents of the directory
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        // Delete the file or empty directory
        if (!fileOrDirectory.delete()) {
            System.err.println("Failed to delete: " + fileOrDirectory.getAbsolutePath());
        }
    }
}
