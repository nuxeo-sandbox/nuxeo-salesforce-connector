package org.nuxeo.oauth2;

/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Kevin Leturc
 */

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.oauth2.tokens.NuxeoOAuth2Token;
import org.nuxeo.runtime.api.Framework;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;

/**
 * We need to add some hook on {@link AuthorizationCodeFlow} as OneDrive for Business needs the resource parameter for
 * token and refresh token requests. Furthermore their response don't follow OAuth standard for expires_in field. See
 * https://github.com/google/google-oauth-java-client/issues/62
 * 
 * @since 8.2
 */
public class SalesforceAuthorizationCodeFlow extends AuthorizationCodeFlow {
	protected Log log = LogFactory.getLog(SalesforceAuthorizationCodeFlow.class);
	
	protected SalesforceAuthorizationCodeFlow(Builder builder) {
        super(builder);       
    }

    @Override
    public AuthorizationCodeTokenRequest newTokenRequest(String authorizationCode) {
    	return super.newTokenRequest(authorizationCode);                  
    }
    

    @Override
    public Credential loadCredential(String userId) throws IOException {
    	Credential credential = super.loadCredential(userId);    	
    	return credential;
    }
    
    
    @Override
	public Credential createAndStoreCredential(TokenResponse response, String userId) throws IOException {    		
    	Credential credential = super.createAndStoreCredential(response, userId);
    	Map<String, Object> additionalInfo = response.getUnknownKeys();
        String instanceUrl = (String)additionalInfo.get("instance_url");
    	
        Map<String, Serializable> filter = new HashMap<>();
        filter.put(NuxeoOAuth2Token.KEY_NUXEO_LOGIN, userId);
        filter.put("serviceName", "Salesforce");
		DocumentModelList entries = query(filter);
		if ((entries == null) || (entries.size() == 0)) {
			return null;
		}
		if (entries.size() > 1) {
			log.error("Found several tokens");
		}
		
		DocumentModel directory = entries.get(0);
		directory.setPropertyValue("instanceUrl", instanceUrl);
		CoreSession session = directory.getCoreSession();
		directory = session.saveDocument(directory);
		return credential;
	}

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

}