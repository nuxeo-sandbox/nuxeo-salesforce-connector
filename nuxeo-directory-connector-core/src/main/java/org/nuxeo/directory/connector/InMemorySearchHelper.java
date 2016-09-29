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
package org.nuxeo.directory.connector;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

/**
 * This is a helper class that provides a simplistic implementation of the directory search API using a full in memory
 * search. This can be useful for testing service that does not support any search feature. However, please note that
 * this can not scale !
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 */
public class InMemorySearchHelper {

    protected final EntryConnector connector;

    public InMemorySearchHelper(EntryConnector connector) {
        this.connector = connector;
    }

    public List<String> queryEntryIds(Map<String, Serializable> filter, Set<String> fulltext) {

        List<String> ids = new ArrayList<String>();

        // do the search
        data_loop: for (String id : connector.getEntryIds()) {

            Map<String, Object> map = connector.getEntryMap(id);
            for (Entry<String, Serializable> e : filter.entrySet()) {
                String fieldName = e.getKey();
                Object expected = e.getValue();
                Object value = map.get(fieldName);
                if (value == null) {
                    if (expected != null) {
                        continue data_loop;
                    }
                } else {
                    if (fulltext != null && fulltext.contains(fieldName)) {
                        if (!value.toString().toLowerCase().startsWith(expected.toString().toLowerCase())) {
                            continue data_loop;
                        }
                    } else {
                        if (!value.equals(expected)) {
                            continue data_loop;
                        }
                    }
                }
            }
            // this entry matches
            ids.add(id);
        }
        return ids;
    }

}
