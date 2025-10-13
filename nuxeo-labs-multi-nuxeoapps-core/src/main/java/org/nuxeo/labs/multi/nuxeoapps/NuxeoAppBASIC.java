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

import java.io.UnsupportedEncodingException;
import java.util.Base64;

import org.json.JSONObject;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * 
 * @since TODO
 */
public class NuxeoAppBASIC implements NuxeoAppAuthentication {

    protected String user;

    protected String pwd;

    protected String basicAuthHeaderValue = null;

    public NuxeoAppBASIC(String user, String pwd) {

        this.user = user;
        this.pwd = pwd;
        
        String auth = user + ":" + pwd;
        try {
            basicAuthHeaderValue = Base64.getEncoder().encodeToString(auth.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new NuxeoException("Invalid basic user or pwd => failed to base64 encode it.", e);
        }
    }
    
    @Override
    public JSONObject toJSONObject() {
        
        JSONObject obj = new JSONObject();
        obj.put("basicUser", user);
        obj.put("basicPwd", pwd);
        
        return obj;
    }

    public static boolean hasEnoughValues(JSONObject obj) {

        return obj.has("basicUser") && obj.has("basicPwd");
    }
    
    @Override
    public String getAutorizationHeaderValue(String user) {
        // We ignore the parameter.
        return "Basic " + basicAuthHeaderValue;
    }
    

}
