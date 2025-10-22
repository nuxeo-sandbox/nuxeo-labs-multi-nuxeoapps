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
import org.nuxeo.labs.multi.nuxeoapps.servlet.NuxeoAppServlet;
import org.nuxeo.labs.multi.nuxeoapps.servlet.NuxeoAppServletUtils;

/**
 * Base class for all NuxeoApp, providing centralized shared methods
 * 
 * @since 2023
 */
public abstract class AbstractNuxeoApp {

    public static final String MULTI_NUXEO_APPS_PROPERTY_NAME = "multiNxAppInfo";
    
    public enum AuthenticationType {
        NOT_NEEDED, BASIC, JWT
    }

    protected String appName;

    protected String appUrl;
    
    protected AuthenticationType authenticationType;

    protected boolean isLocalNuxeo;

    protected boolean fullStackOnError = false;

    /**
     * Initialize the internal fields
     * 
     * @param appName
     * @param appUrl
     * @param isLocalNuxeo
     * @since 2023
     */
    public void initialize(String appName, String appUrl, boolean isLocalNuxeo, AuthenticationType authenticationType) {

        this.appName = appName;
        this.appUrl = appUrl;
        this.isLocalNuxeo = isLocalNuxeo;
        this.authenticationType = authenticationType;
    }

    /**
     * @return the application name
     * @since 2023
     */
    public String getAppName() {
        return appName;
    }

    /**
     * @return the application url
     * @since 2023
     */
    public String getAppUrl() {
        return appUrl;
    }
    
    public AuthenticationType getAuthenticationType() {
        return authenticationType;
    }

    /**
     * If the call returns a statusCode != 200, the whole stack will be put in the result if fullStackOnError is true.
     * 
     * @param value
     * @since 2023
     */
    public void setFullStackOnError(boolean value) {
        fullStackOnError = value;
    }

    /**
     * {@code result} is expected to be a "documents" entity-type.
     * The method:
     * - Gets the "entries" array property of {@code result} and update all urls it can find in blobs, replacing them
     * with a URL to the {@link NuxeoAppServlet}
     * - Updates the "documents" object with info about the NuxeoApp
     * Also add
     * 
     * @param result
     * @param status
     * @since 2023
     */
    public void updateDocumentsEntityType(JSONObject result, Integer status) {

        JSONArray entries = result.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {

            JSONObject oneDoc = entries.getJSONObject(i);

            // Change blob URLs
            NuxeoAppServletUtils.updateBlobUrlsInProperties(oneDoc, getAppName(), isLocalNuxeo);

            // Add NuxeoApp info
            JSONObject info = createMultiNxAppInfo(null, appUrl + "/ui/#!/doc/" + oneDoc.getString("uid"), null);
            info.put("isLocal", isLocalNuxeo);
            oneDoc.put(MULTI_NUXEO_APPS_PROPERTY_NAME, info);

        }

        result.put(MULTI_NUXEO_APPS_PROPERTY_NAME, createMultiNxAppInfo(status, null, null));
    }

    protected JSONObject createMultiNxAppInfo(Integer httpStatus, String docFullUrl, String message) {

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

    public static JSONObject generateErrorObject(int httpResponseStatus, String responseMessage, String appName,
            boolean withEmptyEntries, Throwable exception) {

        JSONObject exceptionJson = null;
        if (exception != null) {
            exceptionJson = Utilities.exceptionToJson(exception);
        }
        return generateErrorObject(httpResponseStatus, responseMessage, appName, withEmptyEntries, exceptionJson);

    }

    public static JSONObject generateErrorObject(int httpResponseStatus, String responseMessage, String appName,
            boolean withEmptyEntries, JSONObject exceptionAsJson) {
        JSONObject result = new JSONObject();
        if (withEmptyEntries) {
            result.put("entity-type", "documents");
            result.put("entries", new JSONArray());
        }

        JSONObject obj = new JSONObject();
        obj.put("hasError", true);
        obj.put("httpResponseStatus", httpResponseStatus);
        obj.put("responseMessage", responseMessage);
        obj.put("appName", appName);
        if (exceptionAsJson != null) {
            obj.put("exception", exceptionAsJson);
        }
        result.put(MULTI_NUXEO_APPS_PROPERTY_NAME, obj);

        return result;
    }

    /**
     * 
     * @return
     * @since TODO
     */
    // Implemented only in sub-classes, but used in getBlob()
    public abstract NuxeoAppAuthentication getNuxeoAppAuthentication();
    
    /**
     * Check availability, trargeting the runningstatus endpoint (that does not require authentication)
     * 
     * @return
     * @since TODO
     */
    public boolean isServerAvailable() {
        try {
            
            HttpClient client = HttpClient.newBuilder()
                                           .connectTimeout(Duration.ofSeconds(5))
                                           .build();

            String healthStatusUrl = appUrl + "/runningstatus";
            HttpRequest request = HttpRequest.newBuilder(URI.create(healthStatusUrl))
                                                .timeout(Duration.ofSeconds(10))
                                                .GET()
                                                .build();

            @SuppressWarnings("unused")
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // With no error we consider the server is available
            // runningstatus can return 500, with on status failing but it's ok if not used (and we don't know if it is)
            return true;

        } catch (IOException | InterruptedException e) {
            // Connection refused, timeout, or other error
            return false;
        }
    }

    /**
     * Return the remote blob. {@code relativePath} is relative to the distant server (like "/nxfile/etc/etc")
     * <br>
     * If {@code returnRedirectInfo} is <code>true</code>, then the method does not download the blob to send it back to
     * the caller. Instead, if the distant server returned redirect data, it is returned as is, in a JSONBlob. This
     * handles the case where the distant Nuxeo server is configured to use AWS directDownload, for example.
     * <br>
     * If {@code returnRedirectInfo} is <code>false</code>, the method always returns the Blob.
     * 
     * @param relativePath
     * @param returnRedirectInfo
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @since 2023
     */
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
