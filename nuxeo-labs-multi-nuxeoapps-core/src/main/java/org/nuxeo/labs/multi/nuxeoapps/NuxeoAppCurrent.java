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
import org.json.JSONObject;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.automation.core.util.PageProviderHelper;
import org.nuxeo.ecm.automation.jaxrs.io.documents.PaginableDocumentModelListImpl;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.io.registry.MarshallerHelper;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.labs.multi.nuxeoapps.authentication.NuxeoAppAuthentication;
import org.nuxeo.labs.multi.nuxeoapps.remote.AbstractNuxeoApp;
import org.nuxeo.labs.multi.nuxeoapps.remote.NuxeoApp;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 2023
 */
public class NuxeoAppCurrent extends AbstractNuxeoApp {

    private static final Logger log = LogManager.getLogger(NuxeoAppCurrent.class);

    public static final String CURRENT_NUXEO_DEFAULT_APPNAME = "CurrentNuxeoApp";

    protected static final NuxeoAppCurrent instance = new NuxeoAppCurrent();

    private NuxeoAppCurrent() {

        String appName = Framework.getProperty(Environment.PRODUCT_NAME);// "org.nuxeo.ecm.product.name"
        if (StringUtils.isBlank(appName)) {
            appName = CURRENT_NUXEO_DEFAULT_APPNAME;
        }

        String appUrl = Framework.getProperty("nuxeo.url");
        if (StringUtils.isBlank(appUrl)) {
            appUrl = "";
        }

        log.warn("NuxeoAppCurrent, appName=<" + appName + ">, appUrl=<" + appUrl + ">");

        initialize(appName, appUrl, true);

    }

    public static NuxeoAppCurrent getInstance() {
        return instance;
    }

    public JSONObject search(CoreSession session, String finalNxql, String enrichers, String properties,
            int pageIndex) {

        try {
            PageProviderDefinition def = PageProviderHelper.getQueryPageProviderDefinition(finalNxql, null, true, true);
            
            @SuppressWarnings("unchecked")
            PageProvider<DocumentModel> pp =
                    (PageProvider<DocumentModel>) PageProviderHelper.getPageProvider(
                        session,
                        def,
                        (Map<String, String>) null, // namedParameters
                        (List<String>) null,        // sortBy
                        (List<String>) null,        // sortOrder
                        (Long) null,                // pageSize
                        Long.valueOf(pageIndex)     // currentPageIndex
                    );
    
            PaginableDocumentModelListImpl res = new PaginableDocumentModelListImpl(pp);
            if (res.hasError()) {
                throw new NuxeoException(res.getErrorMessage());
            }
    
            String[] enrichersList = { "" };
            if (StringUtils.isNotBlank(enrichers)) {
                enrichersList = Arrays.stream(enrichers.split(","))
                                      .map(String::trim)
                                      .filter(s -> !s.isEmpty())
                                      .toArray(String[]::new);
            }
            String[] propertiesList = { "" };
            if (StringUtils.isNotBlank(properties)) {
                propertiesList = Arrays.stream(enrichers.split(","))
                                       .map(String::trim)
                                       .filter(s -> !s.isEmpty())
                                       .toArray(String[]::new);
            }
            RenderingContext rCtx = RenderingContext.CtxBuilder.enrichDoc(enrichersList).properties(propertiesList).get();
            String resultJsonStr;
            try {
                resultJsonStr = MarshallerHelper.objectToJson(DocumentModelList.class, res, rCtx);
                JSONObject result = new JSONObject(resultJsonStr);
                // Assume it's always 200 in this context
                updateEntries(result, 200);
                return result;
            } catch (IOException e) {
    
                JSONObject result = NuxeoApp.generateErrorObject(-1, "An error occured: " + e.getMessage(),
                        "Current Nuxeo Server", true, null);
    
                return result;
            }
        } catch (NuxeoException e) {
            // Typically, no fulltext index in unittest (for now?)
            JSONObject result = NuxeoApp.generateErrorObject(-1, "An error occured: " + e.getMessage(), getAppName(),
                    true, null);

            return result;
        }
    }

    @Override
    protected NuxeoAppAuthentication getNuxeoAppAuthentication() {
        // This one should never be called for the Currentnuxeo app
        throw new UnsupportedOperationException();
    }

}
