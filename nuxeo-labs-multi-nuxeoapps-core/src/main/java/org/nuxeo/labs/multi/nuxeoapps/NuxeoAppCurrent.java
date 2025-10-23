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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.automation.core.util.PageProviderHelper;
import org.nuxeo.ecm.automation.io.rest.documents.PaginableDocumentModelListImpl;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.io.registry.MarshallerHelper;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.labs.multi.nuxeoapps.authentication.NuxeoAppAuthentication;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 2023
 */
public class NuxeoAppCurrent extends AbstractNuxeoApp {

    private static final Logger log = LogManager.getLogger(NuxeoAppCurrent.class);

    public static final String CURRENT_NUXEO_DEFAULT_APPNAME = "Current Nuxeo Server";

    public static final String CONTEXT_PATH = Framework.getProperty("org.nuxeo.ecm.contextPath", "/nuxeo");

    protected static final NuxeoAppCurrent instance = new NuxeoAppCurrent();

    private NuxeoAppCurrent() {

        String appName = Framework.getProperty(Environment.PRODUCT_NAME);// "org.nuxeo.ecm.product.name"
        if (StringUtils.isBlank(appName) || "Nuxeo Platform".equals(appName)) {
            appName = CURRENT_NUXEO_DEFAULT_APPNAME;
        }

        String appUrl = Framework.getProperty("nuxeo.url");
        if (StringUtils.isBlank(appUrl)) {
            appUrl = "";
        }

        log.warn("NuxeoAppCurrent, appName=<" + appName + ">, appUrl=<" + appUrl + ">");

        initialize(appName, appUrl, true, AuthenticationType.NOT_NEEDED);

    }

    public static NuxeoAppCurrent getInstance() {
        return instance;
    }

    public JSONObject search(CoreSession session, String finalNxql, String enrichers, String properties, int pageIndex,
            int pageSize) {

        JSONObject result;
        try {
            PageProviderDefinition ppDef = PageProviderHelper.getQueryPageProviderDefinition(finalNxql, null, true,
                    true);

            result = doSearch(session, ppDef, null, null, enrichers, properties, pageIndex, pageSize);

        } catch (NuxeoException e) {
            // Typically a malformed NXQL but also things like no fulltext index in unit-test (for now?)
            String message = e.getMessage();
            JSONObject exceptionJson = null;
            if (fullStackOnError) {
                exceptionJson = Utilities.exceptionToJson(e);
            }
            result = generateErrorObject(-1, "An error occured: " + message, getAppName(), true, exceptionJson);
        }

        return result;

    }

    public JSONObject search(CoreSession session, String pageProvider, String queryParams,
            Map<String, String> namedParams, String enrichers, String properties, int pageIndex, int pageSize) {

        JSONObject result;
        try {
            PageProviderService ppService = Framework.getService(PageProviderService.class);
            PageProviderDefinition ppDef = ppService.getPageProviderDefinition(pageProvider);

            result = doSearch(session, ppDef, queryParams, namedParams, enrichers, properties, pageIndex, pageSize);

        } catch (NuxeoException e) {
            // Typically a malformed NXQL but also things like no fulltext index in unit-test (for now?)
            String message = e.getMessage();
            JSONObject exceptionJson = null;
            if (fullStackOnError) {
                exceptionJson = Utilities.exceptionToJson(e);
            }
            result = generateErrorObject(-1, "An error occured: " + message, getAppName(), true, exceptionJson);
        }

        return result;
    }

    @Override
    public NuxeoAppAuthentication getNuxeoAppAuthentication() {
        // This one should never be called for the Currentnuxeo app
        throw new UnsupportedOperationException();
    }

    /**
     * The RenderingContext can add a "http://fake-url.nuxeo.com/" prefix to a URL, we remove it and replace with an URL
     * a browser
     * can fetct (if user is logged in)
     * 
     * @param url
     * @return
     * @since 2023
     */
    public static String updateUrlIfNeeded(String url) {
        if (StringUtils.isNotBlank(url)) {
            if (url.startsWith(RenderingContext.DEFAULT_URL)) {
                url = url.replace(RenderingContext.DEFAULT_URL, "");
                if (!url.startsWith("/")) {
                    url = "/" + url;
                }
                url = NuxeoAppCurrent.CONTEXT_PATH + url;
            }
        }

        return url;
    }

