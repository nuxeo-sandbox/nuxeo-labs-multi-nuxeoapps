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
package org.nuxeo.labs.multi.nuxeoapps.remote;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.labs.multi.nuxeoapps.authentication.NuxeoAppAuthentication;
import org.nuxeo.labs.multi.nuxeoapps.authentication.NuxeoAppAuthenticationBASIC;
import org.nuxeo.labs.multi.nuxeoapps.authentication.NuxeoAppAuthenticationJWT;
import org.nuxeo.labs.multi.nuxeoapps.servlet.NuxeoAppServletUtils;

/**
 * @since 2023
 */
public class NuxeoApp extends AbstractNuxeoApp {

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

        if (NuxeoAppAuthenticationBASIC.hasEnoughValues(jsonApp)) {
            return new NuxeoApp(appName, appUrl, jsonApp.getString("basicUser"), jsonApp.getString("basicPwd"));
        }

        if (NuxeoAppAuthenticationJWT.hasEnoughValues(jsonApp)) {
            return new NuxeoApp(appName, appUrl, jsonApp.getString("tokenUser"), jsonApp.getString("tokenClientId"),
                    jsonApp.getString("tokenClientSecret"), jsonApp.getString("jwtSecret"));
        }

        throw new NuxeoException("Object is not BASIC not JWT authentication.");
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

                updateEntries(result, status);

            } else {
                result = createMultiNxAppInfo(status, null, resp.body());
            }

        } catch (IOException | InterruptedException e) {
            result = generateErrorObject(-1, "An error occured: " + e.getMessage(), appName, true, null);
        }

        return result;
    }

    public static JSONObject generateErrorObject(int httpResponseStatus, String responseMessage, String appName,
            boolean withEmptyEntries, Map<String, String> otherFields) {

        JSONObject result = new JSONObject();
        if (withEmptyEntries) {
            result.put("entity-type", "documents");
            result.put("entries", new JSONArray());
        }
        if (otherFields != null) {
            otherFields.forEach(result::put);
        }

        JSONObject obj = new JSONObject();
        obj.put("hasError", true);
        obj.put("httpResponseStatus", httpResponseStatus);
        obj.put("responseMessage", responseMessage);
        obj.put("appName", appName);
        result.put(MULTI_NUXEO_APPS_PROPERTY_NAME, obj);

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
    
//    @Override
//    public Blob getBlob(String relativePath, boolean returnRedirectInfo) throws IOException, InterruptedException {
//
//        String authHeaderValue = nuxeoAppAuthentication.getAutorizationHeaderValue();
//
//        String url = relativePath;
//        if (!url.startsWith("/")) {
//            url = "/" + url;
//        }
//        if (url.indexOf("&clientReason=download") < 0) {
//            url += "&clientReason=download";
//        }
//
//        String targetUrl = appUrl + url;
//
//        HttpClient client = HttpClient.newBuilder()
//                                      .connectTimeout(Duration.ofSeconds(60))
//                                      .followRedirects(HttpClient.Redirect.NEVER) // Automatic redirect fails when using
//                                                                                  // S3 direct download
//                                      .build();
//
//        HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl))
//                                         .timeout(Duration.ofSeconds(40))
//                                         .header("Authorization", authHeaderValue)
//                                         .header("Accept", "*/*")
//                                         .GET()
//                                         .build();
//
//        Blob blob = Blobs.createBlobWithExtension(".bin");// Create a temp. file
//        Path blobFilePath = blob.getFile().toPath();
//
//        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(blobFilePath));
//
//        if (NuxeoAppServletUtils.isRedirect(response.statusCode())) {
//            String location = response.headers()
//                                      .firstValue("Location")
//                                      .orElseThrow(() -> new IOException("Redirect without Location header"));
//
//            if (returnRedirectInfo) {
//                JSONObject redirectInfoJson = new JSONObject();
//                redirectInfoJson.put("status", response.statusCode());
//                redirectInfoJson.put("location", location);
//                blob = Blobs.createJSONBlob(redirectInfoJson.toString());
//                return blob;
//            }
//            // Do download the blob of caller wants it
//            request = HttpRequest.newBuilder(URI.create(location)).GET().header("Accept", "*/*").build();
//            response = client.send(request, HttpResponse.BodyHandlers.ofFile(blobFilePath));
//        }
//
//        if (response.statusCode() != 200) {
//            throw new IOException("Failed to download file: HTTP " + response.statusCode());
//        }
//
//        HttpHeaders headers = response.headers();
//        String mimeType = headers.firstValue("Content-Type").orElse(null);
//        // Detect MIME type if not provided
//        if (mimeType == null) {
//            mimeType = Files.probeContentType(blobFilePath);
//            if (mimeType == null) {
//                mimeType = "application/octet-stream";
//            }
//        }
//        blob.setMimeType(mimeType);
//
//        String fileName = NuxeoAppServletUtils.extractFileName(headers.firstValue("Content-Disposition").orElse(null),
//                url);
//        blob.setFilename(fileName);
//
//        return blob;
//
//    }

}
