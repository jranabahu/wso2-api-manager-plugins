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

import org.wso2.apiManager.plugin.internal.Configuration;

public class APIManagerClientFactory {

    /**
     * This method will return the API Manager Client instance based on the product version that has been selected.
     */
    public static APIManagerClient getInstance() {
        APIManagerClient client;

        Configuration configuration = Configuration.getInstance();
        String productVersion = configuration.getProductVersion();

        if (!"^(2\\.)?(\\d+\\.)?(\\*|\\d+)$".matches(productVersion)) {
            // If version is not staring with 2.0.0, we use the old REST API.
            client = new APIManager1XClientImpl();
        } else {
            // we make 2.0.0 behavior, the default one
            client = new APIManager2XClientImpl();
        }
        client.initialize();

        return client;
    }
}
