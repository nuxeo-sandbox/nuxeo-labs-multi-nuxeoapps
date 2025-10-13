package org.nuxeo.labs.multi.nuxeoapps;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("nuxeo-labs-multi-nuxeoapps-core")
public class TestMultiNuxeoApp {

    @Inject
    protected MultiNuxeoAppService multiNuxeoAppService;

    @Test
    public void testService() {
        assertNotNull(multiNuxeoAppService);
    }

    @Test
    public void shouldHaveNoConfig() {

        JSONArray apps = multiNuxeoAppService.getNuxeoApps();

        assertTrue(apps == null || apps.length() == 0);
    }

}
