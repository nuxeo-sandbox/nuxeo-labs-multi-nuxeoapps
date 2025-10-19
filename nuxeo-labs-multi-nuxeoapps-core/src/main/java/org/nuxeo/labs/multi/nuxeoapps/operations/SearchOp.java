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
package org.nuxeo.labs.multi.nuxeoapps.operations;

import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.labs.multi.nuxeoapps.service.MultiNuxeoAppService;

/**
 * @since 2023
 */
@Operation(id = SearchOp.ID, category = Constants.CAT_SERVICES, label = "Search in All Nuxeo Apps", description = ""
        + "See plugin documentations (or automation doc.) for details on parameters and returned JSON objet")
public class SearchOp {

    public static final String ID = "MultiNuxeoApps.MultiNuxeoAppsSearch";

    @Context
    protected MultiNuxeoAppService service;

    @Param(name = "nuxeoApps", required = false, description = "List of configured apps to call, comma separated. empty or 'all' => all apps")
    protected String nuxeoApps;

    @Param(name = "nxql", required = false, description = "NXQL expression to run. If not passed, fullTextKeywords is required.")
    protected String nxql;

    @Param(name = "fullTextKeywords", required = false, description = "Used when nxql is empty. A default fulltext search is provided")
    protected String fullTextKeywords;

    @Param(name = "enrichers", required = false, description = "Comma separated list of enrichers.")
    protected String enrichers;

    @Param(name = "properties", required = false, description = "comma separated list of properties")
    protected String properties;

    @Param(name = "pageIndex", required = false, description = "Page to fetch. Used if > 1")
    protected int pageIndex = 0;

    @Param(name = "pageSize", required = false, description = "Page size. Used if > 1, else a default  value applies")
    protected int pageSize = 0;

    @OperationMethod
    public Blob run() {

        JSONObject result = service.call(nuxeoApps, nxql, fullTextKeywords, enrichers, properties, pageIndex, pageSize);

        return Blobs.createJSONBlob(result.toString());

    }
}
