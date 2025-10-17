package org.nuxeo.labs.multi.nuxeoapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import javax.inject.Inject;

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
import org.nuxeo.labs.multi.nuxeoapps.service.MultiNuxeoAppService;
import org.nuxeo.labs.multi.nuxeoapps.service.MultiNuxeoAppServiceImpl;
import org.nuxeo.labs.multi.nuxeoapps.servlet.NuxeoAppServletUtils;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("nuxeo-labs-multi-nuxeoapps-core")

/*
 * The MultiNuxeoApps.xml test contrib is deployed in some tests.
 * It contributes different NuxeoApp and assumes env. variables are set for
 * the misc. values (URL, passwords, etc. If not set, the test is ignored.
 */
@Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps.xml")
public class TestMultiNuxeoAppWithCustomConfig {

    protected static final List<String> APP_NAMES = List.of("TEST_AppBASIC1", "TEST_AppBASIC2", "TEST_AppJWT");

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
            String v1 = System.getenv("TEST_MULTIAPPS_APP_BASIC1_URL");
            String v2 = System.getenv("TEST_MULTIAPPS_APP_BASIC2_URL");
            String v3 = System.getenv("TEST_MULTIAPPS_APP_JWT_URL");

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
    public void shouldHaveCustomConfigDeployed() {

        JSONArray apps = multiNuxeoAppService.getNuxeoApps();
        assertNotNull(apps);
        assertEquals(3, apps.length());

        for (int i = 0; i < apps.length(); i++) {
            JSONObject oneAppJson = apps.getJSONObject(i);
            assertNotNull(oneAppJson);

            assertTrue(APP_NAMES.indexOf(oneAppJson.getString("appName")) > -1);
        }

    }
    
    @Test
    public void shouldHaveParamsInfo() {
        
        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigDeployed();
        
        String nxql = "SELECT * FROM Domain";
        String fulltextValues = null;
        String enrichers = null;
        String properties = "uuid, common";
        int pageIndex = -1;
        int pageSize = 40;
        
        // Just test "TEST_AppBASIC1"
        JSONObject resultObj = multiNuxeoAppService.call("TEST_AppBASIC1", nxql, fulltextValues, enrichers, properties, pageIndex, pageSize);
        assertTrue(resultObj.has(MultiNuxeoAppServiceImpl.CALL_PARAMETERS_PROPERTY));
        
        JSONObject props = resultObj.getJSONObject(MultiNuxeoAppServiceImpl.CALL_PARAMETERS_PROPERTY);
        assertEquals(nxql, props.getString("nxql"));
        assertEquals(MultiNuxeoAppServiceImpl.NULL_VALUE_FOR_JSON, props.getString("fulltextSearchValues"));
        assertEquals(MultiNuxeoAppServiceImpl.NULL_VALUE_FOR_JSON, props.getString("enrichers"));
        assertEquals(properties, props.getString("properties"));
        assertEquals(pageIndex, props.getInt("pageIndex"));
        assertEquals(pageSize, props.getInt("pageSize"));

    }

