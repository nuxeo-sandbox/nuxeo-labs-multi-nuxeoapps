package org.nuxeo.labs.multi.nuxeoapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.labs.multi.nuxeoapps.AbstractNuxeoApp.AuthenticationType;
import org.nuxeo.labs.multi.nuxeoapps.authentication.NuxeoAppAuthenticationJWT;
import org.nuxeo.labs.multi.nuxeoapps.service.MultiNuxeoAppService;
import org.nuxeo.labs.multi.nuxeoapps.service.MultiNuxeoAppServiceImpl;
import org.nuxeo.labs.multi.nuxeoapps.servlet.NuxeoAppServletUtils;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import javax.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("nuxeo-labs-multi-nuxeoapps-core")

/*
 * The misc MultiNuxeoApps_NNN.xml test contribs contributes different NuxeoApp and
 * assumes env. variables are set for the misc. values (URL, passwords, etc.)
 * If not set, the test is ignored.
 * Also, as we test remote app, if it's not available it's not considered as
 * a test failure.
 */
public class TestMultiNuxeoAppWithCustomConfig {

    protected static final List<String> APP_NAMES_BASIC = List.of("TEST_App1_BASIC", "TEST_App2_BASIC");

    protected static final List<String> APP_NAMES_JWT = List.of("TEST_App1_JWT", "TEST_App2_JWT");

    @Inject
    protected CoreSession session;

    @Inject
    protected MultiNuxeoAppService multiNuxeoAppService;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Before
    public void setupTest() {
        TestUtils.createDocs(session, transactionalFeature);
    }

    // These variables are used in the MultiNuxeoApps.xml ctest contribution
    protected static Boolean hasVariablesSet = null;

    protected boolean hasEnvVariablesSet() {

        // We just test some, assuming if they are set, the others are set too
        if (hasVariablesSet == null) {
            String v1 = System.getenv("TEST_MULTIAPPS_APP1_BASIC_URL");
            String v2 = System.getenv("TEST_MULTIAPPS_APP2_BASIC_URL");
            String v3 = System.getenv("TEST_MULTIAPPS_APP1_JWT_URL");

            hasVariablesSet = StringUtils.isNoneBlank(v1, v2, v3);
        }

        return hasVariablesSet;
    }

