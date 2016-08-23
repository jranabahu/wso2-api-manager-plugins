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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.wso2.apiManager.plugin.constants.APIConstants;
import org.wso2.apiManager.plugin.dataObjects.APIInfo;
import org.wso2.apiManager.plugin.internal.Configuration;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class APIManager2XClientImpl extends AbstractAPIManagerClientImpl {
    private static final Log logger = LogFactory.getLog(APIManager2XClientImpl.class);

    private static final JsonParser parser = new JsonParser();
    private String clientId;
    private String clientSecret;

    @Override
    public void initialize() {
        super.initialize();

        // We register the OAuth2 Application at the client initialization time
        doDCR();
    }

    @Override
    public List<APIInfo> getAllPublishedAPIs() throws Exception {
        return null;
    }

    private String getAccessToken(){

    }

    private boolean doDCR() {
        boolean isNewApp = false;

        // TODO: store the client key and secret while saving the project and check them here.
        if (isNewApp) {

        } else {
            HttpPost dcrRequest = new HttpPost(getAPIStoreDCRURL());
            CloseableHttpResponse response = null;
            try {
                dcrRequest.setEntity(new StringEntity(getDCRPayload(), ContentType.APPLICATION_JSON));

                response = httpClient.execute(dcrRequest);
                HttpEntity entity = response.getEntity();
                String responseString = EntityUtils.toString(entity, APIConstants.UTF_8);

                if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
                    // Response was received successfully
                    JsonElement responseElement = parser.parse(responseString);
                    JsonObject responseObject = responseElement.getAsJsonObject();

                    if(responseObject.has("clientId")){
                        clientId = responseObject.get("clientId").getAsString();
                    }
                    if(responseObject.has("clientSecret")){
                        clientSecret = responseObject.get("clientSecret").getAsString();
                    }
                    // We have found both the consumer key and secret.
                    return true;
                }
            } catch (Exception e) {
                logger.error("Unable to register the OAuth application", e);
            } finally {
                // This is to release the connections.
                if (response != null) {
                    try {
                        response.close();
                    } catch (IOException e) {
                        //ignore
                    }
                }
            }
        }

        return false;
    }

    /**
     * This is a method to get the REST API URL.
     * The method will check whether the user has given the Store URL(instead of the REST API URL) and if so, will
     * construct the correct URL
     *
     * @return The REST API URL
     */
    private String getAPIStoreDCRURL() {
        String inputURL = Configuration.getInstance().getStoreUrl();

        // We check whether the given URL ends with /store
        if (inputURL.endsWith("/store")) {
            inputURL = inputURL.substring(0, inputURL.indexOf("/store"));
        }

        // Now we append the DCR URL suffix
        return inputURL + "/client-registration/v0.10/register";

    }

    /**
     * This is a method to get the JSON payload of the DCR request.
     * The final result will have the following format.
     * <p>
     * {
     * "callbackUrl": "www.google.lk",
     * "clientName": "rest_api_store",
     * "tokenScope": "Production",
     * "owner": "admin",
     * "grantType": "password refresh_token",
     * "saasApp": true
     * }
     */
    private String getDCRPayload() {
        JsonObject jsonObject = new JsonObject();

        jsonObject.addProperty("clientName", constructDCRClientName());
        jsonObject.addProperty("tokenScope", "production");
        jsonObject.addProperty("owner", Configuration.getInstance().getUserName());
        jsonObject.addProperty("grantType", "password refresh_token");
        jsonObject.addProperty("saasApp", true);
        jsonObject.addProperty("callbackUrl", "");

        return jsonObject.toString();
    }

    /**
     * This is a method to construct the DCR Client name.
     * The final client name will be in the format of {Project Name}-{User Name}-{Time Stamp}
     *
     * @return The constructed client name
     */
    private String constructDCRClientName() {
        Configuration configuration = Configuration.getInstance();

        return configuration.getProjectName() + '-' + configuration.getUserName() + '-' + new Date().toString();
    }
}
