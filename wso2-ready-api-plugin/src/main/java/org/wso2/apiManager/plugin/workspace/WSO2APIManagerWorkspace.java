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

package org.wso2.apiManager.plugin.workspace;

import com.eviware.soapui.impl.WorkspaceImpl;
import com.eviware.soapui.impl.rest.RestService;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.plugins.auto.PluginImportMethod;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;
import com.eviware.x.form.ValidationMessage;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.XFormField;
import com.eviware.x.form.XFormFieldValidator;
import com.eviware.x.form.support.ADialogBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.apiManager.plugin.Utils;
import org.wso2.apiManager.plugin.dataObjects.APIExtractionResult;
import org.wso2.apiManager.plugin.dataObjects.APIInfo;
import org.wso2.apiManager.plugin.dataObjects.APISelectionResult;
import org.wso2.apiManager.plugin.exception.APIManagerPluginException;
import org.wso2.apiManager.plugin.internal.Configuration;
import org.wso2.apiManager.plugin.ui.ProjectModel;
import org.wso2.apiManager.plugin.worker.APIExtractorWorker;
import org.wso2.apiManager.plugin.worker.APIImporterWorker;

import java.net.URL;
import java.util.List;

import static org.wso2.apiManager.plugin.constants.HelpMessageConstants.INVALID_API_STORE_URL;
import static org.wso2.apiManager.plugin.constants.HelpMessageConstants.PASSWORD_VALIDATION_MSG;
import static org.wso2.apiManager.plugin.constants.HelpMessageConstants.PROJECT_NAME_VALIDATION_MSG;
import static org.wso2.apiManager.plugin.constants.HelpMessageConstants.USER_NAME_VALIDATION_MSG;

/**
 * This class is used to generate a new workspace for the WSO2 API Manager projects
 */
@PluginImportMethod(label = "Import from WSO2 API Manager")
public class WSO2APIManagerWorkspace extends AbstractSoapUIAction<WorkspaceImpl> {
    private static final Logger logger = LoggerFactory.getLogger(WSO2APIManagerWorkspace.class);

    private APIExtractionResult listExtractionResult = null;

    public WSO2APIManagerWorkspace() {
        super("Create Project from WSO2 API Manager", "Creates new project from API specifications on the API Store");
    }

    public void perform(WorkspaceImpl workspace, final Object params) {
        final XFormDialog dialog = ADialogBuilder.buildDialog(ProjectModel.class);

        /*
         * The purpose of this listener is to validate the API Store URL and the Project name upon submitting the form
         */
        dialog.getFormField(ProjectModel.API_STORE_URL).addFormFieldValidator(new XFormFieldValidator() {
            public ValidationMessage[] validateField(XFormField formField) {
                String storeUrlValue = formField.getValue();

                // We validate the Store URL first.
                URL storeUrl = Utils.validateURL(storeUrlValue);
                if (storeUrl == null) {
                    return new ValidationMessage[]{new ValidationMessage(INVALID_API_STORE_URL, formField)};
                }

                String userName = dialog.getValue(ProjectModel.USER_NAME);
                char[] password = dialog.getValue(ProjectModel.PASSWORD).toCharArray();
                String projectVersion = dialog.getValue(ProjectModel.PRODUCT_VERSION);
                String projectName = dialog.getValue(ProjectModel.PROJECT_NAME);

                // At this point, we keep these values to that we can use them later in the same session or to be
                // saved later when the project is saved.
                Configuration configuration = Configuration.getInstance();
                configuration.setProjectName(projectName);
                configuration.setStoreUrl(storeUrlValue);
                configuration.setUserName(userName);
                configuration.setPassword(password);
                configuration.setTenantDomain(Utils.getTenantDomain(userName));
                configuration.setProductVersion(projectVersion);

                if (StringUtils.isNullOrEmpty(storeUrlValue)) {
                    return new ValidationMessage[]{new ValidationMessage(INVALID_API_STORE_URL, dialog.getFormField
                            (ProjectModel.API_STORE_URL))};
                }
                if (StringUtils.isNullOrEmpty(projectName)) {
                    return new ValidationMessage[]{new ValidationMessage(PROJECT_NAME_VALIDATION_MSG, dialog
                            .getFormField(ProjectModel.PROJECT_NAME))};
                }
                if (StringUtils.isNullOrEmpty(userName)) {
                    return new ValidationMessage[]{new ValidationMessage(USER_NAME_VALIDATION_MSG, dialog
                            .getFormField(ProjectModel.USER_NAME))};
                }
                if (StringUtils.isNullOrEmpty(dialog.getValue(ProjectModel.PASSWORD))) {
                    return new ValidationMessage[]{new ValidationMessage(PASSWORD_VALIDATION_MSG, dialog.getFormField
                            (ProjectModel.PASSWORD))};
                }

                try {
                    listExtractionResult = APIExtractorWorker.downloadAPIList();
                } catch (APIManagerPluginException e) {
                    // An exception has been thrown when trying to extract the list of APIs from the store.
                    // We expose that as a validation message for the user because this can happen due to
                    // misinformation given by the user.
                    if (StringUtils.hasContent(listExtractionResult.getError())) {
                        return new ValidationMessage[]{new ValidationMessage(listExtractionResult.getError(), formField)};
                    }
                }
                return new ValidationMessage[0];
            }
        });

        // If the values are been set previously, we populate the dialog with the same values
        Configuration configuration = Configuration.getInstance();
        if (configuration.getStoreUrl() != null) {
            dialog.setValue(ProjectModel.API_STORE_URL, configuration.getStoreUrl());
        }
        if (configuration.getUserName() != null) {
            dialog.setValue(ProjectModel.USER_NAME, configuration.getUserName());
        }

        if (dialog.show() && listExtractionResult != null && !listExtractionResult.isCanceled()) {
            APISelectionResult selectionResult = Utils.showSelectAPIDefDialog(listExtractionResult.getApiList());
            if (selectionResult == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Selected API List is empty");
                }
                return;
            }

            List<APIInfo> selectedAPIs = selectionResult.getApiInfoList();
            if (selectedAPIs != null) {
                WsdlProject project;
                try {
                    project = workspace.createProject(dialog.getValue(ProjectModel.PROJECT_NAME), null);
                } catch (Exception e) {
                    // If any exception happens during the project creation, we log an error and return the flow.
                    String msg = String.format("Unable to create Project because of %s exception with " + "\"%s\" " +
                                               "message", e.getClass().getName(), e.getMessage());
                    logger.error(msg, e);
                    UISupport.showErrorMessage(msg);
                    return;
                }

                // Once the project is created, we import the services from the list of APIs
                List<RestService> services = APIImporterWorker.importServices(selectionResult, project);
                if (services != null && !services.isEmpty()) {
                    UISupport.select(services.get(0));
                } else {
                    workspace.removeProject(project);
                }
            }
        }
    }
}