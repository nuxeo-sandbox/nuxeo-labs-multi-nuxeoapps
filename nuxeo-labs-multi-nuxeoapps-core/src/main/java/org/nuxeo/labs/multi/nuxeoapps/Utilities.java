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

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @since 2023
 */
public class Utilities {
    
    public static final String NULL_VALUE_FOR_JSON = "(null)";

    /**
     * @param e
     * @return
     * @since TODO
     */
    public static JSONObject exceptionToJson(Throwable exception) {

        JSONObject json = new JSONObject();
        json.put("className", exception.getClass().getName());
        json.put("message", exception.getMessage());
        json.put("localizedMessage", exception.getLocalizedMessage());

        // Stack trace as array
        JSONArray stack = new JSONArray();
        for (StackTraceElement el : exception.getStackTrace()) {
            stack.put(el.toString());
        }
        json.put("stackTrace", stack);

        // Handle cause recursively (if any)
        Throwable cause = exception.getCause();
        if (cause != null && cause != exception) {
            json.put("cause", exceptionToJson(cause));
        } else {
            json.put("cause", (String) null);
        }

        return json;
    }
    
    /**
     * Formatting null values so they are displayed as "(null)"
     * 
     * @param value
     * @return
     * @since TODO
     */
    public static String returnNullAsStringIfNeeded(String value) {
        
        if(value == null) {
            return NULL_VALUE_FOR_JSON;
        }
        
        return value;
        
    }

}
