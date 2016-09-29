/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thibaud Arguillere (nuxeo)
 */

package org.nuxeo.directory.connector.json.akeneo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codehaus.jackson.JsonNode;
import org.nuxeo.directory.connector.ConnectorBasedDirectoryDescriptor;
import org.nuxeo.directory.connector.json.JsonInMemoryDirectoryConnector;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * @author mgena
 *
 * @since 5.9.4
 */

public class AkeneoInMemoryConnector extends
        JsonInMemoryDirectoryConnector {

    protected Log log = LogFactory.getLog(AkeneoInMemoryConnector.class);
    protected Map<String, String> mapping;

    @Override
    protected JsonNode call(String url) {
    	String proxyHost = params.get("proxyHost");
    	String proxyPort = params.get("proxyPort");
    	System.setProperty("http.proxyHost", proxyHost);
    	System.setProperty("http.proxyPort", proxyPort);
    	CloseableHttpClient httpClient = HttpClientBuilder.create().build();
    	HttpGet getRequest = new HttpGet(url);
    	getRequest.addHeader("accept", "application/json");
    	HttpResponse response;
    	StringBuffer productListBuffer = new StringBuffer();
		try {
			response = httpClient.execute(getRequest);
		} catch (Exception e1) {
			 throw new NuxeoException("Error while getting response", e1);
		}

        try {
        	BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
			String output;
			
			while ((output = br.readLine()) != null) {
				productListBuffer.append(output);
			}
            return getMapper().readTree(productListBuffer.toString());
        } catch (Exception e) {
            throw new NuxeoException("Error while reading JSON response", e);
        }
    }
 

    @Override
    protected JsonNode extractResult(JsonNode responseAsJson) {
    	log.info("get result: "+responseAsJson.get("products"));
        return responseAsJson.get("products");
    }
    
    @Override
    protected ArrayList<HashMap<String, Object>> getJsonStream() {
        ArrayList<HashMap<String, Object>> mapList = new ArrayList<HashMap<String, Object>>();

        JsonNode responseAsJson = call(params.get("url"));

        JsonNode resultsNode = extractResult(responseAsJson);
        for (int i = 0; i < resultsNode.size(); i++) {
            try {
                Map<String, Object> map = new HashMap<String, Object>();
                map = readAsMap(resultsNode.get(i).get("attributes"));
                mapList.add((HashMap<String, Object>) map);
            } catch (IOException e) {
                log.error("Error while mapping JSON to Map", e);
            }
        }
        return mapList;
    }

    @Override
    public Map<String, Object> getEntryMap(String id) {
        Map<String, Object> entry = super.getEntryMap(id);
        // add the obsolete flag so that the default directory filters will work
        entry.put("obsolete", new Long(0));
        return entry;
    }


    @Override
    public List<String> queryEntryIds(Map<String, Serializable> filter,
            Set<String> fulltext) {

        List<String> ids = new ArrayList<String>();

        String valueToFind = "";

        log.info("filter "+filter.toString());
        if(filter.containsKey("label")) {
            valueToFind = (String) filter.get("label");
            log.info("valueToFind ["+valueToFind+"]");
            if(valueToFind != null) {
                valueToFind = valueToFind.toLowerCase();
            }
        }
        log.info("mapping ["+mapping.get("label")+"]");
        String fieldName = mapping.get("label");
        // do the search
        data_loop: for (String id : getEntryIds()) {

            Map<String, Object> map = getEntryMap(id);
            Object value = map.get(fieldName);
            
            if(value == null) {
                continue data_loop;
            }

            if( value.toString().toLowerCase().indexOf(valueToFind) < 0) {
                continue data_loop;
            }

            ids.add(id);
        }
        log.info("ids size ["+ids.size()+"]");
        log.info("ids ["+ids.toString()+"]");
        return ids;
    }
    

    @Override
    public void init(ConnectorBasedDirectoryDescriptor descriptor) {
        super.init(descriptor);
        mapping = descriptor.getMapping();
        
    }
}
