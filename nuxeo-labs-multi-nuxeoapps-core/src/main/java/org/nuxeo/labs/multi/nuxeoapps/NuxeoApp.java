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

import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.labs.multi.nuxeoapps.authentication.NuxeoAppAuthentication;
import org.nuxeo.labs.multi.nuxeoapps.authentication.NuxeoAppAuthenticationBASIC;
import org.nuxeo.labs.multi.nuxeoapps.authentication.NuxeoAppAuthenticationJWT;

/**
 * @since 2023
 */
public class NuxeoApp extends AbstractNuxeoApp {

    public static final int DEFAULT_PAGE_SIZE = 50;

    protected NuxeoAppAuthentication nuxeoAppAuthentication = null;

    @Override
    protected NuxeoAppAuthentication getNuxeoAppAuthentication() {
        return nuxeoAppAuthentication;
    }

    public NuxeoApp(String appName, String appUrl, String basicUser, String basicPwd) {

        super.initialize(appName, appUrl, false);

        nuxeoAppAuthentication = new NuxeoAppAuthenticationBASIC(basicUser, basicPwd);

    }

    public NuxeoApp(String appName, String appUrl, String tokenUser, String tokenClientId, String tokenClientSecret,
            String jwtSecret) {

        super.initialize(appName, appUrl, false);

        nuxeoAppAuthentication = new NuxeoAppAuthenticationJWT(appUrl, tokenUser, tokenClientId, tokenClientSecret,
                jwtSecret);

    }

    public static NuxeoApp fromJSONObject(JSONObject jsonApp) {

        String appName = jsonApp.getString("jsonApp");
        String appUrl = jsonApp.getString("appUrl");

        if (NuxeoAppAuthenticationBASIC.hasRequiredFields(jsonApp)) {
            return new NuxeoApp(appName, appUrl, jsonApp.getString("basicUser"), jsonApp.getString("basicPwd"));
        }

        if (NuxeoAppAuthenticationJWT.hasRequiredFields(jsonApp)) {
            return new NuxeoApp(appName, appUrl, jsonApp.getString("tokenUser"), jsonApp.getString("tokenClientId"),
                    jsonApp.getString("tokenClientSecret"), jsonApp.getString("jwtSecret"));
        }

        throw new NuxeoException("Object is not BASIC not JWT authentication.");
    }

    public JSONObject call(String nxql, String enrichers, String properties, int pageIndex, int pageSize) {

        JSONObject result = null;

        result = call(null, nxql, enrichers, properties, pageIndex, pageSize);

        return result;
    }

    public JSONObject call(String currentUserName, String nxql, String enrichers, String properties, int pageIndex,
            int pageSize) {

        JSONObject result;

        try {
            String authHeaderValue = nuxeoAppAuthentication.getAutorizationHeaderValue(currentUserName);

            String targetUrl = appUrl + "/api/v1/search/execute";
            String encodedNxql;
            encodedNxql = URLEncoder.encode(nxql, StandardCharsets.UTF_8);
            targetUrl += "?query=" + encodedNxql;
            
            if (pageIndex < 0) {
                pageIndex = 0;
            }
            targetUrl += "&currentPageIndex=" + pageIndex;
            
            if (pageSize < 1) {
                pageSize = DEFAULT_PAGE_SIZE;
            }
            targetUrl += "&pageSize=" + pageSize;

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

            if (status == 200) { // ==============================> All good
                result = new JSONObject(resp.body());
                updateDocumentsEntityType(result, status);
                
            } else { // ==========================================> Error occured
                String body = resp.body();
                String errMessage = null;

                JSONObject errJson;
                try {
                    errJson = new JSONObject(body);
                    errMessage = errJson.optString("message", null);
                } catch (JSONException e) {
                    errJson = null;
                }

                if (fullStackOnError) {
                    if (errJson == null) {
                        errMessage = body;
                    }
                } else {
                    if (errMessage == null) {
                        int maxSize = 5 * 1024;

                        errMessage = body;
                        // We receive UTF-8 English, so it's safe to consider one char = one byte.
                        if (errMessage.length() > maxSize) {
                            errMessage = "[TRUNCATED TO 5k] " + errMessage.substring(0, maxSize);
                        }
                    }
                }
                result = generateErrorObject(status, "An error occured: " + errMessage, appName, true,
                        fullStackOnError ? errJson : (JSONObject) null);
            }

        } catch (IOException | InterruptedException e) {
            result = generateErrorObject(-1, "An error occured: " + e.getMessage(), appName, true,
                    fullStackOnError ? e : (Throwable) null);
        }

        return result;
    }

    public JSONObject toJSONObject() {

        JSONObject jsonApp = new JSONObject();

        jsonApp.put("appName", appName);
        jsonApp.put("appUrl", appUrl);

        JSONObject authObj = null;
        if (nuxeoAppAuthentication instanceof NuxeoAppAuthenticationBASIC) {
            authObj = nuxeoAppAuthentication.toJSONObject();
        } else if (nuxeoAppAuthentication instanceof NuxeoAppAuthenticationJWT) {
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
