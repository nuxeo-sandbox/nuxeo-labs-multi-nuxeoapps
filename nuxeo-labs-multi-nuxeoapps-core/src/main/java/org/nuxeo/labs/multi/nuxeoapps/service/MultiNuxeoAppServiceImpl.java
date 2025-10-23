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
package org.nuxeo.labs.multi.nuxeoapps.service;

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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.labs.multi.nuxeoapps.AbstractNuxeoApp;
import org.nuxeo.labs.multi.nuxeoapps.NuxeoApp;
import org.nuxeo.labs.multi.nuxeoapps.NuxeoAppCurrent;
import org.nuxeo.labs.multi.nuxeoapps.Utilities;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Extension;

public class MultiNuxeoAppServiceImpl extends DefaultComponent implements MultiNuxeoAppService {

    public static final String CALL_PARAMETERS_PROPERTY = "MultiNxApps_CallParameters";

    private static final Logger log = LogManager.getLogger(MultiNuxeoAppServiceImpl.class);

    protected static final String EXT_POINT = "nuxeoapp";

    protected Map<String, NuxeoApp> configuredNuxeoApps = new HashMap<String, NuxeoApp>();

    protected List<AbstractNuxeoApp> allApps = new ArrayList<AbstractNuxeoApp>();
    
    protected static boolean doFullStackOnError = false;

    protected static boolean alwaysSearchLocalNuxeo = true;

    @Override
    public JSONObject tuneNuxeoApps(JSONObject params) {
        
        JSONObject previousValues = new JSONObject();
        
        previousValues.put("doFullStackOnError", MultiNuxeoAppServiceImpl.doFullStackOnError);
        previousValues.put("alwaysSearchLocalNuxeo", MultiNuxeoAppServiceImpl.alwaysSearchLocalNuxeo);

        if (params.has("doFullStackOnError")) {
            boolean doFullStackOnError = params.getBoolean("doFullStackOnError");
            if(doFullStackOnError != MultiNuxeoAppServiceImpl.doFullStackOnError) {
                MultiNuxeoAppServiceImpl.doFullStackOnError = doFullStackOnError;
                allApps.forEach(app -> app.setFullStackOnError(doFullStackOnError));
            }
        }

        if (params.has("alwaysSearchLocalNuxeo")) {
            boolean alwaysSearchLocalNuxeo = params.getBoolean("alwaysSearchLocalNuxeo");
            MultiNuxeoAppServiceImpl.alwaysSearchLocalNuxeo = alwaysSearchLocalNuxeo;
        }
        
        return previousValues;
    }

    @Override
    public NuxeoApp getNuxeoApp(String appName) {
        return configuredNuxeoApps.get(appName);
    }

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

    @Override
    public List<NuxeoApp> appNamesToNuxeoAppList(String appsToUse) {

        List<NuxeoApp> nuxeoApps;
        if (StringUtils.isBlank(appsToUse) || "all".equals(appsToUse.toLowerCase())) {
            nuxeoApps = configuredNuxeoApps.entrySet().stream().map(Map.Entry::getValue).toList();
        } else {
            Set<String> keysToUse = Arrays.stream(appsToUse.split(","))
                                          .map(String::trim)
                                          .filter(s -> !s.isEmpty())
                                          .collect(Collectors.toSet());
            nuxeoApps = configuredNuxeoApps.entrySet()
                                           .stream()
                                           .filter(e -> keysToUse.contains(e.getKey()))
                                           .map(Map.Entry::getValue)
                                           .toList();
        }

        return nuxeoApps;

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
            String properties, int pageIndex, int pageSize) {

        int maxProcessors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int maxApps = Math.max(1, nuxeoApps.size());
        int poolSize = Math.min(maxProcessors, maxApps);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);

