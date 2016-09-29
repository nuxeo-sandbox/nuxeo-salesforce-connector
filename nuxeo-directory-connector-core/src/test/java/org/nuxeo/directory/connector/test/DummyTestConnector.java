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
package org.nuxeo.directory.connector.test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nuxeo.directory.connector.AbstractEntryConnector;
import org.nuxeo.directory.connector.ConnectorBasedDirectoryDescriptor;
import org.nuxeo.directory.connector.EntryConnector;
import org.nuxeo.directory.connector.InMemorySearchHelper;

public class DummyTestConnector extends AbstractEntryConnector implements EntryConnector {

    protected Map<String, String> params;

    protected final InMemorySearchHelper searchHelper;

    public DummyTestConnector() {
        super();
        searchHelper = new InMemorySearchHelper(this);
    }

    public List<String> getEntryIds() {
        List<String> ids = new ArrayList<String>();
        ids.addAll(params.keySet());
        return ids;
    }

    public Map<String, Object> getEntryMap(String id) {

        Map<String, Object> map = null;

        String data = params.get(id);
        if (data != null) {
            map = new HashMap<String, Object>();
            String[] parts = data.split("\\|");
            for (String part : parts) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    map.put(kv[0], kv[1]);
                }
            }
        }
        return map;
    }

    @Override
    public List<String> queryEntryIds(Map<String, Serializable> filter, Set<String> fulltext) {
        return searchHelper.queryEntryIds(filter, fulltext);
    }

    public boolean hasEntry(String id) {
        return params.keySet().contains(id);
    }

    @Override
    public void init(ConnectorBasedDirectoryDescriptor descriptor) {
        super.init(descriptor);
        params = descriptor.getParameters();
    }

    @Override
    public Set<String> getFullTextConfig() {
        Set<String> ft = new HashSet<String>();

        ft.add("username");

        return ft;
    }

}
