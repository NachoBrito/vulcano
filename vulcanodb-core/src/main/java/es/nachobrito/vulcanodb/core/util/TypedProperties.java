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

import java.util.Properties;

/**
 * @author nacho
 */
public class TypedProperties {
    private final Properties properties;

    public TypedProperties() {
        this.properties = new Properties();
    }

    public TypedProperties(Properties properties) {
        this.properties = properties;
    }

    public String getString(String key, String defValue) {
        return properties.getProperty(key, defValue);
    }

    public long getLong(String key, long defValue) {
        if (!properties.contains(key)) {
            return defValue;
        }
        try {
            return Long.parseLong(properties.getProperty(key));
        } catch (NumberFormatException e) {
            return defValue;
        }
    }
}