    protected boolean hasApps(JSONArray arr, String... appNames) {

        for (String oneName : appNames) {
            boolean found = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject documentsJson = arr.getJSONObject(i);
                JSONObject appInfo = documentsJson.getJSONObject(NuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
                if (oneName.equals(appInfo.getString("appName"))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        return true;
    }

    protected boolean atLeastOneAppAvailable(String... appNames) {

        for (String oneName : appNames) {
            NuxeoApp app = multiNuxeoAppService.getNuxeoApp(oneName);
            if (app.isServerAvailable()) {
                return true;
            }
        }
        return false;

    }

    protected JSONObject getResultsForApp(JSONArray arr, String appName) {

        for (int i = 0; i < arr.length(); i++) {
            JSONObject documentsJson = arr.getJSONObject(i);
            JSONObject appInfo = documentsJson.getJSONObject(NuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
            if (appName.equals(appInfo.getString("appName"))) {
                return documentsJson;
            }
        }

        return null;
    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_BASIC.xml")
    public void shouldHaveCustomConfigBASICDeployed() {

        JSONArray apps = multiNuxeoAppService.getNuxeoApps();
        assertNotNull(apps);
        
        // When called from other test, we wcould have deployed all apps, not just BASIC
        int foundCount = 0;
        for(int i = 0; i < apps.length(); i++) {
            JSONObject oneAppJson = apps.getJSONObject(i);
            String appName = oneAppJson.getString("appName");
            if(APP_NAMES_BASIC.indexOf(appName) > -1) {
                foundCount += 1;
            }
        }
        assertEquals(APP_NAMES_BASIC.size(), foundCount);

    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_JWT.xml")
    public void shouldHaveCustomConfigJWTDeployed() {

        JSONArray apps = multiNuxeoAppService.getNuxeoApps();
        assertNotNull(apps);
        
        // When called from other test, we wcould have deployed all apps, not just JWT
        int foundCount = 0;
        for(int i = 0; i < apps.length(); i++) {
            JSONObject oneAppJson = apps.getJSONObject(i);
            String appName = oneAppJson.getString("appName");
            if(APP_NAMES_JWT.indexOf(appName) > -1) {
                foundCount += 1;
            }
        }
        assertEquals(APP_NAMES_JWT.size(), foundCount);
    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_BASIC.xml")
    public void shouldHaveParamsInfo() {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigBASICDeployed();

        String nxql = "SELECT * FROM Domain";
        String fulltextValues = null;
        String enrichers = null;
        String properties = "uuid, common";
        int pageIndex = -1;
        int pageSize = 40;

        // Just test "TEST_App1_BASIC"
        JSONObject resultObj = multiNuxeoAppService.call("TEST_App1_BASIC", nxql, fulltextValues, enrichers, properties,
                pageIndex, pageSize);
        assertTrue(resultObj.has(MultiNuxeoAppServiceImpl.CALL_PARAMETERS_PROPERTY));

        JSONObject props = resultObj.getJSONObject(MultiNuxeoAppServiceImpl.CALL_PARAMETERS_PROPERTY);
        assertEquals(nxql, props.getString("nxql"));
        assertEquals(Utilities.NULL_VALUE_FOR_JSON, props.getString("fulltextSearchValues"));
        assertEquals(Utilities.NULL_VALUE_FOR_JSON, props.getString("enrichers"));
        assertEquals(properties, props.getString("properties"));
        assertEquals(pageIndex, props.getInt("pageIndex"));
        assertEquals(pageSize, props.getInt("pageSize"));

    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_BASIC.xml")
    public void shouldSearchOneApp() {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigBASICDeployed();

        String APP_TO_TEST = "TEST_App1_BASIC";

        Assume.assumeTrue("No distant Nuxeo app available => ignoring the test", atLeastOneAppAvailable(APP_TO_TEST));

        // (TEST_App1_BASIC defined in the xml contrib)
        JSONObject resultObj = multiNuxeoAppService.call(APP_TO_TEST, null, TestUtils.KEYWORD, null, null, 0, 0);
        // JSONObject resultObj = multiNuxeoAppService.call(APP_TO_TEST, "SELECT * FROM Picture", TestUtils.KEYWORD,
        // null, "file,thumbnail,picture", 0);
        assertNotNull(resultObj);

        JSONArray arr = resultObj.getJSONArray("results");
        assertTrue(arr.length() == 2); // Only one repo searched, + local

        assertTrue(hasApps(arr, APP_TO_TEST, NuxeoAppCurrent.getInstance().getAppName()));

        // Not testing tst server results, it likely failed because of "no fulltext index"
        JSONObject result = getResultsForApp(arr, APP_TO_TEST);
        assertNotNull(result);

        JSONObject appInfo = result.getJSONObject(NuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
        assertNotNull(appInfo);

        assertEquals("documents", result.getString("entity-type"));
    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_BASIC.xml")
    public void shouldSearchOnlyCurrent() {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigBASICDeployed();

        JSONObject resultObj = multiNuxeoAppService.call("not a valid app", "SELECT * FROM File", null, null, null, 0,
                0);
        assertNotNull(resultObj);

        JSONArray arr = resultObj.getJSONArray("results");
        assertTrue(arr.length() == 2);

        // First one is the remote apps info
        JSONObject result = arr.getJSONObject(0);
        JSONObject info = result.getJSONObject(AbstractNuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
        assertTrue(info.getBoolean("hasError"));

        // Then local one
        result = arr.getJSONObject(1);
        info = result.getJSONObject(AbstractNuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
        assertEquals(NuxeoAppCurrent.CURRENT_NUXEO_DEFAULT_APPNAME, info.getString("appName"));
        int resultCount = result.getInt("resultsCount");
        assertEquals(TestUtils.CREATE_DOCS_COUNT, resultCount);

    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_BASIC.xml")
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_JWT.xml")
    public void shoudSearchAllApps() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigBASICDeployed();
        shouldHaveCustomConfigJWTDeployed();

        String[] allNames = Stream.concat(APP_NAMES_BASIC.stream(), APP_NAMES_JWT.stream()).toArray(String[]::new);
        Assume.assumeTrue("No distant Nuxeo app available => ignoring the test", atLeastOneAppAvailable(allNames));

        JSONObject resultObj = multiNuxeoAppService.call("all", null, TestUtils.KEYWORD, "thumbnail", "dublincore,file",
                0, 0);
        assertNotNull(resultObj);

        File f = new File("/Users/thibaud.arguillere/Downloads/hop.json");
        org.apache.commons.io.FileUtils.writeStringToFile(f, resultObj.toString(2), Charset.defaultCharset(), false);

        JSONArray arr = resultObj.getJSONArray("results");

        assertTrue(arr.length() == APP_NAMES_BASIC.size() + APP_NAMES_JWT.size() + 1); // + local

    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_BASIC.xml")
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_JWT.xml")
    public void shoudSearchAllAppsWithPageSize() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigBASICDeployed();
        shouldHaveCustomConfigJWTDeployed();

        String[] allNames = Stream.concat(APP_NAMES_BASIC.stream(), APP_NAMES_JWT.stream()).toArray(String[]::new);
        Assume.assumeTrue("No distant Nuxeo app available => ignoring the test", atLeastOneAppAvailable(allNames));

        int PAGE_SIZE = 1;
        JSONObject resultObj = multiNuxeoAppService.call("all", "SELECT * FROM File", /* TestUtils.KEYWORD */ null,
                null, null, 0, PAGE_SIZE);
        assertNotNull(resultObj);

        JSONArray arr = resultObj.getJSONArray("results");

        assertTrue(arr.length() == APP_NAMES_BASIC.size() + APP_NAMES_JWT.size() + 1); // + local

        // Check result entries is max PAGE_SIZE
        for (int i = 0; i < arr.length(); i++) {
            JSONObject oneResult = arr.getJSONObject(i);
            JSONObject info = oneResult.getJSONObject(AbstractNuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
            if (!info.optBoolean("hasError", false)) {
                JSONArray entries = oneResult.getJSONArray("entries");
                assertTrue(entries.length() <= PAGE_SIZE);
            }
        }
    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_BASIC.xml")
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_JWT.xml")
    public void shoudSearchAllAppsWithEnrichersAndProperties() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigBASICDeployed();
        shouldHaveCustomConfigJWTDeployed();

        String[] allNames = Stream.concat(APP_NAMES_BASIC.stream(), APP_NAMES_JWT.stream()).toArray(String[]::new);
        Assume.assumeTrue("No distant Nuxeo app available => ignoring the test", atLeastOneAppAvailable(allNames));

        // Let's keep it short, 1 for pageSize
        int PAGE_SIZE = 1;
        JSONObject resultObj = multiNuxeoAppService.call("all", "SELECT * FROM File", /* TestUtils.KEYWORD */ null,
                "thumbnail", "dublincore", 0, PAGE_SIZE);
        assertNotNull(resultObj);

        JSONArray arr = resultObj.getJSONArray("results");

        assertTrue(arr.length() == APP_NAMES_BASIC.size() + APP_NAMES_JWT.size() + 1); // + local

        // Check result entries, and if one entry, it has the dc schema and the thumbnail
        for (int i = 0; i < arr.length(); i++) {
            JSONObject oneResult = arr.getJSONObject(i);
            JSONObject info = oneResult.getJSONObject(AbstractNuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
            if (!info.optBoolean("hasError", false)) {
                JSONArray entries = oneResult.getJSONArray("entries");
                assertTrue(entries.length() <= PAGE_SIZE);

                if (entries.length() > 0) {
                    JSONObject oneDoc = entries.getJSONObject(0);

                    // Dublincore
                    assertTrue(oneDoc.has("properties"));
                    JSONObject properties = oneDoc.getJSONObject("properties");
                    assertTrue(properties.has("dc:creator"));

                    // Thumbnail
                    assertTrue(oneDoc.has("contextParameters"));
                    JSONObject contextParameters = oneDoc.getJSONObject("contextParameters");
                    assertTrue(contextParameters.has("thumbnail"));
                }
            }
        }
    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_BASIC.xml")
    public void shouldHaveError() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigBASICDeployed();

        String[] allNames = APP_NAMES_BASIC.toArray(String[]::new);
        Assume.assumeTrue("No distant Nuxeo app available => ignoring the test", atLeastOneAppAvailable(allNames));

        // Wrong NXQL
        JSONObject resultObj = multiNuxeoAppService.call("all", "SELECT ***** FROM File", null, null, null, 0, 0);
        assertNotNull(resultObj);

        JSONArray arr = resultObj.getJSONArray("results");

        for (int i = 0; i < arr.length(); i++) {
            JSONObject oneResult = arr.getJSONObject(i);

            JSONObject info = oneResult.getJSONObject(AbstractNuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
            assertTrue(info.has("hasError"));
            assertTrue(info.getBoolean("hasError"));
        }

        // With full stack
        multiNuxeoAppService.tuneNuxeoApps(true);
        resultObj = multiNuxeoAppService.call("all", "SELECT ***** FROM File", null, null, null, 0, 0);
        assertNotNull(resultObj);

        arr = resultObj.getJSONArray("results");

    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_JWT.xml")
    public void testOneWithJWT() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigJWTDeployed();

        String APP_TO_TEST = "TEST_App2_JWT";
        Assume.assumeTrue("No distant Nuxeo app available => ignoring the test", atLeastOneAppAvailable(APP_TO_TEST));
        
        NuxeoApp nxApp = multiNuxeoAppService.getNuxeoApp(APP_TO_TEST);
        assertEquals(AuthenticationType.JWT, nxApp.getAuthenticationType());

        JSONObject resultObj = multiNuxeoAppService.call(APP_TO_TEST, null, TestUtils.KEYWORD, null, null, 0, 0);
        assertNotNull(resultObj);

        JSONArray arr = resultObj.getJSONArray("results");
        assertTrue(arr.length() == 2); // Only one repo searched, + local

        assertTrue(hasApps(arr, APP_TO_TEST, NuxeoAppCurrent.getInstance().getAppName()));

        // Not testing tst server results, it likely failed because of "no fulltext index"
        JSONObject result = getResultsForApp(arr, APP_TO_TEST);
        assertNotNull(result);

        JSONObject appInfo = result.getJSONObject(NuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
        assertNotNull(appInfo);

        assertEquals("documents", result.getString("entity-type"));

    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_JWT.xml")
    public void shouldReuseJWTToken() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigJWTDeployed();

        String APP_TO_TEST = "TEST_App2_JWT";
        Assume.assumeTrue("No distant Nuxeo app available => ignoring the test", atLeastOneAppAvailable(APP_TO_TEST));
        
        NuxeoApp nxApp = multiNuxeoAppService.getNuxeoApp(APP_TO_TEST);
        assertEquals(nxApp.getAuthenticationType(), AuthenticationType.JWT);

        // First call => first authentication
        JSONObject resultObj = multiNuxeoAppService.call(APP_TO_TEST, null, TestUtils.KEYWORD, null, null, 0, 0);
        assertNotNull(resultObj);

        // We should have a non expired token that will be reused.
        NuxeoAppAuthenticationJWT nxApAuth = (NuxeoAppAuthenticationJWT) nxApp.getNuxeoAppAuthentication();
        String token = nxApAuth.getToken(null);
        assertNotNull(token);

        Thread.sleep(5000);
        token = nxApAuth.getToken(null);
        assertNotNull(token);

    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps_BASIC.xml")
    public void shouldGetRemoteBlob() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigBASICDeployed();

        String APP_TO_TEST = "TEST_App2_BASIC";
        Assume.assumeTrue("No distant Nuxeo app available => ignoring the test", atLeastOneAppAvailable(APP_TO_TEST));

        /*
         * NuxeoApp app = multiNuxeoAppService.getNuxeoApp("TEST_App1_BASIC");
         * assertNotNull(app);
         * Blob b = app.getBlob(
         * "/nxfile/default/d14837dc-bfcd-41bb-8886-ae1b8bfbc17c/file:content/NYCHighLine.jpg?changeToken=3-0");
         */
        // Test should have redirect. In our settngs, TEST_App2_BASIC is an app on AWS with direct download
        // Cmment this if it's not your case
        NuxeoApp app = multiNuxeoAppService.getNuxeoApp(APP_TO_TEST);
        assertNotNull(app);

        // Get redirect info
        String BLOB_URL = System.getenv("TEST_MULTIAPPS_APP2_REMOTE_BLOB_URL");
        String BLOB_FILENAME = System.getenv("TEST_MULTIAPPS_APP2_REMOTE_BLOB_FILENAME");
        Blob b = app.getBlob(BLOB_URL, true);
        assertNotNull(b);
        assertTrue(b instanceof JSONBlob);
        JSONObject redirectInfoJson = new JSONObject(b.getString());
        assertTrue(redirectInfoJson.has("status"));
        assertTrue(redirectInfoJson.has("location"));
        int status = redirectInfoJson.getInt("status");
        assertTrue(NuxeoAppServletUtils.isRedirect(status));

        String location = redirectInfoJson.getString("location");
        assertTrue(StringUtils.isNotBlank(location));

        // Get the file
        b = app.getBlob(BLOB_URL, false);
        assertEquals(BLOB_FILENAME, b.getFilename());

    }

}
