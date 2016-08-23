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

package org.wso2.apiManager.plugin.worker;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.x.dialogs.Worker;
import com.eviware.x.dialogs.XProgressDialog;
import com.eviware.x.dialogs.XProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.apiManager.plugin.client.APIManagerClientFactory;
import org.wso2.apiManager.plugin.dataObjects.APIExtractionResult;
import org.wso2.apiManager.plugin.exception.APIManagerPluginException;

/**
 * This class acts as the worker class to fetch APIs from WSO2 API Manager.
 */
public class APIExtractorWorker implements Worker {
    private static final Logger logger = LoggerFactory.getLogger(APIExtractorWorker.class);

    private XProgressDialog waitDialog = null;
    private APIExtractionResult result = new APIExtractionResult();

    private String apiRetrievingError = null;

    public APIExtractorWorker(XProgressDialog waitDialog) {
        this.waitDialog = waitDialog;
    }

    public static APIExtractionResult downloadAPIList() throws APIManagerPluginException {
        APIExtractorWorker worker =
                new APIExtractorWorker(UISupport.getDialogs().createProgressDialog(
                        "Getting the  list of  APIs", 0, "", true));
        try {
            worker.waitDialog.run(worker);
        } catch (Exception e) {
            worker.result.setError(e.getMessage());

            worker.waitDialog.setVisible(false);
            worker.waitDialog.dispose();

            String msg = "Unable to download the list of APIs from WSO2 API Store";
            logger.error(msg, e);
            throw new APIManagerPluginException(msg, e);
        }
        return worker.result;
    }

    @Override
    public Object construct(XProgressMonitor xProgressMonitor) {
        try {
            result.setApiList(APIManagerClientFactory.getInstance().getAllPublishedAPIs());
        } catch (Exception e) {
            SoapUI.logError(e);
            apiRetrievingError = e.getMessage();
            if (StringUtils.isNullOrEmpty(apiRetrievingError)) {
                apiRetrievingError = e.getClass().getName();
            }
        }
        return null;
    }

    @Override
    public void finished() {
        if (result.isCanceled()) {
            return;
        }
        waitDialog.setVisible(false);
        if (StringUtils.hasContent(apiRetrievingError)) {
            result.setError("Unable to read API list from the specified WSO2 API Manager Store because of the " +
                            "following error:\n" + apiRetrievingError);
            return;
        }
        if (result.getApiList() == null || result.getApiList().isEmpty()) {
            result.setError("No API is accessible at the specified URL or registered correctly.");
        }
    }

    @Override
    public boolean onCancel() {
        waitDialog.setVisible(false);
        result.setCanceled();
        return true;
    }
}
