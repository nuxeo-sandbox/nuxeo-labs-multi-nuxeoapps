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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.util.PageProviderHelper;
import org.nuxeo.ecm.automation.jaxrs.io.documents.PaginableDocumentModelListImpl;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.io.registry.MarshallerHelper;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Extension;

public class MultiNuxeoAppServiceImpl extends DefaultComponent implements MultiNuxeoAppService {

    private static final Logger log = LogManager.getLogger(MultiNuxeoAppServiceImpl.class);

    protected static final String EXT_POINT = "nuxeoapp";

    protected Map<String, NuxeoApp> configuredNuxeoApps = new HashMap<String, NuxeoApp>();

    @Inject
    CoreSession session;

    @Override
    public JSONArray getNuxeoApps() {

        JSONArray array = new JSONArray();
        if (configuredNuxeoApps != null) {
            for (Map.Entry<String, NuxeoApp> entry : configuredNuxeoApps.entrySet()) {
                NuxeoApp app = entry.getValue();
                array.put(app.toJSONObject());
            }
        }

        return array;
    }

    // ====================================================
    // Multi thread search, for speed.
    // ====================================================
    protected String getCurrentUserName() {
        String currentUser = null;
        NuxeoPrincipal pcipal = NuxeoPrincipal.getCurrent();
        if (pcipal != null && StringUtils.isNotBlank(pcipal.getName())) {
            currentUser = pcipal.getName();
        }
        return currentUser;
    }

    protected JSONArray fetchAllAsync(List<NuxeoApp> nuxeoApps, String currentUser, String nxql, String enrichers,
            String properties, int pageIndex) {

        int maxProcessors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int maxApps = Math.max(1, nuxeoApps.size());
        int poolSize = Math.min(maxProcessors, maxApps);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);

