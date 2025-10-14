/*
 * (C) Copyright 2025 Hyland (http://hyland.com/)  and others.
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
 *     Thibaud Arguillere
 */
package org.nuxeo.labs.multi.nuxeoapps;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.labs.multi.nuxeoapps.servlet.NuxeoAppServletUtils;

/**
 * Base class for all NuxeoApp, providing centrfalized shared methods
 * 
 * @since 2023
 */
public abstract class AbstractNuxeoApp {

    public static final String MULTI_NUXEO_APPS_PROPERTY_NAME = "multiNxAppInfo";

    String appName;

    String appUrl;
    
    boolean isLocalNuxeo;

    public void initialize(String appName, String appUrl, boolean isLocalNuxeo) {

        this.appName = appName;
        this.appUrl = appUrl;
        this.isLocalNuxeo = isLocalNuxeo;
    }

    public String getAppName() {
        return appName;
    }

    public String getAppUrl() {
        return appUrl;
    }

    public void updateEntries(JSONObject result, Integer status) {

        JSONArray entries = result.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            
            JSONObject oneDoc = entries.getJSONObject(i);
            
            // Change blob URLs
            NuxeoAppServletUtils.updateBlobUrlsInProperties(oneDoc, getAppName(), isLocalNuxeo);
            
            // Add NuxeoApp info
            JSONObject info = createMultiNxAppInfo(null, appUrl + "/ui/#!/doc/" + oneDoc.getString("uid"), null);
            oneDoc.put(MULTI_NUXEO_APPS_PROPERTY_NAME, info);

        }

        result.put(MULTI_NUXEO_APPS_PROPERTY_NAME, createMultiNxAppInfo(status, null, null));
    }

    public JSONObject createMultiNxAppInfo(Integer httpStatus, String docFullUrl, String message) {

        JSONObject obj = new JSONObject();

        obj.put("appName", appName);
        if (httpStatus != null) {
            obj.put("httpResponseStatus", httpStatus);
        }
        if (StringUtils.isNotBlank(docFullUrl)) {
            obj.put("docFullUrl", docFullUrl);
        }
        if (StringUtils.isNotBlank(message)) {
            obj.put("message", message);
        }

        return obj;

    }

    // To be overriden
    public Blob getBlob(String url) throws IOException, InterruptedException {

        // Implement calling GET to get the blob
        throw new UnsupportedOperationException();
    }

}
