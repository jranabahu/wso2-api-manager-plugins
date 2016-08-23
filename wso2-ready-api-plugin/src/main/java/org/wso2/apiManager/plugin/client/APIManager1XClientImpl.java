/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import com.eviware.soapui.support.StringUtils;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.apiManager.plugin.constants.APIConstants;
import org.wso2.apiManager.plugin.dataObjects.APIInfo;
import org.wso2.apiManager.plugin.internal.Configuration;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is responsible for connecting to WSO2 API Manager and fetching APIs and their definitions
 */
public class APIManager1XClientImpl extends AbstractAPIManagerClientImpl {
    private static final Logger logger = LoggerFactory.getLogger(APIManager1XClientImpl.class);

    /**
     * This method will return all APIs of the given tenant domain
     *
     * @return list of @link{APIInfo}
     * @throws java.lang.Exception if any error occurs
     */
    @Override
    public List<APIInfo> getAllPublishedAPIs() throws Exception {
        List<APIInfo> apiList = new ArrayList<>();

        Configuration configuration = Configuration.getInstance();
        String storeEndpoint = configuration.getStoreUrl();
        String userName = configuration.getUserName();
        char[] password = configuration.getPassword();
        String tenantDomain = configuration.getTenantDomain();
        String productVersion = configuration.getProductVersion();

        String tenantUserName = userName;

        /*
         The tenant domain can be empty.
         If the tenant domain is not empty then we use the tenant aware user name for authentication purposes.
         If it empty, then we assign super tenant domain name for that.
         */
        if (!StringUtils.isNullOrEmpty(tenantDomain)) {
            tenantUserName = constructTenantUserName(userName, tenantDomain);
        } else {
            tenantDomain = APIConstants.CARBON_SUPER;
        }

        // If the authentication process is successful
        if (authenticate(storeEndpoint, tenantUserName, password)) {
            HttpPost httpPost = new HttpPost(getAPIStoreListUrl(storeEndpoint));

            CloseableHttpResponse response = null;
            try {
                // Request parameters and other properties.
                List<NameValuePair> params = new ArrayList<>(3);

                params.add(new BasicNameValuePair(APIConstants.API_ACTION, APIConstants
                        .PAGINATED_PUBLISHED_API_GET_ACTION));
                params.add(new BasicNameValuePair("tenant", tenantDomain));
                params.add(new BasicNameValuePair("start", Integer.toString(0)));
                params.add(new BasicNameValuePair("end", Integer.toString(Integer.MAX_VALUE)));
                httpPost.setEntity(new UrlEncodedFormEntity(params, APIConstants.UTF_8));

                response = httpClient.execute(httpPost);
                HttpEntity entity = response.getEntity();
                String responseString = EntityUtils.toString(entity, APIConstants.UTF_8);

                String[] errorSection = responseString.split(",");
                boolean isError = Boolean.parseBoolean(errorSection[0].split(":")[1].split("}")[0].trim());
                if (isError) {
                    String errorMsg = errorSection[1].split(":")[1].split("}")[0].trim();
                    String message = "Error occurred while getting the list of APIs " + errorMsg;
                    logger.error(message);
                    throw new Exception(message);
                }

                JSONObject jsonObject;
                JSONArray apiArray;
                try {
                    jsonObject = (JSONObject) JSONValue.parse(responseString);

                    // We expect an JSON array for api list
                    apiArray = (JSONArray) jsonObject.get("apis");
                    for (Object apiJsonObject : apiArray) {
                        JSONObject apiJson = (JSONObject) apiJsonObject;

                        String apiName = apiJson.get("name").toString();
                        String apiProvider = apiJson.get("provider").toString();
                        String version = apiJson.get("version").toString();
                        String description = apiJson.get("description") == null ? "" : apiJson.get("description")
                                .toString();

                        APIInfo apiInfo = new APIInfo();
                        apiInfo.setName(apiName);
                        apiInfo.setProvider(apiProvider);
                        apiInfo.setVersion(version);
                        apiInfo.setDescription(description);
                        apiInfo.setSwaggerDocLink(getSwaggerDocLink(storeEndpoint, apiName, version, apiProvider,
                                                                    productVersion));
                        apiInfo.setProductVersion(productVersion);

                        apiList.add(apiInfo);
                    }
                } catch (ClassCastException e) {
                    String message = "Could not parse the results. Incompatible result";
                    logger.error(message, e);
                    throw new Exception(message, e);
                }
            } finally {
                // This is done to release the connections
                if(response != null){
                    response.close();
                }
            }
        }

        return apiList;
    }

