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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.labs.multi.nuxeoapps.NuxeoApp;
import org.nuxeo.labs.multi.nuxeoapps.service.MultiNuxeoAppService;
import org.nuxeo.runtime.api.Framework;

/**
 * 
 * @since 2023
 */
public class NuxeoAppServlet extends HttpServlet  {
    
    private static final long serialVersionUID = 2975604269886022815L;

    public static final String MULTI_NUXEO_APPS_SERVLET_KEY = "multiNxApps";
    
    private static final Logger log = LogManager.getLogger(NuxeoAppServlet.class);
    
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        
        // My reminder: the path that comes after /multiNxApps/ is available directly via request.getPathInfo()
        // So if the full url is https://myserver.com/nuxeo/multiNxApps/myApp/distant/url/with/slashes,
        // getPathInfo() returns "/myApp/distant/url/with/slashes"
        String pathInfo = req.getPathInfo();
        // Security check, JIC
        FileUtils.checkPathTraversal(pathInfo);
        
        pathInfo = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
     
        int idx = pathInfo.indexOf('/');
        String appName;
        String remotePath;

        if (idx == -1) {
            // Only appName present
            throw new MalformedURLException("No remote path in the url.");
        }

        appName = pathInfo.substring(0, idx);
        remotePath = pathInfo.substring(idx);
        
        MultiNuxeoAppService service = Framework.getService(MultiNuxeoAppService.class);
        NuxeoApp remoteApp = service.getNuxeoApp(appName);
        if(remoteApp == null) {
            throw new NuxeoException("Application <" + appName + "> not found.");
        }
              
        // Call distant server
        Blob blob;
        try {
            blob = remoteApp.getBlob(remotePath, true);
        } catch (IOException | InterruptedException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to get the blob from remote Nuxeo App: " + e.getMessage());
            return;
        }
        
        if(blob instanceof JSONBlob) {
            // We do have a redirect
            JSONObject redirectInfoJson = new JSONObject(blob.getString());
            resp.setStatus(redirectInfoJson.getInt("status"));
            resp.setHeader("Location", redirectInfoJson.getString("location"));
            return;
        }

        // 4) Write blob to HttpServletResponse with correct headers
        String mimeType = blob.getMimeType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        }
        resp.setContentType(mimeType);

        resp.setHeader("Content-Length", Long.toString(blob.getLength()));

        String filename = blob.getFilename();
        if (StringUtils.isNotBlank(filename)) {
            // Content-Disposition (RFC 5987 for UTF-8 filename*)
            String ascii = filename.replace("\"", "");
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            resp.setHeader(
                "Content-Disposition",
                "inline; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded
            );
        }

        // Optional: weak caching hint (tune as needed)
        //resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        //resp.setHeader("Pragma", "no-cache");

        // Stream out
        try (InputStream in = blob.getStream();
             var out = resp.getOutputStream()) {
            in.transferTo(out);
        }
    }

}
