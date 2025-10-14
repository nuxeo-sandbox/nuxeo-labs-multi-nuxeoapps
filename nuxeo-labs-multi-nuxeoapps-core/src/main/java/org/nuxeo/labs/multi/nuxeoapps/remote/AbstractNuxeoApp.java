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
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.labs.multi.nuxeoapps.authentication.NuxeoAppAuthentication;
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
    
    // Implemented only in sub-classes
    protected abstract NuxeoAppAuthentication getNuxeoAppAuthentication();
    
    // To be overriden
    public Blob getBlob(String relativePath, boolean returnRedirectInfo) throws IOException, InterruptedException {

        // Implement calling GET to get the blob
       // throw new UnsupportedOperationException();
        
        String authHeaderValue = getNuxeoAppAuthentication().getAutorizationHeaderValue();

        String url = relativePath;
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        if (url.indexOf("&clientReason=download") < 0) {
            url += "&clientReason=download";
        }

        String targetUrl = appUrl + url;

        HttpClient client = HttpClient.newBuilder()
                                      .connectTimeout(Duration.ofSeconds(60))
                                      .followRedirects(HttpClient.Redirect.NEVER) // Automatic redirect fails when using
                                                                                  // S3 direct download
                                      .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl))
                                         .timeout(Duration.ofSeconds(40))
                                         .header("Authorization", authHeaderValue)
                                         .header("Accept", "*/*")
                                         .GET()
                                         .build();

        Blob blob = Blobs.createBlobWithExtension(".bin");// Create a temp. file
        Path blobFilePath = blob.getFile().toPath();

        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(blobFilePath));

        if (NuxeoAppServletUtils.isRedirect(response.statusCode())) {
            String location = response.headers()
                                      .firstValue("Location")
                                      .orElseThrow(() -> new IOException("Redirect without Location header"));

            if (returnRedirectInfo) {
                JSONObject redirectInfoJson = new JSONObject();
                redirectInfoJson.put("status", response.statusCode());
                redirectInfoJson.put("location", location);
                blob = Blobs.createJSONBlob(redirectInfoJson.toString());
                return blob;
            }
            // Do download the blob of caller wants it
            request = HttpRequest.newBuilder(URI.create(location)).GET().header("Accept", "*/*").build();
            response = client.send(request, HttpResponse.BodyHandlers.ofFile(blobFilePath));
        }

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download file: HTTP " + response.statusCode());
        }

        HttpHeaders headers = response.headers();
        String mimeType = headers.firstValue("Content-Type").orElse(null);
        // Detect MIME type if not provided
        if (mimeType == null) {
            mimeType = Files.probeContentType(blobFilePath);
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }
        }
        blob.setMimeType(mimeType);

        String fileName = NuxeoAppServletUtils.extractFileName(headers.firstValue("Content-Disposition").orElse(null),
                url);
        blob.setFilename(fileName);

        return blob;
    }

}
