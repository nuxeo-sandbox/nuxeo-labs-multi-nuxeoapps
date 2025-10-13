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

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 
 * @since TODO
 */
public abstract class AbstractNuxeoApp {

    public static final String MULTI_NUXEO_APPS_PROPERTY_NAME = "multiNxAppInfo";
    
    String appName;
    
    String appUrl;
    
    public void initialize(String appName, String appUrl) {
        
        this.appName = appName;
        this.appUrl = appUrl;
    }
    
    String getAppName() {
        return appName;
    }
    
    String getAppUrl() {
        return appUrl;
    }
    
    public void updateEntries(JSONObject result, Integer status) {
        
        JSONArray entries = result.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject oneDoc = entries.getJSONObject(i);
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

}
