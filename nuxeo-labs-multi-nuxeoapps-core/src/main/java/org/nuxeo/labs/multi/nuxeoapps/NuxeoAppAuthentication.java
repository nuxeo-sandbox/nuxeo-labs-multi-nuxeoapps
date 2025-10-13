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

import org.json.JSONObject;

/**
 * @since TODO
 */
public interface NuxeoAppAuthentication {

    /**
     * For JWT authenticaiton, {@code user} is used only once. After the call, it is reset internally.
     * For BASIC authentication, {@code user} is not used at all
     * 
     * @param user
     * @return the value to pust in the "Authoriation" header. Includes "Basic " or "Bearer " prefix.
     * @since 2023
     */
    String getAutorizationHeaderValue(String user);

    default String getAutorizationHeaderValue() {
        return getAutorizationHeaderValue(null);
    }

    JSONObject toJSONObject();

}
