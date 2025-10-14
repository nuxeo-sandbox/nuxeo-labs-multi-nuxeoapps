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

/**
 * 
 * @since TODO
 */
public class NuxeoAppServletUtils {
    
    public static List<String> BLOB_JSON_FIELDS = List.of("mime-type",
                                                            "digestAlgorithm",
                                                            "digest",
                                                            "length",
                                                            "data",
                                                            "blobUrl");
    
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
    
    public static void updateBlobUrlsInProperties(JSONObject oneDocObj, String appName, boolean isLocalNuxeo) {
        
        if(oneDocObj == null || isLocalNuxeo) {
            return;
        }
        
        String type = oneDocObj.optString("entity-type", null);
        if(!StringUtils.equals("document", type)) {
            throw new NuxeoException("Expting a document entity-type");
        }

        // ====================> properties
        JSONObject properties = oneDocObj.optJSONObject("properties", null);
        if(properties != null) {
        
            Deque<Object> stack = new ArrayDeque<>();
            stack.push(properties);
    
            while (!stack.isEmpty()) {
                Object node = stack.pop();
    
                if (node instanceof JSONObject obj) {
                    // If this object has a non-null "blobUrl", process it now
                    if (NuxeoAppServletUtils.looksLikeABlob(obj)) {
                        String blobUrl = obj.optString("blobUrl", null);
                        if (blobUrl != null) {
                            blobUrl = NuxeoAppServletUtils.buildMultiNxAppUrl(blobUrl, appName);
                            obj.put("data", blobUrl);
                            obj.put("blobUrl", blobUrl);
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
        if(ctxParams != null) {
            JSONObject thumbnailObj = ctxParams.optJSONObject("thumbnail", null);
            if(thumbnailObj != null) {
                String url = thumbnailObj.getString("url");
                url = NuxeoAppServletUtils.buildMultiNxAppUrl(url, appName);
                thumbnailObj.put("url", url);
            }
        }
        
    }
    
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
    
    public static String buildMultiNxAppUrl(String url, String appName) {
        
        return "/" + NuxeoAppServlet.MULTI_NUXEO_APPS_SERVLET_KEY + "/" + appName + removeUrlPrefix(url);
        
    }
    
    public static String restoreOriginalUrl(String url, String appUrl, String appName) {
        
        url = StringUtils.remove(url,  "/" + NuxeoAppServlet.MULTI_NUXEO_APPS_SERVLET_KEY + "/" + appName);
        if(!url.startsWith("/")) {
            url = "/" + url;
        }
        url = appUrl + url;
        
        return url;
    }
    
    /**
     * Not strictly speaking a BlobUrlUtils, but strangely enough, I did not find
     * a Nuxeo or Java API doing this
     * @param contentDisposition
     * @param url
     * @return
     * @since TODO
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
    

}
