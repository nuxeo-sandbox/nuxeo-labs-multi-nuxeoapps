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

import static org.nuxeo.ecm.jwt.JWTServiceImpl.NUXEO_ISSUER;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

/**
 * @since TODO
 */
public class NuxeoAppJWT implements NuxeoAppAuthentication {

    public static final String TAG_CURRENT_USER = "MULTI_NUXEO_APPS_JWT_CURRENT_USER";

    public static final String MULTI_NUXEO_APPS_PROPERTY_NAME = "multiNuxeoAppsInfo";

    protected String appUrl;

    protected String tokenUser;

    protected String tempTokenUser;

    protected String tokenClientId;

    protected String tokenClientSecret;

    protected String jwtSecret;

    public NuxeoAppJWT(String appUrl, String tokenUser, String tokenClientId, String tokenClientSecret,
            String jwtSecret) {

        this.appUrl = appUrl;
        this.tokenUser = tokenUser;
        this.tokenClientId = tokenClientId;
        this.tokenClientSecret = tokenClientSecret;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public JSONObject toJSONObject() {

        JSONObject obj = new JSONObject();
        obj.put("tokenUser", tokenUser);
        obj.put("tokenClientId", tokenClientId);
        obj.put("tokenClientSecret", tokenClientSecret);
        obj.put("jwtSecret", jwtSecret);

        return obj;
    }

    public static boolean hasEnoughValues(JSONObject obj) {

        return obj.has("tempTokenUser") && obj.has("tokenClientId") && obj.has("tokenClientSecret")
                && obj.has("jwtSecret");
    }

    @Override
    public String getAutorizationHeaderValue(String user) {

        if (StringUtils.isNoneBlank(user)) {
            tempTokenUser = user;
        }

        String headerValue = null;
        try {
            String tokenUrl = appUrl + "/oauth2/token";
            String postData = createTokenPOSTData();

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                                             .timeout(Duration.ofSeconds(20))
                                             .header("Content-Type", "application/x-www-form-urlencoded")
                                             .POST(HttpRequest.BodyPublishers.ofString(postData))
                                             .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject obj = new JSONObject(resp.body());
            String token = obj.getString("access_token");

            headerValue = "Bearer " + token;

        } catch (IOException | InterruptedException e) {
            headerValue = null;
        } finally {
            // ================== VERY IMPORTANT
            tempTokenUser = null;
            // =================================
        }

        return headerValue;

    }

    protected String getTokenUserId() {

        if (StringUtils.isNotBlank(tempTokenUser)) {
            return tempTokenUser;
        }

        String user = tokenUser;

        if (TAG_CURRENT_USER.equals(tokenUser)) {
            NuxeoPrincipal principal = NuxeoPrincipal.getCurrent();
            if (principal != null) {
                user = principal.getName();
                /*
                 * String actingUser = principal.getActingUser();
                 * if(StringUtils.isNotBlank(actingUser)) {
                 * user = actingUser;
                 * } else {
                 * user = principal.getName();
                 * }
                 */
            } else {
                throw new NuxeoException(
                        "Cannot resolve " + TAG_CURRENT_USER + ", there is no current NuxeoPrincipal.");
            }
        }

        return user;
    }

    protected String createTokenPOSTData() throws UnsupportedEncodingException {

        // Create signing key
        String assertion = JWT.create()
                              .withIssuer(NUXEO_ISSUER)
                              .withSubject(getTokenUserId())
                              .sign(Algorithm.HMAC512(jwtSecret));

        // Grant token
        String postData = "grant_type="
                + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8);
        postData += "&client_id=" + URLEncoder.encode(tokenClientId, StandardCharsets.UTF_8);
        postData += "&client_secret=" + URLEncoder.encode(tokenClientSecret, StandardCharsets.UTF_8);
        postData += "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);

        return postData;
    }

}
