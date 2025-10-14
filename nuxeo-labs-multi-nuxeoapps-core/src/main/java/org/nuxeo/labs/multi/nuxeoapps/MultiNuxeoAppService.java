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

import java.util.List;

import org.json.JSONArray;

/**
 * Perform a search on several distant Nuxeo servers.
 * Earch access is configured via the "nuxeoapp" extension point.
 * Authentication is either BASIC or JWT Token.
 * If one needs to tune for different users, then contribute as many "nuxeoapp" as required.
 * The main search methods accept a list of nuxeoapps to use (or "all").
 * Also, it is possible to dynamically change the user:
 * -> for token authentication.
 * -> Also for BASIC, which is to be used only for testing, since you'll have to pass user and pwd to the methods
 * 
 * @since 2023
 */
public interface MultiNuxeoAppService {

    /**
     * Return a JSONArray of NuxeoApps, with all their fields?
     * Usefull for modifing some and call {@code call(JSONArray customApps, ...etc...} with modified values (specific
     * user for token auth, etc.)
     * 
     * @return
     * @since 2023
     */
    JSONArray getNuxeoApps();
    
    NuxeoApp getNuxeoApp(String appName);

    /**
     * Returns a JSONArray, one entry/app with the usual JSON of a search result.
     * if nxql is passed it is used as is. Else a fulltext search is performed, using fulltextSearchValues as keywords
     * Each entry has a couple more properties:
     * httpResponseStatus and appName
     * And for each document, there should be a docFullUrl String (the full URL to the dox) and appName
     * 
     * @param appsToUseStr. If null, empty or "all", use all contributed apps
     * @param nxql if not null or empty, this overrides the default NXQL
     * @param fulltextSearchValues, required if nxql not passed
     * @param enrichers, optional. If null, thumbnail is requested. If "", no enrichers is requested
     * @param properties, optional. If null, fetch dublincore,file,uid,common. Any other value is applied as is
     * @param pageIndex, optional default is 0
     * @return a JSONArray, one entry/app with the usual JSON of a search result
     * @since 2023
     */
    JSONArray call(String appsToUseStr, String nxql, String fulltextSearchValues, String enrichers, String properties,
            int pageIndex);

    /**
     * Same as
     * {@code call(String appsToUseStr, String nxql, String fulltextSearchValues, String enrichers, String properties, int pageIndex)},
     * but we don't use the contributed NuxeApps, and instead we use the JSONArray listing the apps to use.
     * UseCase: Calling for different users than the ones declared in the contribution.
     * (This just creates a List<NuxeoApp> from the JSONArray and call the corresponding method)
     * Typicvally:
     * 1. Get the NuxeoApps ({@code List<NuxeoApp> getNuxeoApps())
     * 2. Duplicate (as they are immutable)
     * @param customApps
     * 
     * @param nxql if not null or empty, this overrides the default NXQL
     * @param fulltextSearchValues, required if nxql not passed
     * @param enrichers
     * @param properties
     * @param pageIndex
     * @return
     * @since 2023
     */
    JSONArray call(JSONArray customApps, String nxql, String fulltextSearchValues, String enrichers, String properties,
            int pageIndex);

    /**
     * Same as
     * {@code call(String appsToUseStr, String nxql, String fulltextSearchValues, String enrichers, String properties, int pageIndex)},
     * but we don't use the contributed NuxeApps, and instead we use the customApps listing the apps to use.
     * UseCase: Calling for different users than the ones declared in the contribution.
     * 
     * @param customApps
     * @param nxql if not null or empty, this overrides the default NXQL
     * @param fulltextSearchValues, required if nxql not passed
     * @param enrichers
     * @param properties
     * @param pageIndex
     * @return
     * @since 2023
     */
    JSONArray call(List<NuxeoApp> customApps, String nxql, String fulltextSearchValues, String enrichers,
            String properties, int pageIndex);

}
