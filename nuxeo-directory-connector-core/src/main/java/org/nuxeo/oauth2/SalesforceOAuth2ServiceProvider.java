package org.nuxeo.oauth2;

/*
 * (C) Copyright 2006-2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nelson Silva
 */


import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.oauth2.providers.OAuth2ServiceProvider;
import org.nuxeo.ecm.platform.oauth2.providers.OAuth2ServiceUserStore;
import org.nuxeo.ecm.platform.oauth2.tokens.NuxeoOAuth2Token;
import org.nuxeo.ecm.platform.oauth2.tokens.OAuth2TokenStore;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;
import org.nuxeo.runtime.api.Framework;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.Credential.AccessMethod;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;

public class SalesforceOAuth2ServiceProvider implements OAuth2ServiceProvider {
	
	protected Log log = LogFactory.getLog(SalesforceOAuth2ServiceProvider.class);
	
    public static final String SCHEMA = "oauth2ServiceProvider";

    /** Global instance of the HTTP transport. */
    protected static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    /** Global instance of the JSON factory. */
    protected static final JsonFactory JSON_FACTORY = new JacksonFactory();

    public static final String CODE_URL_PARAMETER = "code";

    public static final String ERROR_URL_PARAMETER = "error";

    protected String serviceName;

    protected Long id;

    private String tokenServerURL;

    private String authorizationServerURL;

    private String clientId;

    private String clientSecret;

    private List<String> scopes;

    private boolean enabled;

    protected OAuth2ServiceUserStore serviceUserStore;

    protected OAuth2TokenStore tokenStore;

    @Override
    public String getAuthorizationUrl(HttpServletRequest request) {
        return getAuthorizationCodeFlow()
            .newAuthorizationUrl()
            .setRedirectUri(getCallbackUrl(request))
            .build();
    }

    protected String getCallbackUrl(HttpServletRequest request) {
        String serverURL = VirtualHostHelper.getBaseURL(request);

        if (serverURL.endsWith("/")) {
            serverURL = serverURL.substring(0, serverURL.length() - 1);
        }

        return serverURL + "/site/oauth2/" + serviceName + "/callback";
    }

