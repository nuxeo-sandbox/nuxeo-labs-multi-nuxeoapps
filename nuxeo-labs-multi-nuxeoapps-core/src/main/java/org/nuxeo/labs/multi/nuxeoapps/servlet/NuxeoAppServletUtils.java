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
package org.nuxeo.labs.multi.nuxeoapps.servlet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.labs.multi.nuxeoapps.NuxeoAppCurrent;

/**
 * Utility class to handle URLs, mainly
 * 
 * @since 2023
 */
public class NuxeoAppServletUtils {

    public static List<String> BLOB_JSON_FIELDS = List.of("mime-type", "digestAlgorithm", "digest", "length", "data",
            "blobUrl");

    public static String SERVLET_BLOB_URL_PROPERTY = "blobUrl";

    /**
     * Check if the object has the regular Blob fields
     * 
     * @param obj
     * @return
     * @since 2023
     */
    public static boolean looksLikeABlob(JSONObject obj) {
        if (obj == null) {
            return false;
        }

        for (String oneField : BLOB_JSON_FIELDS) {
            if (!obj.has(oneField)) {
                return false;
            }
        }

        return true;
    }

    /**
     * {@code oneDocObj} must be a "document" entity-type.
     * The method gets the "properties" object of {@code oneDocObj} and add a URL pointing to the
     * {@link NuxeoAppServlet}
     * <br>
     * If {@code isLocalNuxeo} is <code>true</code>, the method looks for for possible "http://fake-url.nuxeo.com" set
     * by the RenderingContext, but does not set the url to the servlet
     * 
     * @param oneDocObj
     * @param appName
     * @param isLocalNuxeo
     * @since 2023
     */
    public static void updateBlobUrlsInProperties(JSONObject oneDocObj, String appName, boolean isLocalNuxeo) {

        if (oneDocObj == null) {
            return;
        }

        String type = oneDocObj.optString("entity-type", null);
        if (!StringUtils.equals("document", type)) {
            throw new NuxeoException("Expecting a \"document\" entity-type");
        }

        // ====================> properties
        JSONObject properties = oneDocObj.optJSONObject("properties", null);
        if (properties != null) {

            Deque<Object> stack = new ArrayDeque<>();
            stack.push(properties);

            while (!stack.isEmpty()) {
                Object node = stack.pop();

                if (node instanceof JSONObject obj) {
                    // If this object has a non-null "blobUrl", process it now
                    if (NuxeoAppServletUtils.looksLikeABlob(obj)) {
                        String blobUrl = obj.optString("blobUrl", null);
                        if (blobUrl != null) {
                            if (isLocalNuxeo) {
                                blobUrl = NuxeoAppCurrent.updateUrlIfNeeded(blobUrl);
                            } else {
                                blobUrl = NuxeoAppServletUtils.buildMultiNxAppUrl(blobUrl, appName);
                            }
                            obj.put(NuxeoAppServlet.MULTI_NUXEO_APPS_SERVLET_BLOB_URL_KEY, blobUrl);
                        }
                    }
                    // Traverse children
                    for (String key : obj.keySet()) {
                        Object child = obj.opt(key);
                        if (child instanceof JSONObject || child instanceof JSONArray) {
                            stack.push(child);
                        }
                    }

                } else if (node instanceof JSONArray arr) {
                    for (int i = 0; i < arr.length(); i++) {
                        Object child = arr.opt(i);
                        if (child instanceof JSONObject || child instanceof JSONArray) {
                            stack.push(child);
                        }
                    }
                }
            }
        }

        // ====================> contextParameters and thumbnail
        JSONObject ctxParams = oneDocObj.optJSONObject("contextParameters", null);
        if (ctxParams != null) {
            JSONObject thumbnailObj = ctxParams.optJSONObject("thumbnail", null);
            if (thumbnailObj != null) {
                String url = thumbnailObj.getString("url");
                if (isLocalNuxeo) {
                    url = NuxeoAppCurrent.updateUrlIfNeeded(url);
                } else {
                    url = NuxeoAppServletUtils.buildMultiNxAppUrl(url, appName);
                }
                thumbnailObj.put(NuxeoAppServlet.MULTI_NUXEO_APPS_SERVLET_BLOB_URL_KEY, url);
            }
        }

    }

    /**
     * Cleans up the {@code url} by removing the base url of the distant server. So, for example:
     * "https://my.server.com/nuxeo/nxfile/1234-abcd/etc/etc" becomes:
     * "/nxfile/1234-abcd/etc/etc"
     * 
     * @param url
     * @return
     * @since TODO
     */
    public static String removeUrlPrefix(String url) {

        if (StringUtils.isBlank(url)) {
            return "";
        }

        // Regex:
        // ^https?:// → match http:// or https://
        // [^/]+ → host (and optional port)
        // /[^/]+ → first path segment (like /nuxeo or /other)
        // (.*) → capture everything after that
        Pattern pattern = Pattern.compile("^https?://[^/]+/[^/]+(.*)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(url.trim());

        if (matcher.find()) {
            return matcher.group(1);
        }

        return url;
    }

    /**
     * Cleans up the URL: Replaces the original remote Nuxeo application base URL with access to the NuxeoAppServlet.
     * <br>
     * "https://my.server.com/nuxeo/nxfile/1234-abcd/etc/etc" becomes:
     * "/multiNxApps/DistantAppName/nxfile/1234-abcd/etc/etc"
     * 
     * @param url
     * @param appName
     * @return
     * @since TODO
     */
    public static String buildMultiNxAppUrl(String url, String appName) {

        return NuxeoAppCurrent.CONTEXT_PATH + "/" + NuxeoAppServlet.MULTI_NUXEO_APPS_SERVLET_KEY + "/" + appName
                + removeUrlPrefix(url);

    }

    /**
     * Cleans up the URL: Replaces the NuxeoAppServlet with the remote Nuxeo application base URL .
     * <br>
     * "/multiNxApps/DistantAppName/nxfile/1234-abcd/etc/etc" becomes
     * "https://my.server.com/nuxeo/nxfile/1234-abcd/etc/etc"
     * 
     * @param url
     * @param appUrl
     * @param appName
     * @return
     * @since TODO
     */
    public static String restoreOriginalUrl(String url, String appUrl, String appName) {

        url = StringUtils.remove(url,
                NuxeoAppCurrent.CONTEXT_PATH + "/" + NuxeoAppServlet.MULTI_NUXEO_APPS_SERVLET_KEY + "/" + appName);
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        url = appUrl + url;

        return url;
    }

    /**
     * Not strictly speaking a BlobUrlUtils, but strangely enough, I did not find
     * a Nuxeo or Java API doing this in a single call
     * 
     * @param contentDisposition
     * @param url
     * @return
     * @since 2023
     */
    public static String extractFileName(String contentDisposition, String url) {

        if (StringUtils.isNotBlank(contentDisposition)) {
            // Try filename*= (RFC 5987) first
            Matcher m = Pattern.compile("filename\\*=['\"]?UTF-8''([^'\"]+)").matcher(contentDisposition);
            if (m.find()) {
                return java.net.URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
            }
            // Fallback to plain filename=
            m = Pattern.compile("filename=['\"]?([^'\"]+)").matcher(contentDisposition);
            if (m.find()) {
                return m.group(1);
            }
        }

        // Fallback: take last segment of URL
        int idx = url.lastIndexOf('/');
        if (idx >= 0 && idx < url.length() - 1) {
            return url.substring(idx + 1);
        }

        // Default name
        return "downloaded-file";
    }

    /**
     * Yet another utility
     * 
     * @param code
     * @return
     * @since 2023
     */
    public static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

}
