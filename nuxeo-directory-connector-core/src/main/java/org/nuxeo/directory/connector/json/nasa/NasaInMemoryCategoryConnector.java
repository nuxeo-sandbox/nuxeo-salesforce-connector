/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thierry Delprat
 */
package org.nuxeo.directory.connector.json.nasa;

import java.util.Map;

import org.codehaus.jackson.JsonNode;
import org.nuxeo.directory.connector.json.JsonInMemoryDirectoryConnector;

public class NasaInMemoryCategoryConnector extends JsonInMemoryDirectoryConnector {

    @Override
    protected JsonNode extractResult(JsonNode responseAsJson) {
        return responseAsJson.get("categories");
    }

    @Override
    public Map<String, Object> getEntryMap(String id) {
        Map<String, Object> entry = super.getEntryMap(id);
        // add the obsolete flag so that the default directory filters will work
        entry.put("obsolete", new Long(0));
        return entry;
    }
}
