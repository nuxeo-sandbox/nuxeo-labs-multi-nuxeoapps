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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * @since 2023
 */
public class NuxeoApp {

    public static final String MULTI_NUXEO_APPS_PROPERTY_NAME = "multiNxAppInfo";

    protected String appName;

    protected String appUrl;

    protected NuxeoAppAuthentication nuxeoAppAuthentication = null;

    public NuxeoApp(String appName, String appUrl, String basicUser, String basicPwd) {

        this.appName = appName;
        this.appUrl = appUrl;

        nuxeoAppAuthentication = new NuxeoAppBASIC(basicUser, basicPwd);

    }

    public NuxeoApp(String appName, String appUrl, String tokenUser, String tokenClientId, String tokenClientSecret,
            String jwtSecret) {

        this.appName = appName;
        this.appUrl = appUrl;

        nuxeoAppAuthentication = new NuxeoAppJWT(appUrl, tokenUser, tokenClientId, tokenClientSecret, jwtSecret);

    }

    public static NuxeoApp fromJSONObject(JSONObject jsonApp) {

        String appName = jsonApp.getString("jsonApp");
        String appUrl = jsonApp.getString("appUrl");

        if (NuxeoAppBASIC.hasEnoughValues(jsonApp)) {
            return new NuxeoApp(appName, appUrl, jsonApp.getString("basicUser"), jsonApp.getString("basicPwd"));
        }

        if (NuxeoAppJWT.hasEnoughValues(jsonApp)) {
            return new NuxeoApp(appName, appUrl, jsonApp.getString("tokenUser"), jsonApp.getString("tokenClientId"),
                    jsonApp.getString("tokenClientSecret"), jsonApp.getString("jwtSecret"));
        }

        throw new NuxeoException("Object is not BASIC not JWT authentication.");
    }

    public String getAppName() {
        return appName;
    }

    public String getAppUrl() {
        return appUrl;
    }

    public JSONObject call(String nxql, String enrichers, String properties, int pageIndex) {

        JSONObject result = null;

        result = call(null, nxql, enrichers, properties, pageIndex);

        return result;
    }

    public JSONObject call(String currentUserName, String nxql, String enrichers, String properties, int pageIndex) {

        JSONObject result = new JSONObject();

        try {
            String authHeaderValue = nuxeoAppAuthentication.getAutorizationHeaderValue(currentUserName);

            String targetUrl = appUrl + "/api/v1/search/execute";
            String encodedNxql;
            encodedNxql = URLEncoder.encode(nxql, StandardCharsets.UTF_8);
            targetUrl += "?query=" + encodedNxql;
            if (pageIndex > 0) {
                targetUrl += "&currentPageIndex=" + pageIndex;
            }

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl))
                                             .timeout(Duration.ofSeconds(40))
                                             .header("Authorization", authHeaderValue)
                                             .header("Content-Type", "application/json")
                                             .header("enrichers.document", enrichers)
                                             .header("properties", properties)
                                             .GET()
                                             .build();

            // We assume the response will not be megabytes, it's JSON string, no need for a stream,
            // get it directly in a String
            HttpResponse<String> resp;
            resp = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Read response
            int status = resp.statusCode();
            if (status == 200) {

                result = new JSONObject(resp.body());
                JSONArray entries = result.getJSONArray("entries");
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject oneDoc = entries.getJSONObject(i);
                    JSONObject info = addMultiNxAppInfo(null, appUrl + "/ui/#!/doc/" + oneDoc.getString("uid"), null);
                    oneDoc.put(MULTI_NUXEO_APPS_PROPERTY_NAME, info);
                }

                result.put(MULTI_NUXEO_APPS_PROPERTY_NAME, addMultiNxAppInfo(status, null, null));

            } else {
                result = addMultiNxAppInfo(status, null, resp.body());
            }

        } catch (IOException | InterruptedException e) {
            result = generateErrorObject(-1, "An error occured: " + e.getMessage(), appName, true, null);
        }

        return result;
    }

    protected JSONObject addMultiNxAppInfo(Integer httpStatus, String docFullUrl, String message) {

        JSONObject obj = new JSONObject();

        obj.put("appName", appName);
        if (httpStatus != null) {
            obj.put("httpResponseStatus", httpStatus);
        }
        if (StringUtils.isNotBlank(docFullUrl)) {
            obj.put("docFullUrl", docFullUrl);
        }
        if (StringUtils.isNotBlank(message)) {
            obj.put("docFullUrl", message);
        }

        return obj;

    }

    public static JSONObject generateErrorObject(int httpResponseStatus, String responseMessage, String appName,
            boolean withEmptyEntries, Map<String, String> otherFields) {

        JSONObject obj = new JSONObject();
        obj.put("multiNxApp_hasError", true);
        obj.put("multiNxApp_httpResponseStatus", httpResponseStatus);
        obj.put("multiNxApp_responseMessage", responseMessage);
        obj.put("multiNxApp_appName", appName);
        if (withEmptyEntries) {
            obj.put("entity-type", "documents");
            obj.put("entries", new JSONArray());
        }
        if (otherFields != null) {
            otherFields.forEach(obj::put);
        }

        return obj;

    }

    public JSONObject toJSONObject() {

        JSONObject jsonApp = new JSONObject();

        jsonApp.put("appName", appName);
        jsonApp.put("appUrl", appUrl);

        JSONObject authObj = null;
        if (nuxeoAppAuthentication instanceof NuxeoAppBASIC) {
            authObj = nuxeoAppAuthentication.toJSONObject();
        } else if (nuxeoAppAuthentication instanceof NuxeoAppJWT) {
            authObj = nuxeoAppAuthentication.toJSONObject();
        } else {
            throw new NuxeoException("No NuxeoAppAuthentication defined.");
        }

        for (String key : authObj.keySet()) {
            jsonApp.put(key, authObj.get(key));
        }

        return jsonApp;
    }

}
