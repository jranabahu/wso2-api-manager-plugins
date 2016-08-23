/*
*  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.apiManager.plugin.client;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.apiManager.plugin.constants.APIConstants;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

public abstract class AbstractAPIManagerClientImpl implements APIManagerClient {
    private static final Logger logger = LoggerFactory.getLogger(AbstractAPIManagerClientImpl.class);
    private static final int MAX_CONNECTIONS = 100;
    private static final int DEFAULT_MAX_PER_ROUTE = 20;

    protected CloseableHttpClient httpClient;

    @Override
    public void initialize() {
        if (httpClient == null) {
            try {
                // TODO: need to use the ReadyAPI truststore

                SSLContextBuilder builder = new SSLContextBuilder();
                builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(builder.build());

                Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
                        .<ConnectionSocketFactory>create().register("http", new PlainConnectionSocketFactory())
                        .register("https", sslConnectionSocketFactory).build();

                PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager
                        (socketFactoryRegistry);
                // Increase max total connection to 100
                connectionManager.setMaxTotal(MAX_CONNECTIONS);
                // Increase default max connection per route to 20
                connectionManager.setDefaultMaxPerRoute(DEFAULT_MAX_PER_ROUTE);

                httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();
            } catch (NoSuchAlgorithmException e) {
                logger.error("Unable to load the trust store", e);
            } catch (KeyStoreException e) {
                logger.error("Unable to get the key store instance", e);
            } catch (KeyManagementException e) {
                logger.error("Unable to load trust store material", e);
            }
        }

    }

    /**
     * This method will construct the tenant user name
     * Ex:- janaka@sampleTenant.com
     *
     * @param userName     The user name
     * @param tenantDomain The tenant domain of the user
     * @return The tenant user name
     */
    String constructTenantUserName(String userName, String tenantDomain) {
        return userName + APIConstants.TENANT_DOMAIN_SEPARATOR + tenantDomain;
    }
}
