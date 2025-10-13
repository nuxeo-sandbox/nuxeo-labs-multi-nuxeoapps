package org.nuxeo.labs.multi.nuxeoapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.ThreadLocalRandom;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/* The MultiNuxeoApps.xml test contrib is deployed in some tests.
 * It contributes different NuxeoApp and assumes env. variables are set for
 * the misc. values (URL, passwords, etc. If not set, the test is ignored.
 * 
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("nuxeo-labs-multi-nuxeoapps-core")
public class TestMultiNuxeoApp {

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Inject
    protected MultiNuxeoAppService multiNuxeoAppService;

    protected static final String KEYWORD = "nuxeo";

    // These variables are used in the MultiNuxeoApps.xml ctest contribution
    protected static Boolean hasVariablesSet = null;

    protected boolean hasEnvVariablesSet() {

        if (hasVariablesSet == null) {
            String v1 = System.getenv("TEST_MULTIAPPS_APP_BASIC1_URL");
            String v2 = System.getenv("TEST_MULTIAPPS_APP_BASIC2_URL");

            hasVariablesSet = StringUtils.isNoneBlank(v1, v2);
        }

        // We just set some, assumng if they are set, the others are set too
        return hasVariablesSet;
    }

    protected void createDocs() {

        int countWithValue = 0;

        for (int i = 1; i < 11; i++) {
            DocumentModel doc = session.createDocumentModel("/", "file-" + i, "File");
            doc.setPropertyValue("dc:title", "file-" + i);
            int random = ThreadLocalRandom.current().nextInt(1, 11);
            if (random > 7 || (i == 10 && countWithValue == 0)) {
                countWithValue += 1;
                doc.setPropertyValue("dc:description", KEYWORD);
            }
            doc = session.createDocument(doc);
        }

        session.save();
        transactionalFeature.nextTransaction();

    }

    @Test
    public void testService() {
        assertNotNull(multiNuxeoAppService);
    }

    @Test
    public void shouldHaveNoConfig() {

        JSONArray apps = multiNuxeoAppService.getNuxeoApps();

        assertTrue(apps == null || apps.length() == 0);
    }

    protected void checkLocalDeploy() {

        JSONArray apps = multiNuxeoAppService.getNuxeoApps();
        assertNotNull(apps);
        assertEquals(3, apps.length());

    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps.xml")
    public void shouldSearchOneApp() {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        checkLocalDeploy();

        // (TEST_AppBASIC1 defined in the xml contrib)
        JSONArray arr = multiNuxeoAppService.call("TEST_AppBASIC1", null, KEYWORD, null, null, 0);
        assertNotNull(arr);
        assertTrue(arr.length() == 1); // Only one repo searched

        JSONObject result = arr.getJSONObject(0);
        assertEquals("TEST_AppBASIC1", result.getString("appName"));
        assertEquals("documents", result.getString("entity-type"));
    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps.xml")
    public void shoudSearchAllApps() throws Exception {

        Assume.assumeTrue("No test env. variables set => ignoring the test", hasEnvVariablesSet());

        String v1 = System.getenv("TEST_MULTIAPPS_APP_JWT_URL");
        Assume.assumeTrue("No test env. variables set => ignoring the test", StringUtils.isNotBlank(v1));

        checkLocalDeploy();

        JSONArray arr = multiNuxeoAppService.call("all", null, KEYWORD, null, null, 0);

        assertNotNull(arr);
        assertTrue(arr.length() == 3);

    }

    @Test
    @Deploy("nuxeo-labs-multi-nuxeoapps-core:MultiNuxeoApps.xml")
    public void testOneWithJWT() throws Exception {

        String v1 = System.getenv("TEST_MULTIAPPS_APP_JWT_URL");
        Assume.assumeTrue("No test env. variables set => ignoring the test", StringUtils.isNotBlank(v1));

        checkLocalDeploy();

        JSONArray arr = multiNuxeoAppService.call("TEST_AppJWT", null, KEYWORD, null, null, 0);
        assertNotNull(arr);
        assertTrue(arr.length() == 1); // Only one repo searched

        JSONObject result = arr.getJSONObject(0);
        assertEquals("documents", result.getString("entity-type"));
        JSONObject appInfo = result.getJSONObject(NuxeoApp.MULTI_NUXEO_APPS_PROPERTY_NAME);
        assertEquals("TEST_AppJWT", appInfo.getString("appName"));

    }

}