    @Override
    public Credential handleAuthorizationCallback(HttpServletRequest request) {

        // Checking if there was an error such as the user denied access
        String error = getError(request);
        if (error != null) {
            throw new NuxeoException("There was an error: \"" + error + "\".");
        }

        // Checking conditions on the "code" URL parameter
        String code = getAuthorizationCode(request);
        if (code == null) {
            throw new NuxeoException("There is not code provided as QueryParam.");
        }

        try {
        	AuthorizationCodeFlow flow = getAuthorizationCodeFlow();

            String redirectUri = getCallbackUrl(request);

            TokenResponse tokenResponse = flow.newTokenRequest(code)
                .setScopes(scopes.isEmpty() ? null : scopes) // some providers do not support the 'scopes' param
                .setRedirectUri(redirectUri).execute();
            
            //tokenResponse.set
            // Create a unique userId to use with the credential store
            String userId = getOrCreateServiceUser(request, tokenResponse.getAccessToken());
            
            Credential credential = flow.createAndStoreCredential(tokenResponse, userId);
            
            
            Map<String, Object> additionalInfo = tokenResponse.getUnknownKeys();
            String instanceUrl = (String)additionalInfo.get("instance_url");
        	
            Map<String, Serializable> filter = new HashMap<>();
            filter.put("id", userId);
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
    		DirectoryService ds = (DirectoryService) Framework.getLocalService(DirectoryService.class);
    		
    		
    		Session session = ds.open("oauth2Tokens");
    		session.updateEntry(directory);
            
    		return credential;
            
        } catch (IOException e) {
            throw new NuxeoException("Failed to retrieve credential", e);
        }
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

    /**
     * Load a credential from the token store with the userId returned by getServiceUser() as key.
     */
    @Override
    public Credential loadCredential(String user) {
        String userId = getServiceUserId(user);
        try {
            return userId != null ? getAuthorizationCodeFlow().loadCredential(userId) : null;
        } catch (IOException e) {
            throw new NuxeoException("Failed to load credential for " + user, e);
        }
    }

    /**
     * Returns the userId to use for token entries.
     * Should be overriden by subclasses wanting to rely on a different field as key.
     */
    protected String getServiceUserId(String key) {
        Map<String, Serializable> filter = new HashMap<>();
        filter.put(NuxeoOAuth2Token.KEY_NUXEO_LOGIN, key);
        return getServiceUserStore().find(filter);
    }

    /**
     * Retrieves or creates a service user.
     * Should be overriden by subclasses wanting to rely on a different field as key.
     */
    protected String getOrCreateServiceUser(HttpServletRequest request, String accessToken) throws IOException {
        String nuxeoLogin = request.getUserPrincipal().getName();
        String userId = getServiceUserId(nuxeoLogin);
        if (userId == null) {
            userId = getServiceUserStore().store(nuxeoLogin);
        }   

        return userId;
    }

    public AuthorizationCodeFlow getAuthorizationCodeFlow() {
        Credential.AccessMethod method = BearerToken.authorizationHeaderAccessMethod();
        GenericUrl tokenServerUrl = new GenericUrl(tokenServerURL);
        HttpExecuteInterceptor clientAuthentication = new ClientParametersAuthentication(clientId, clientSecret);
        String authorizationServerUrl = authorizationServerURL;

        return new AuthorizationCodeFlow.Builder(method, HTTP_TRANSPORT, JSON_FACTORY, tokenServerUrl,
                clientAuthentication, clientId, authorizationServerUrl)
                .setScopes(scopes)
                .setCredentialDataStore(getCredentialDataStore())
                .build();
    }

    protected OAuth2ServiceUserStore getServiceUserStore() {
        if (serviceUserStore == null) {
            serviceUserStore = new OAuth2ServiceUserStore(serviceName);
        }
        return serviceUserStore;
    }

    public OAuth2TokenStore getCredentialDataStore() {
        if (tokenStore == null) {
            tokenStore = new OAuth2TokenStore(serviceName);
        }
        return tokenStore;
    }

    protected String getError(HttpServletRequest request) {
        String error = request.getParameter(ERROR_URL_PARAMETER);
        return StringUtils.isBlank(error) ? null : error;
    }

    // Checking conditions on the "code" URL parameter
    protected String getAuthorizationCode(HttpServletRequest request) {
        String code = request.getParameter(CODE_URL_PARAMETER);
        return StringUtils.isBlank(code) ? null : code;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getTokenServerURL() {
        return tokenServerURL;
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public String getClientSecret() {
        return clientSecret;
    }

    @Override
    public List<String> getScopes() {
        return scopes;
    }

    @Override
    public String getAuthorizationServerURL() {
        return authorizationServerURL;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isProviderAvailable() {
        return isEnabled() && getClientSecret() != null && getClientId() != null;
    }

    @Override
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public void setTokenServerURL(String tokenServerURL) {
        this.tokenServerURL = tokenServerURL;
    }

    @Override
    public void setAuthorizationServerURL(String authorizationServerURL) {
        this.authorizationServerURL = authorizationServerURL;
    }

    @Override
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    @Override
    public void setScopes(String... scopes) {
        this.scopes = (scopes == null) ? Collections.emptyList() : Arrays.asList(scopes);
    }

    public static class Builder extends AuthorizationCodeFlow.Builder {

        public Builder(AccessMethod method, HttpTransport transport, JsonFactory jsonFactory,
                GenericUrl tokenServerUrl, HttpExecuteInterceptor clientAuthentication, String clientId,
                String authorizationServerEncodedUrl) {
            super(method, transport, jsonFactory, tokenServerUrl, clientAuthentication, clientId,
                    authorizationServerEncodedUrl);
        }

        @Override
        public SalesforceAuthorizationCodeFlow build() {
            return new SalesforceAuthorizationCodeFlow(this);
        }

    }
   

}