    protected JSONObject doSearch(CoreSession session, PageProviderDefinition ppDef, String queryParams,
            Map<String, String> namedParams, String enrichers, String properties, int pageIndex, int pageSize) {

        if (pageIndex < 0) {
            pageIndex = 0;
        }
        if (pageSize < 1) {
            pageSize = NuxeoApp.DEFAULT_PAGE_SIZE;
        }

        Object[] queryParamsArray = null;
        if(StringUtils.isNotBlank(queryParams)) {
            queryParamsArray = Arrays.stream(queryParams.split(","))
                                     .filter(s -> !s.isEmpty())
                                     .map(String::trim)
                                     .toArray();
        }

        @SuppressWarnings("unchecked")
        PageProvider<DocumentModel> pp = (PageProvider<DocumentModel>) PageProviderHelper.getPageProvider(session,
                ppDef, namedParams, // namedParameters
                (List<String>) null, // sortBy
                (List<String>) null, // sortOrder
                Long.valueOf(pageSize), // pageSize
                Long.valueOf(pageIndex), // currentPageIndex
                queryParamsArray);

        PaginableDocumentModelListImpl paginableDocList = new PaginableDocumentModelListImpl(pp);
        if (paginableDocList.hasError()) {
            throw new NuxeoException(paginableDocList.getErrorMessage());
        }

        // Convert to JSON "documents" entity-type
        String[] enrichersList = { "" };
        if (StringUtils.isNotBlank(enrichers)) {
            enrichersList = Arrays.stream(enrichers.split(","))
                                  .map(String::trim)
                                  .filter(s -> !s.isEmpty())
                                  .toArray(String[]::new);
        }
        String[] propertiesList = { "" };
        if (StringUtils.isNotBlank(properties)) {
            propertiesList = Arrays.stream(properties.split(","))
                                   .map(String::trim)
                                   .filter(s -> !s.isEmpty())
                                   .toArray(String[]::new);
        }
        RenderingContext rCtx = RenderingContext.CtxBuilder.enrichDoc(enrichersList).properties(propertiesList).get();

        JSONObject result;
        try {
            String resultJsonStr = MarshallerHelper.objectToJson(DocumentModelList.class, paginableDocList, rCtx);
            result = new JSONObject(resultJsonStr);
            
            // Strange enough, but no time to explore. If "thumbnail" enricher is required, it is not added to the JSON
            // I suspect it's only an issue in unit-tests though
            for (String s : enrichersList) {
                if (s.equals("thumbnail")) {
                    try {
                        
                        JSONArray entries = result.getJSONArray(("entries"));
                        for(int i = 0; i < entries.length(); i++) {
                            
                            JSONObject oneEntry = entries.getJSONObject(i);
                            
                            JSONObject contextParameters = oneEntry.optJSONObject("contextParameters");
                            if(contextParameters == null) {
                                contextParameters = new JSONObject();
                            }
                            JSONObject thumbnailObj = contextParameters.optJSONObject("thumbnail");
                            if(thumbnailObj == null) {
                                String docUid = oneEntry.getString("uid");
                                DocumentModel doc = session.getDocument(new IdRef(docUid));
                                String changeToken = doc.getChangeToken();
                                thumbnailObj = new JSONObject();
                                String thumbUrl = "/api/v1/repo/default/id/" + docUid + "/@rendition/thumbnail?changeToken=" + changeToken + "&sync=true";
                                thumbnailObj.put("url", thumbUrl);
                                contextParameters.put("thumbnail",  thumbnailObj);
                                oneEntry.put("contextParameters",  contextParameters);
                            }
                        }
                    } catch (JSONException e) {
                        // Ignore
                    }
                    break;
                }
            }
            
            // Assume it's always 200 in this context
            updateDocumentsEntityType(result, 200);
        } catch (IOException e) {
            result = generateErrorObject(-1, "An error occured: " + e.getMessage(), getAppName(), true,
                    fullStackOnError ? e : (Throwable) null);
        }

        return result;

    }

}