    @Test
    public void shouldSearchOneApp() {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigDeployed();

        // (TEST_AppBASIC1 defined in the xml contrib)
        JSONObject resultObj = multiNuxeoAppService.call("TEST_AppBASIC1", null, TestUtils.KEYWORD, null, null, 0, 0);
        //JSONObject resultObj = multiNuxeoAppService.call("TEST_AppBASIC2", "SELECT * FROM Picture", TestUtils.KEYWORD, null, "file,thumbnail,picture", 0);
        assertNotNull(resultObj);
        
        JSONArray arr = resultObj.getJSONArray("results");
        assertTrue(arr.length() == 2); // Only one repo searched, + local

        assertTrue(hasApps(arr, "TEST_AppBASIC1", NuxeoAppCurrent.getInstance().getAppName()));

        // Not testing tst server results, it likely failed because of "no fulltext index"
        JSONObject result = getResultsForApp(arr, "TEST_AppBASIC1");
        assertNotNull(result);

        JSONObject appInfo = result.getJSONObject(NuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
        assertNotNull(appInfo);

        assertEquals("documents", result.getString("entity-type"));
    }
    
    @Test
    public void shouldSearchOnlyCurrent() {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigDeployed();
        
        JSONObject resultObj = multiNuxeoAppService.call("not a valid app", "SELECT * FROM File", null, null, null, 0, 0);
        //JSONObject resultObj = multiNuxeoAppService.call("TEST_AppBASIC2", "SELECT * FROM Picture", TestUtils.KEYWORD, null, "file,thumbnail,picture", 0);
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
    public void shoudSearchAllApps() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigDeployed();

        JSONObject resultObj = multiNuxeoAppService.call("all", null, TestUtils.KEYWORD, "thumbnail", "dublincore,file", 0, 0);
        assertNotNull(resultObj);
        
        File f = new File("/Users/thibaud.arguillere/Downloads/hop.json");
        org.apache.commons.io.FileUtils.writeStringToFile(f, resultObj.toString(2), Charset.defaultCharset(), false);

        JSONArray arr = resultObj.getJSONArray("results");
        
        String currentAppNamme = NuxeoAppCurrent.getInstance().getAppName();
        String[] array = Stream.concat(APP_NAMES.stream(), Stream.of(currentAppNamme)).toArray(String[]::new);
        assertTrue(hasApps(arr, array));
        assertTrue(arr.length() == APP_NAMES.size() + 1); // + local

    }

    @Test
    public void shoudSearchAllAppsWithPageSize() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigDeployed();

        int PAGE_SIZE = 1;
        JSONObject resultObj = multiNuxeoAppService.call("all", "SELECT * FROM File", /*TestUtils.KEYWORD*/ null, null, null, 0, PAGE_SIZE);
        assertNotNull(resultObj);

        JSONArray arr = resultObj.getJSONArray("results");

        String currentAppNamme = NuxeoAppCurrent.getInstance().getAppName();
        String[] array = Stream.concat(APP_NAMES.stream(), Stream.of(currentAppNamme)).toArray(String[]::new);
        assertTrue(hasApps(arr, array));
        assertTrue(arr.length() == APP_NAMES.size() + 1); // + local
        
        // Check result entries is max PAGE_SIZE
        for(int i = 0; i < arr.length(); i++) {
            JSONObject oneResult = arr.getJSONObject(i);
            JSONObject info = oneResult.getJSONObject(AbstractNuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
            if(!info.optBoolean("hasError", false)) {
                JSONArray entries = oneResult.getJSONArray("entries");
                assertTrue(entries.length() <= PAGE_SIZE);
            }
        }
    }
    
    @Test
    public void shouldHaveError() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigDeployed();
        
        // Wrong NXQL
        JSONObject resultObj = multiNuxeoAppService.call("all", "SELECT ***** FROM File", null, null, null, 0, 0);
        assertNotNull(resultObj);

        JSONArray arr = resultObj.getJSONArray("results");
        
        for(int i = 0; i < arr.length(); i++) {
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
    public void testOneWithJWT() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigDeployed();

        JSONObject resultObj = multiNuxeoAppService.call("TEST_AppJWT", null, TestUtils.KEYWORD, null, null, 0, 0);
        assertNotNull(resultObj);

        JSONArray arr = resultObj.getJSONArray("results");
        assertTrue(arr.length() == 2); // Only one repo searched, + local

        assertTrue(hasApps(arr, "TEST_AppJWT", NuxeoAppCurrent.getInstance().getAppName()));
        
        // Not testing tst server results, it likely failed because of "no fulltext index"
        JSONObject result = getResultsForApp(arr, "TEST_AppJWT");
        assertNotNull(result);

        JSONObject appInfo = result.getJSONObject(NuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
        assertNotNull(appInfo);

        assertEquals("documents", result.getString("entity-type"));

    }
    
    @Test
    public void shouldGetRemoteBlob() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        shouldHaveCustomConfigDeployed();
        
        /*
        NuxeoApp app = multiNuxeoAppService.getNuxeoApp("TEST_AppBASIC1");
        assertNotNull(app);
        Blob b = app.getBlob("/nxfile/default/d14837dc-bfcd-41bb-8886-ae1b8bfbc17c/file:content/NYCHighLine.jpg?changeToken=3-0");
        */
        // Test should have redirect. In our settngs, TEST_AppBASIC2 is an app on AWS with direct download
        // Cmment this if it's not your case
        NuxeoApp app = multiNuxeoAppService.getNuxeoApp("TEST_AppBASIC2");
        assertNotNull(app);
        
        // Get redirect info
        Blob b = app.getBlob("/nxfile/default/b5af5424-7080-4779-abdd-b3fdcb8d8eb6/thumb:thumbnail/181002121444-file-exlarge-169_small.jpeg?changeToken=25-0", true);
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
        b = app.getBlob("/nxfile/default/b5af5424-7080-4779-abdd-b3fdcb8d8eb6/thumb:thumbnail/181002121444-file-exlarge-169_small.jpeg?changeToken=25-0", false);
        assertEquals("181002121444-file-exlarge-169_small.jpeg", b.getFilename());
        
    }

}