        try {
            List<CompletableFuture<JSONObject>> futures = new ArrayList<>();

            for (NuxeoApp app : nuxeoApps) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return app.call(currentUser, nxql, enrichers, properties, pageIndex, pageSize);
                    } catch (Exception e) {
                        JSONObject err = AbstractNuxeoApp.generateErrorObject(-1, e.getMessage(), app.getAppName(),
                                true, e);
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

    protected JSONArray fetchAllAsync(List<NuxeoApp> nuxeoApps, String currentUser, String pageProvider,
            String queryParams, Map<String, String> namedParams, String enrichers, String properties, int pageIndex,
            int pageSize) {

        int maxProcessors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int maxApps = Math.max(1, nuxeoApps.size());
        int poolSize = Math.min(maxProcessors, maxApps);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);

        try {
            List<CompletableFuture<JSONObject>> futures = new ArrayList<>();

            for (NuxeoApp app : nuxeoApps) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return app.call(currentUser, pageProvider, queryParams, namedParams, enrichers, properties,
                                pageIndex, pageSize);
                    } catch (Exception e) {
                        JSONObject err = AbstractNuxeoApp.generateErrorObject(-1, e.getMessage(), app.getAppName(),
                                true, e);
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
    public JSONObject call(List<NuxeoApp> nuxeoApps, String nxql, String fulltextSearchValues, String enrichers,
            String properties, int pageIndex, int pageSize) {

        if (StringUtils.isAllBlank(fulltextSearchValues, nxql)) {
            throw new IllegalArgumentException("Both fulltextSearchValues and nxql can't be empty.");
        }

        // ====================> Store now search info as received
        JSONObject callParameters = new JSONObject();
        callParameters.put("applications", nuxeoApps);
        callParameters.put("nxql", Utilities.returnNullAsStringIfNeeded(nxql));
        // orj.json.JSONOBject#put remove the key of the value passed is null.
        callParameters.put("fulltextSearchValues", Utilities.returnNullAsStringIfNeeded(fulltextSearchValues));
        callParameters.put("enrichers", Utilities.returnNullAsStringIfNeeded(enrichers));
        callParameters.put("properties", Utilities.returnNullAsStringIfNeeded(properties));
        callParameters.put("pageIndex", pageIndex);
        callParameters.put("pageSize", pageSize);

        // ====================> Work
        if (StringUtils.isBlank(nxql)) {
            nxql = "SELECT * FROM Document WHERE ecm:fulltext='" + fulltextSearchValues + "'";
            nxql += " AND ecm:isVersion = 0 AND ecm:isProxy = 0 AND ecm:isTrashed = 0";
            nxql += " AND ecm:mixinType != 'HiddenInNavigation'";
        }

        if (enrichers == null) {
            enrichers = "";
        }

        if (properties == null) {
            properties = "";
        }

        JSONArray allresults;
        if (nuxeoApps.size() == 0) {
            allresults = new JSONArray();
            JSONObject obj = AbstractNuxeoApp.generateErrorObject(-1, "", "No Application to call", false,
                    (Throwable) null);
            allresults.put(obj);
        } else if (nuxeoApps.size() == 1) {
            JSONObject result = nuxeoApps.get(0).call(nxql, enrichers, properties, pageIndex, pageSize);
            allresults = new JSONArray();
            allresults.put(result);
        } else {
            allresults = fetchAllAsync(nuxeoApps, getCurrentUserName(), nxql, enrichers, properties, pageIndex,
                    pageSize);
        }

        // Now, search current Nuxeo?
        if (alwaysSearchLocalNuxeo) {
            NuxeoPrincipal pcipal = NuxeoPrincipal.getCurrent();
            CoreSession session = CoreInstance.getCoreSession(null, pcipal);
            JSONObject localSearchObj = NuxeoAppCurrent.getInstance()
                                                       .search(session, nxql, enrichers, properties, pageIndex,
                                                               pageSize);
            allresults.put(localSearchObj);
        }

        JSONObject finalResultObj = new JSONObject();
        finalResultObj.put(CALL_PARAMETERS_PROPERTY, callParameters);
        finalResultObj.put("results", allresults);

        return finalResultObj;

    }

    @Override
    public JSONObject call(String appsToUseStr, String nxql, String fulltextSearchValues, String enrichers,
            String properties, int pageIndex, int pageSize) {

        List<NuxeoApp> appsToUse = appNamesToNuxeoAppList(appsToUseStr);

        return call(appsToUse, nxql, fulltextSearchValues, enrichers, properties, pageIndex, pageSize);
    }

    @Override
    public JSONObject call(JSONArray appsToUse, String nxql, String fulltextSearchValues, String enrichers,
            String properties, int pageIndex, int pageSize) {

        List<NuxeoApp> nuxeoApps = new ArrayList<NuxeoApp>();

        for (int i = 0; i < appsToUse.length(); i++) {
            JSONObject json = appsToUse.getJSONObject(i);
            nuxeoApps.add(NuxeoApp.fromJSONObject(json));
        }

        return call(nuxeoApps, nxql, fulltextSearchValues, enrichers, properties, pageIndex, pageSize);

    }

    @Override
    public JSONObject callPageProvider(List<NuxeoApp> nuxeoApps, String pageProvider, String queryParams,
            Map<String, String> namedParams, String enrichers, String properties, int pageIndex, int pageSize) {

        if (nuxeoApps == null) {
            throw new IllegalArgumentException("No appsToUse.");
        }
        if (StringUtils.isAllBlank(pageProvider)) {
            throw new IllegalArgumentException("No Page Provider.");
        }

        // ====================> Store now search info as received
        JSONObject callParameters = new JSONObject();
        String allNames = nuxeoApps.stream().map(NuxeoApp::getAppName).collect(Collectors.joining(","));
        callParameters.put("applications", allNames);
        callParameters.put("pageProvider", Utilities.returnNullAsStringIfNeeded(pageProvider));
        callParameters.put("queryParams", Utilities.returnNullAsStringIfNeeded(queryParams));
        if (namedParams == null) {
            namedParams = null;
        } else {
            allNames = namedParams.entrySet()
                                  .stream()
                                  .map(e -> e.getKey() + "=" + e.getValue())
                                  .collect(Collectors.joining(","));
        }
        callParameters.put("namedParams", Utilities.returnNullAsStringIfNeeded(allNames));
        callParameters.put("enrichers", Utilities.returnNullAsStringIfNeeded(enrichers));
        callParameters.put("properties", Utilities.returnNullAsStringIfNeeded(properties));
        callParameters.put("pageIndex", pageIndex);
        callParameters.put("pageSize", pageSize);

        // ====================> Work
        if (enrichers == null) {
            enrichers = "";
        }

        if (properties == null) {
            properties = "";
        }
        JSONArray allresults;
        if (nuxeoApps.size() == 0) {
            allresults = new JSONArray();
            JSONObject obj = AbstractNuxeoApp.generateErrorObject(-1, "", "No Application to call", false,
                    (Throwable) null);
            allresults.put(obj);
        } else if (nuxeoApps.size() == 1) {
            JSONObject result = nuxeoApps.get(0)
                                         .call(pageProvider, queryParams, namedParams, enrichers, properties, pageIndex,
                                                 pageSize);
            allresults = new JSONArray();
            allresults.put(result);
        } else {
            allresults = fetchAllAsync(nuxeoApps, getCurrentUserName(), pageProvider, queryParams, namedParams,
                    enrichers, properties, pageIndex, pageSize);
        }

        // Now, search current Nuxeo?
        if (alwaysSearchLocalNuxeo) {
            NuxeoPrincipal pcipal = NuxeoPrincipal.getCurrent();
            CoreSession session = CoreInstance.getCoreSession(null, pcipal);
            JSONObject localSearchObj = NuxeoAppCurrent.getInstance()
                                                       .search(session, pageProvider, queryParams, namedParams,
                                                               enrichers, properties, pageIndex, pageSize);
            allresults.put(localSearchObj);
        }

        JSONObject finalResultObj = new JSONObject();
        finalResultObj.put(CALL_PARAMETERS_PROPERTY, callParameters);
        finalResultObj.put("results", allresults);

        return finalResultObj;
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
        if (configuredNuxeoApps == null || configuredNuxeoApps.size() == 0) {
            log.warn("No configuration found for MultiNuxeoAppsPageProvider.");
        }

        allApps = new ArrayList<>(configuredNuxeoApps.values());
        allApps.add(NuxeoAppCurrent.getInstance());
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
