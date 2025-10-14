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

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

@XObject("nuxeoapp")
public class NuxeoAppDescriptor {

    @XNode("appName")
    protected String appName;

    @XNode("appUrl")
    protected String appUrl;

    @XNode("basicUser")
    protected String basicUser;

    @XNode("basicPwd")
    protected String basicPwd;

    @XNode("tokenUser")
    protected String tokenUser;

    @XNode("tokenClientId")
    protected String tokenClientId;

    @XNode("tokenClientSecret")
    protected String tokenClientSecret;

    @XNode("jwtSecret")
    protected String jwtSecret;
    
    public String getAppName() {
        return appName;
    }
    
    /**
     * Mainly usefull in unit testing, to avoid setting up configuration pareameters, etc.
     * 
     * @since TODO
     */
    public void expandEnvironmentVariables() {
        appUrl = expandEnvironmentVariable(appUrl);
        
        basicUser = expandEnvironmentVariable(basicUser);
        basicPwd = expandEnvironmentVariable(basicPwd);
        
        tokenUser = expandEnvironmentVariable(tokenUser);
        tokenClientId = expandEnvironmentVariable(tokenClientId);
        tokenClientSecret = expandEnvironmentVariable(tokenClientSecret);
        jwtSecret = expandEnvironmentVariable(jwtSecret);
    }
    
    protected String expandEnvironmentVariable(String expression) {
        
        if(expression == null) {
            return null;
        }
        
        if(NuxeoAppAuthenticationJWT.TAG_CURRENT_USER.equals(expression)) {
            return expression;
        }
        
        String varValue = null;
        if(expression.startsWith("${")) {
            String varName = expression.substring(2);
            if(varName.endsWith(":=}")) {
                varName = varName.substring(0, varName.length() - 3);
            } else  if(varName.endsWith("}")) {
                varName = varName.substring(0, varName.length() - 1);
            } else {
                varName = null;
            }
            if(StringUtils.isNoneBlank(varName)) {
                if(NuxeoAppAuthenticationJWT.TAG_CURRENT_USER.equals(varName)) {
                    varValue = varName;
                } else {
                    varValue = System.getenv(varName);
                }
            }
        }
        if(StringUtils.isBlank(varValue)) {
            return expression;
        }
        return varValue;
    }
    
    public NuxeoApp createNuxeoApp() {
        
        if(StringUtils.isNoneBlank(basicUser, basicPwd)) {
            return new NuxeoApp(appName, appUrl, basicUser, basicPwd);
        }
        
        return new NuxeoApp(appName, appUrl, tokenUser, tokenClientId, tokenClientSecret, jwtSecret);
    }

}


