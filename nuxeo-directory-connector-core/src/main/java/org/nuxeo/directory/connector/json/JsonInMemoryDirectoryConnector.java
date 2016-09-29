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
package org.nuxeo.directory.connector.json;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.nuxeo.directory.connector.ConnectorBasedDirectoryDescriptor;
import org.nuxeo.directory.connector.EntryConnector;
import org.nuxeo.directory.connector.InMemorySearchHelper;

public class JsonInMemoryDirectoryConnector extends BaseJSONDirectoryConnector implements EntryConnector {

    public ArrayList<HashMap<String, Object>> results;

    protected final InMemorySearchHelper searchHelper;

    protected String idField;

    public JsonInMemoryDirectoryConnector() {
        searchHelper = new InMemorySearchHelper(this);
    }

    protected JsonNode extractResult(JsonNode responseAsJson) {
        return responseAsJson.get("results");
    }

    protected ArrayList<HashMap<String, Object>> getJsonStream() {
        ArrayList<HashMap<String, Object>> mapList = new ArrayList<HashMap<String, Object>>();

        JsonNode responseAsJson = call(params.get("url"));

        JsonNode resultsNode = extractResult(responseAsJson);
        for (int i = 0; i < resultsNode.size(); i++) {
            try {
                Map<String, Object> map = new HashMap<String, Object>();
                map = readAsMap(resultsNode.get(i));
                mapList.add((HashMap<String, Object>) map);
            } catch (IOException e) {
                log.error("Error while mapping JSON to Map", e);
            }
        }
        return mapList;
    }

    public List<String> getEntryIds() {

        List<String> ids = new ArrayList<String>();
        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
                ids.add(results.get(i).get(idField).toString());
            }
        }
        return ids;
    }

    public Map<String, Object> getEntryMap(String id) {
        Map<String, Object> rc = new HashMap<String, Object>();
        rc = null;
        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i).get(idField).toString().equals(id)) {
                    rc = results.get(i);
                    break;
                }
            }
        }
        return rc;
    }

    public boolean hasEntry(String id) {
        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i).get(idField).equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void init(ConnectorBasedDirectoryDescriptor descriptor) {
        super.init(descriptor);
        results = this.getJsonStream();
        idField = descriptor.idField;

    }

    @Override
    public List<String> queryEntryIds(Map<String, Serializable> filter, Set<String> fulltext) {
        return searchHelper.queryEntryIds(filter, fulltext);
    }

}
