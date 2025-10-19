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

import org.json.JSONArray;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.labs.multi.nuxeoapps.service.MultiNuxeoAppService;

/**
 * @since 2023
 */
@Operation(id = GetRemoteNuxeoAppsConfigurationOp.ID, category = Constants.CAT_SERVICES, label = "Get Nuxeo Apps Configuration", description = ""
        + "return a JSON Array of the configuration for the Nuxeo Apps")
public class GetRemoteNuxeoAppsConfigurationOp {

    public static final String ID = "MultiNuxeoApps.GetNuxeoAppsConfigutation";

    @Context
    protected MultiNuxeoAppService service;

    @OperationMethod
    public Blob run() {

        JSONArray result = service.getNuxeoApps();

        return Blobs.createJSONBlob(result.toString());

    }
}
