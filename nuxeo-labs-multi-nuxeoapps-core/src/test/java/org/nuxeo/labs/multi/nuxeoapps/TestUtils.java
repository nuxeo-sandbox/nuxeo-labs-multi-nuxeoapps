package org.nuxeo.labs.multi.nuxeoapps;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

public class TestUtils {

    public static final String KEYWORD = "nuxeo";

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

    public static void createDocs(CoreSession session) {

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
        
        TransactionalFeature transactionalFeature = Framework.getService(TransactionalFeature.class);
        transactionalFeature.nextTransaction();

    }

}