    /**
     * Method to authenticate with the given API Store
     *
     * @param storeEndpoint The endpoint of the API Store
     * @param userName      the username with the tenant domain
     * @param password      the user password
     * @return true if authentication is successful
     * throws Exception if any error happens
     */
    private boolean authenticate(String storeEndpoint, String userName, char[] password) throws Exception {
        // create a post request to addAPI.
        HttpPost httpPost = new HttpPost(getAPIStoreLoginUrl(storeEndpoint));

        CloseableHttpResponse response = null;
        try {
            // Request parameters and other properties.
            List<NameValuePair> params = new ArrayList<>(3);

            params.add(new BasicNameValuePair(APIConstants.API_ACTION, APIConstants.API_LOGIN_ACTION));
            params.add(new BasicNameValuePair(APIConstants.API_STORE_LOGIN_USERNAME, userName));
            params.add(new BasicNameValuePair(APIConstants.API_STORE_LOGIN_PASSWORD, new String(password)));
            httpPost.setEntity(new UrlEncodedFormEntity(params, APIConstants.UTF_8));

            response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();
            String responseString = EntityUtils.toString(entity, APIConstants.UTF_8);

            String[] errorSection = responseString.split(",");
            boolean isError = Boolean.parseBoolean(errorSection[0].split(":")[1].split("}")[0].trim());
            if (isError) {
                String errorMsg = errorSection[1].split(":")[1].split("}")[0].trim();
                throw new Exception(" Authentication with external APIStore -  failed due to " + errorMsg);
            } else {
                return true;
            }
        }catch (Exception e){
            logger.error("Unable to authenticate user", e);
        } finally {
            // This is to release the connections.
            if(response != null){
                response.close();
            }
        }
        return false;
    }

    /**
     * This method returns the login endpoint of the store
     * Ex:- https://localgost:9443/store/site/blocks/user/login/ajax/login.jag
     *
     * @param baseUrl The endpoint of the API Store
     * @return the login endpoint URL
     */
    private String getAPIStoreLoginUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf('/'));
        }
        return baseUrl + APIConstants.API_STORE_LOGIN_URL;
    }

    /**
     * This method returns the list URL of the API Store
     * Ex:- https://localhost:9443/store/site/blocks/api/listing/ajax/list.jag
     *
     * @param baseUrl The endpoint of the API Store
     * @return The list endpoint of the API Store
     */
    private String getAPIStoreListUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf('/'));
        }
        return baseUrl + APIConstants.API_STORE_API_LIST_URL;
    }

    /**
     * This method returns the swagger doc link of the API
     * Ex:- https://localhost:9443/store/api-docs/janaka%40janaka.com/WikipediaAPI/1.0.0
     *
     * @param baseUrl        The endpoint of the API Store
     * @param apiName        The name of the API
     * @param apiVersion     The version of the API
     * @param apiProvider    The provider of the API
     * @param productVersion The version of the API Manager
     * @return The swagger doc link of the API
     */
    private String getSwaggerDocLink(String baseUrl, String apiName, String apiVersion, String apiProvider,
                                     String productVersion) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf('/'));
        }
        if ("1.8.0".equals(productVersion)) {
            try {
                apiProvider = URLEncoder.encode(apiProvider, APIConstants.UTF_8);
            } catch (UnsupportedEncodingException e) {
                logger.error("Error while generating the api-docs URL ", e);
            }
        } else {
            apiProvider = apiProvider.replace("@","-AT-");
        }
        return baseUrl + "/api-docs/" + apiProvider + "/" + apiName + "/" + apiVersion;
    }
}
