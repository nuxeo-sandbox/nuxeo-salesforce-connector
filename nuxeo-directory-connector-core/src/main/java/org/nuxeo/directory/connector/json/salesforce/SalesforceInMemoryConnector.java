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

package org.nuxeo.directory.connector.json.salesforce;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jackson.JsonNode;
import org.nuxeo.directory.connector.ConnectorBasedDirectoryDescriptor;
import org.nuxeo.directory.connector.json.JsonInMemoryDirectoryConnector;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.oauth2.providers.OAuth2ServiceProvider;
import org.nuxeo.ecm.platform.oauth2.providers.OAuth2ServiceProviderRegistry;
import org.nuxeo.runtime.api.Framework;
import com.google.api.client.auth.oauth2.Credential;

/**
 * @author mgena
 *
 * @since 8.3
 */

public class SalesforceInMemoryConnector extends
        JsonInMemoryDirectoryConnector {

    protected Log log = LogFactory.getLog(SalesforceInMemoryConnector.class);
    protected Map<String, String> mapping;
	   
    @Override
    protected JsonNode call(String url) {
    	CloseableHttpClient httpClient = HttpClientBuilder.create().build();   	   		   	
    	String user = "Administrator";   	 	
    	HttpResponse response;
    	StringBuffer listBuffer = new StringBuffer();
    	
    	OAuth2ServiceProvider serviceProvider = Framework.getLocalService(OAuth2ServiceProviderRegistry.class).getProvider("Salesforce");
    	Credential credential = serviceProvider.loadCredential(user); 
		String token = credential.getAccessToken();
        String refreshToken = credential.getRefreshToken();				
		JsonNode json = refreshToken(serviceProvider.getTokenServerURL(), serviceProvider.getClientId(), serviceProvider.getClientSecret(), refreshToken);
		
		if(json != null && json.get("access_token") != null){
			token = json.get("access_token").getTextValue();
			String instanceUrl = json.get("instance_url").getTextValue();
			HttpGet getRequest = new HttpGet(instanceUrl+url);
			getRequest.addHeader("Authorization", "OAuth "+token);		
			try {
				response = httpClient.execute(getRequest);
				try {
		        	BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
					String output;
					
					while ((output = br.readLine()) != null) {
						listBuffer.append(output);
					}
		            return getMapper().readTree(listBuffer.toString());
				  } catch (Exception e) {
			            throw new NuxeoException("Error while reading JSON response", e);
			    }
			} catch (ClientProtocolException e1) {
				throw new NuxeoException("Error while reading response", e1);
			} catch (IOException e1) {
				throw new NuxeoException("Error while reading response", e1);
			}		
		}
		return null;		
    }
        
    protected JsonNode refreshToken(String url, String clientId, String clientSecret, String refreshToken){
    	StringBuffer listBuffer = new StringBuffer();
    	CloseableHttpClient httpClient = HttpClientBuilder.create().build(); 
    	HttpPost postRequest = new HttpPost(url);
    	postRequest.addHeader("Accept", "application/json");
    	List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
		urlParameters.add(new BasicNameValuePair("grant_type", "refresh_token"));
		urlParameters.add(new BasicNameValuePair("client_id", clientId));
		urlParameters.add(new BasicNameValuePair("client_secret", clientSecret));
		urlParameters.add(new BasicNameValuePair("refresh_token", refreshToken));	
    	try {
    		postRequest.setEntity( new UrlEncodedFormEntity(urlParameters));
    		HttpResponse response = httpClient.execute(postRequest);
    		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
    		 try {
    	        	BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
    				String output;
    				
    				while ((output = br.readLine()) != null) {
    					listBuffer.append(output);
    				}
    	            return getMapper().readTree(listBuffer.toString());
    	        } catch (Exception e) {
    	            throw new NuxeoException("Error while reading JSON response", e);
    	        }
    		}else{
    			log.error("Request for Refresh Token not accepted: " + response.toString());
    			return null;
    		}
		} catch (UnsupportedEncodingException e) {
			log.error("Error while getting response", e);
		} catch (ClientProtocolException e) {
			log.error("Error while getting response", e);
		} catch (IOException e) {
			log.error("Error while getting response", e);
		}

    	return null;
    }

    @Override
    protected JsonNode extractResult(JsonNode responseAsJson) {
    	if(responseAsJson == null){
    		return null;
    	}
        return responseAsJson.get("records");
    }
    
    @Override
    protected ArrayList<HashMap<String, Object>> getJsonStream() {
        ArrayList<HashMap<String, Object>> mapList = new ArrayList<HashMap<String, Object>>();
        JsonNode responseAsJson = call(params.get("url"));
        JsonNode resultsNode = extractResult(responseAsJson);
        if(resultsNode == null){
        	return mapList;
        }
        for (int i = 0; i < resultsNode.size(); i++) {
            Map<String, Object> map = new HashMap<String, Object>();
            String label = resultsNode.get(i).get(params.get("label")).toString().replaceAll("\"", "");
			map.put(params.get("label"), label);
			String id = resultsNode.get(i).get(params.get("id")).toString().replaceAll("\"", "");
			map.put(params.get("id"), id);
			mapList.add((HashMap<String, Object>) map);
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
    	if(params.get("autoRefresh") != null && params.get("autoRefresh").equals("true")){
    		results = this.getJsonStream();
    	}
        List<String> ids = new ArrayList<String>();
        String valueToFind = "";
        if(filter.containsKey("label")) {
            valueToFind = (String) filter.get("label");
            log.info("valueToFind ["+valueToFind+"]");
            if(valueToFind != null) {
                valueToFind = valueToFind.toLowerCase();
            }
        }
        String fieldName = params.get("label");
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
        return ids;
    }

    @Override
    public void init(ConnectorBasedDirectoryDescriptor descriptor) {
        super.init(descriptor);
        mapping = descriptor.getMapping();        
    }
}
