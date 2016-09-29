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

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.JsonNode;
import org.nuxeo.directory.connector.json.BaseJSONDirectoryConnector;

public class NasaDataSetConnector extends BaseJSONDirectoryConnector {

    @Override
    public boolean hasEntry(String id) {
        return getEntryMap(id) != null;
    }

    @Override
    public Map<String, Object> getEntryMap(String id) {

        String getDataSetUrl = params.get("url") + "get_dataset?id=" + id;
        JsonNode responseAsJson = call(getDataSetUrl);

        JsonNode result = responseAsJson.get("post");

        try {
            return readAsMap(result);
        } catch (IOException e) {
            log.error("Unable to handle mapping from JSON", e);
            return null;
        }
    }

    @Override
    public List<String> getEntryIds() {
        return new ArrayList<String>();
    }

    @Override
    public List<String> queryEntryIds(Map<String, Serializable> filter, Set<String> fulltext) {

        if (filter.containsKey("category")) {
            String getDataSetUrl = params.get("url") + "get_category_datasets/?id=" + filter.get("category");
            JsonNode responseAsJson = call(getDataSetUrl);

            JsonNode result = responseAsJson.get("posts");

            List<String> ids = new ArrayList<>();

            for (int i = 0; i < result.size(); i++) {
                ids.add(result.get(i).get("id").getValueAsText());
            }
            return ids;
        }
        return null;
    }

}
