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
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.oauth2.providers.OAuth2ServiceProvider;
import org.nuxeo.ecm.platform.oauth2.providers.OAuth2ServiceProviderRegistry;
import org.nuxeo.ecm.platform.oauth2.tokens.NuxeoOAuth2Token;
import org.nuxeo.runtime.api.Framework;

import com.google.api.client.auth.oauth2.Credential;

/**
 * @author mgena
 *
 * @since 5.9.4
 */

public class SalesforceInMemoryConnector extends
        JsonInMemoryDirectoryConnector {

    protected Log log = LogFactory.getLog(SalesforceInMemoryConnector.class);
    protected Map<String, String> mapping;
	
    
    protected DocumentModelList query(Map<String, Serializable> filter) {
		DirectoryService ds = (DirectoryService) Framework.getLocalService(DirectoryService.class);
		Session session = ds.open("oauth2Tokens");
		Throwable localThrowable3 = null;
		try {
			DocumentModelList localDocumentModelList = session.query(filter);
			return localDocumentModelList;
		} catch (Throwable localThrowable4) {
		} finally {
			if (session != null)
				if (localThrowable3 != null)
					try {
						session.close();
					} catch (Throwable localThrowable2) {
						localThrowable3.addSuppressed(localThrowable2);
					}
				else
					session.close();
		}
		return null;
    }
    
    @Override
    protected JsonNode call(String url) {
 
    	CloseableHttpClient httpClient = HttpClientBuilder.create().build();   	   	
    	Map<String, Serializable> filter = new HashMap<>();
        filter.put(NuxeoOAuth2Token.KEY_NUXEO_LOGIN, "Administrator");
        filter.put("serviceName", "Salesforce");
		DocumentModelList entries = query(filter);
		if ((entries == null) || (entries.size() == 0)) {
			return null;
		}
		if (entries.size() > 1) {
			log.error("Found several tokens");
		}
		
		DocumentModel directory = entries.get(0);
		String instanceUrl = (String)directory.getPropertyValue("instanceUrl");
    	if( instanceUrl == null){
    		instanceUrl = "https://cs86.salesforce.com";
    	}
    	HttpGet getRequest = new HttpGet(instanceUrl+url);
    	//getRequest.addHeader("accept", "application/json");
    	String user = "Administrator";
    	String token = getAccessToken(user);
    	getRequest.addHeader("Authorization", "OAuth "+token);
    	HttpResponse response;
    	StringBuffer listBuffer = new StringBuffer();
    	
    	try {		
			response = httpClient.execute(getRequest);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {	
				OAuth2ServiceProvider serviceProvider = Framework.getLocalService(OAuth2ServiceProviderRegistry.class).getProvider("Salesforce");
				Credential credential = serviceProvider.loadCredential(user); 
		        String refreshToken = credential.getAccessToken();				
				JsonNode json = refreshToken(serviceProvider.getAuthorizationServerURL(), serviceProvider.getClientId(), serviceProvider.getClientSecret(), refreshToken);
				//String accessToken = (String)json.get(0).get("access_token");
			}	
			
		} catch (Exception e1) {
			log.error("Error while getting response", e1);
			return null;
			
		}
    	
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
    }
    
    
    protected JsonNode refreshToken(String url, String clientId, String clientSecret, String refreshToken){
    	StringBuffer listBuffer = new StringBuffer();
    	CloseableHttpClient httpClient = HttpClientBuilder.create().build(); 
    	HttpPost postRequest = new HttpPost(url);
    	postRequest.addHeader("accept", "application/json");
    	List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
		urlParameters.add(new BasicNameValuePair("grant_type", "refresh_token"));
		urlParameters.add(new BasicNameValuePair("client_id", clientId));
		urlParameters.add(new BasicNameValuePair("client_secret", clientSecret));
		urlParameters.add(new BasicNameValuePair("refresh_token", refreshToken));
	
    	try {
    		postRequest.setEntity( new UrlEncodedFormEntity(urlParameters));
    		HttpResponse response = httpClient.execute(postRequest);
    		
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
    		
		} catch (UnsupportedEncodingException e) {
			log.error("Error while getting response", e);
		} catch (ClientProtocolException e) {
			log.error("Error while getting response", e);
		} catch (IOException e) {
			log.error("Error while getting response", e);
		}

    	return null;
    }
 
    protected String getAccessToken(String user) {
        OAuth2ServiceProvider serviceProvider = Framework.getLocalService(OAuth2ServiceProviderRegistry.class).getProvider("Salesforce");
        Credential credential = serviceProvider.loadCredential(user);
        
        //credential
        
        if (credential != null) {
            String accessToken = credential.getAccessToken();
            if (accessToken != null) {
                return accessToken;
            }
        }
        return null;
    }

    @Override
    protected JsonNode extractResult(JsonNode responseAsJson) {
    	//log.info("get result: "+responseAsJson.get("records"));
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
        log.info("mapping ["+params.get("label")+"]");
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
