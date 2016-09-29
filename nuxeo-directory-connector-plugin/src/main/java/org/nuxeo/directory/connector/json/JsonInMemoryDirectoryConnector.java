package org.nuxeo.directory.connector.json;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.JsonNode;
import org.nuxeo.directory.connector.ConnectorBasedDirectoryDescriptor;
import org.nuxeo.directory.connector.EntryConnector;
import org.nuxeo.directory.connector.InMemorySearchHelper;
import org.nuxeo.ecm.core.api.NuxeoException;

public class JsonInMemoryDirectoryConnector extends BaseJSONDirectoryConnector
        implements EntryConnector {

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
        log.info("idField "+idField);
        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
            	HashMap<String, Object> result = results.get(i);
            	HashMap<String, Object> resultPart1 = (HashMap<String, Object>)result.get("451");
            	HashMap<String, Object> resultPart2 = (HashMap<String, Object>)resultPart1.get("");
            	HashMap<String, Object> resultPart3 = (HashMap<String, Object>)resultPart2.get("");
            	ids.add(resultPart3.get("value").toString());
            	//ids.add(results.get(i).get(idField).toString());                
            }
        }
        return ids;
    }

    public Map<String, Object> getEntryMap(String id) {
        Map<String, Object> rc = new HashMap<String, Object>();
        rc = null;
        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
            	
            	HashMap<String, Object> result = results.get(i);
            	HashMap<String, Object> resultPart1 = (HashMap<String, Object>)result.get("451");
            	HashMap<String, Object> resultPart2 = (HashMap<String, Object>)resultPart1.get("");
            	HashMap<String, Object> resultPart3 = (HashMap<String, Object>)resultPart2.get("");
            	if (resultPart3.get("value").toString().equals(id)) {
                //if (results.get(i).get(idField).toString().equals(id)) {
                    //rc = results.get(i);
            		
            		HashMap<String, Object> result305Part1 = (HashMap<String, Object>)result.get("305");
                	HashMap<String, Object> result305Part2 = (HashMap<String, Object>)result305Part1.get("");
                	HashMap<String, Object> result305Part3 = (HashMap<String, Object>)result305Part2.get("");
                	HashMap<String, Object> finalResult = new HashMap();
                	finalResult.put("common_name", result305Part3.get("value").toString());
            		rc = finalResult;
                    break;
                }
            }
        }
        return rc;
    }

    public boolean hasEntry(String id) throws NuxeoException {
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
        idField = descriptor.getIdField();

    }

    @Override
    public List<String> queryEntryIds(Map<String, Serializable> filter,
            Set<String> fulltext) {
        return searchHelper.queryEntryIds(filter, fulltext);
    }

}