        try {
            List<CompletableFuture<JSONObject>> futures = new ArrayList<>();

            for (NuxeoApp app : nuxeoApps) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return app.call(currentUser, nxql, enrichers, properties, pageIndex);
                    } catch (Exception e) {
                        JSONObject err = new JSONObject();
                        err.put("app", app.getAppName());
                        err.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                        return err;
                    }
                }, pool));
            }

            // Wait and collect
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            JSONArray finalResult = new JSONArray();
            for (CompletableFuture<JSONObject> f : futures) {
                finalResult.put(f.join());
            }
            return finalResult;

        } finally {
            pool.shutdown();
        }
    }
    // ====================================================
    // ====================================================
    // ====================================================

    @Override
    public JSONArray call(List<NuxeoApp> nuxeoApps, String nxql, String fulltextSearchValues, String enrichers,
            String properties, int pageIndex) {

        if (StringUtils.isAllBlank(fulltextSearchValues, nxql)) {
            throw new IllegalArgumentException("Both fulltextSearchValues and nxql can't be empty.");
        }

        if (StringUtils.isBlank(nxql)) {
            nxql = "SELECT * FROM Document WHERE ecm:fulltext='" + fulltextSearchValues + "'";
            nxql += " AND ecm:isVersion = 0 AND ecm:isProxy = 0 AND ecm:isTrashed = 0";
            nxql += " AND ecm:mixinType != 'HiddenInNavigation'";
        }

        if (enrichers == null) {
            enrichers = "thumbnail";
        }

        if (properties == null) {
            properties = "dublincore,file,uid,common";
        }

        JSONArray finalResult;
        if (nuxeoApps.size() == 0) {
            finalResult = new JSONArray();
            JSONObject obj = NuxeoApp.generateErrorObject(-1, "", "No Application to call", false, null);
            finalResult.put(obj);
        } else if (nuxeoApps.size() == 1) {
            JSONObject result = nuxeoApps.get(0).call(nxql, enrichers, properties, pageIndex);
            finalResult = new JSONArray();
            finalResult.put(result);
        } else {
            /*
             * finalResult = new JSONArray();
             * for (NuxeoApp oneApp : nuxeoApps) {
             * JSONObject result = oneApp.call(nxql, enrichers, properties, pageIndex);
             * finalResult.put(result);
             * }
             */
            finalResult = fetchAllAsync(nuxeoApps, getCurrentUserName(), nxql, enrichers, properties, pageIndex);
        }

        return finalResult;

    }

    @Override
    public JSONArray call(String appsToUseStr, String nxql, String fulltextSearchValues, String enrichers,
            String properties, int pageIndex) {

        List<NuxeoApp> appsToUse;
        if (StringUtils.isBlank(appsToUseStr) || "all".equals(appsToUseStr.toLowerCase())) {
            appsToUse = configuredNuxeoApps.entrySet().stream().map(Map.Entry::getValue).toList();
        } else {
            Set<String> keysToUse = Arrays.stream(appsToUseStr.split(","))
                                          .map(String::trim)
                                          .filter(s -> !s.isEmpty())
                                          .collect(Collectors.toSet());
            appsToUse = configuredNuxeoApps.entrySet()
                                           .stream()
                                           .filter(e -> keysToUse.contains(e.getKey()))
                                           .map(Map.Entry::getValue)
                                           .toList();
        }

        return call(appsToUse, nxql, fulltextSearchValues, enrichers, properties, pageIndex);
    }

    @Override
    public JSONArray call(JSONArray customApps, String nxql, String fulltextSearchValues, String enrichers,
            String properties, int pageIndex) {

        List<NuxeoApp> nuxeoApps = new ArrayList<NuxeoApp>();

        for (int i = 0; i < customApps.length(); i++) {
            JSONObject json = customApps.getJSONObject(i);
            nuxeoApps.add(NuxeoApp.fromJSONObject(json));
        }

        return call(nuxeoApps, nxql, fulltextSearchValues, enrichers, properties, pageIndex);

    }

    protected JSONObject searchCurrentNuxeo(String finalNxql, String enrichers, String properties, int pageIndex) {

        PageProviderDefinition def = PageProviderHelper.getQueryPageProviderDefinition(finalNxql, null, true, true);
        @SuppressWarnings("unchecked")
        PageProvider<DocumentModel> pp = (PageProvider<DocumentModel>) PageProviderHelper.getPageProvider(session, def,
                null, null, null, null/* pageSize */, pageIndex, null);

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
            return new JSONObject(resultJsonStr);

        } catch (IOException e) {

            JSONObject result = NuxeoApp.generateErrorObject(-1, "An error occured: " + e.getMessage(),
                    "Current Nuxeo Server", true, null);

            return result;
        }
    }

    // ======================================================================
    // ======================================================================
    // Service Configuration
    // ======================================================================
    // ======================================================================
    /**
     * Component activated notification.
     * Called when the component is activated. All component dependencies are resolved at that moment.
     * Use this method to initialize the component.
     *
     * @param context the component context.
     */
    @Override
    public void activate(ComponentContext context) {
        super.activate(context);
    }

    /**
     * Component deactivated notification.
     * Called before a component is unregistered.
     * Use this method to do cleanup if any and free any resources held by the component.
     *
     * @param context the component context.
     */
    @Override
    public void deactivate(ComponentContext context) {
        super.deactivate(context);
    }

    /**
     * Registers the given extension.
     *
     * @param extension the extension to register
     */
    @Override
    public void registerExtension(Extension extension) {
        super.registerExtension(extension);

        if (configuredNuxeoApps == null) {
            configuredNuxeoApps = new HashMap<String, NuxeoApp>();
        }

        if (EXT_POINT.equals(extension.getExtensionPoint())) {
            Object[] contribs = extension.getContributions();
            if (contribs != null) {
                for (Object contrib : contribs) {
                    NuxeoAppDescriptor desc = (NuxeoAppDescriptor) contrib;
                    desc.expandEnvironmentVariables();
                    NuxeoApp nxApp = desc.createNuxeoApp();
                    configuredNuxeoApps.put(nxApp.getAppName(), nxApp);
                }
            }
        }
    }

    /**
     * Unregisters the given extension.
     *
     * @param extension the extension to unregister
     */
    @Override
    public void unregisterExtension(Extension extension) {
        super.unregisterExtension(extension);

        if (configuredNuxeoApps == null) {
            return;
        }

        if (EXT_POINT.equals(extension.getExtensionPoint())) {

            Object[] contribs = extension.getContributions();
            if (contribs != null) {
                for (Object contrib : contribs) {
                    NuxeoAppDescriptor desc = (NuxeoAppDescriptor) contrib;
                    configuredNuxeoApps.remove(desc.getAppName());
                }
            }
        }

    }

    /**
     * Start the component. This method is called after all the components were resolved and activated
     *
     * @param context the component context. Use it to get the current bundle context
     */
    @Override
    public void start(ComponentContext context) {
        if (configuredNuxeoApps == null) {
            log.warn("No configuration found for MultiNuxeoAppsPageProvider.");
        }
    }

    /**
     * Stop the component.
     *
     * @param context the component context. Use it to get the current bundle context
     * @throws InterruptedException
     */
    @Override
    public void stop(ComponentContext context) throws InterruptedException {

        // Nothing for now

    }
}
