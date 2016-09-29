package org.nuxeo.directory.connector.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.directory.Directory;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.NXRuntimeTestCase;

public class TestConnectorDirectory extends NXRuntimeTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        deployBundle("org.nuxeo.ecm.core.api");
        deployBundle("org.nuxeo.ecm.core.schema");
        deployBundle("org.nuxeo.ecm.core");
        deployBundle("org.nuxeo.ecm.directory.api");
        deployBundle("org.nuxeo.ecm.directory");
        deployBundle("org.nuxeo.ecm.directory.types.contrib");
        deployContrib("org.nuxeo.directory.connector",
                "OSGI-INF/connectorbased-directory-framework.xml");
    }

    @Test
    public void testContrib() throws Exception {

        deployContrib("org.nuxeo.directory.connector.test",
                "OSGI-INF/testContrib.xml");

        DirectoryService ds = Framework.getLocalService(DirectoryService.class);
        assertNotNull(ds);

        List<String> dsNames = ds.getDirectoryNames();

        assertTrue(dsNames.contains("testConnector"));

        Directory d = ds.getDirectory("testConnector");

        Session session = d.getSession();

        assertNotNull(session);

        DocumentModelList entries = session.getEntries();
        assertEquals(2, entries.totalSize());

        DocumentModel entry = session.getEntry("toto");
        assertNotNull(entry);
        assertEquals("Toto", (String) entry.getProperty("user", "firstName"));

        entry = session.getEntry("toti");
        assertNull(entry);

        boolean auth = session.authenticate("toto", "password");
        assertTrue(auth);
        auth = session.authenticate("toto", "whatever");
        assertFalse(auth);
        auth = session.authenticate("toti", "whatever");
        assertFalse(auth);

        Map<String, Serializable> filter = new HashMap<String, Serializable>();
        filter.put("username", "to");
        entries = session.query(filter);
        assertNotNull(entries);
        assertEquals(1, entries.totalSize());

        filter.put("username", "t");
        entries = session.query(filter);
        assertNotNull(entries);
        assertEquals(2, entries.totalSize());

        filter.put("username", "x");
        entries = session.query(filter);
        assertNotNull(entries);
        assertEquals(0, entries.totalSize());
    }

    @Test
    public void testJsonDirectoryConnectorContrib() throws Exception {

        /*deployContrib("org.nuxeo.directory.connector.test",
                "OSGI-INF/testJsonDirectoryConnectorContrib.xml");
        DirectoryService ds = Framework.getLocalService(DirectoryService.class);
        assertNotNull(ds);

        List<String> dsNames = ds.getDirectoryNames();
        assertTrue(dsNames.contains("jsonDirectoryConnector"));

        Directory d = ds.getDirectory("jsonDirectoryConnector");

        Session session = d.getSession();
        assertNotNull(session);

        DocumentModelList entries = session.getEntries();
        assertNotNull(entries);
        assertEquals(50, entries.totalSize());

        DocumentModel entry = session.getEntry("358317744");
        assertNotNull(entry);
        assertEquals("The Sea", (String) entry.getProperty("itunes", "trackName"));*/

    }


    @Test
    public void testNasaDirectoryConnectorContrib() throws Exception {

        /*deployContrib("org.nuxeo.directory.connector.test",
                "OSGI-INF/testJsonDirectoryConnectorContrib.xml");
        DirectoryService ds = Framework.getLocalService(DirectoryService.class);
        assertNotNull(ds);

        List<String> dsNames = ds.getDirectoryNames();
        assertTrue(dsNames.contains("nasaCategories"));

        Directory d = ds.getDirectory("nasaCategories");

        Session session = d.getSession();
        assertNotNull(session);

        DocumentModelList entries = session.getEntries();
        assertNotNull(entries);
        assertEquals(9, entries.totalSize());

        DocumentModel entry = session.getEntry("178");
        assertNotNull(entry);
        assertEquals("Aeronautics", (String) entry.getProperty("vocabulary", "label"));*/

    }


    @Test
    public void testNasaDSDirectoryConnectorContrib() throws Exception {

        /*deployContrib("org.nuxeo.directory.connector.test",
                "OSGI-INF/testJsonDirectoryConnectorContrib.xml");
        DirectoryService ds = Framework.getLocalService(DirectoryService.class);
        assertNotNull(ds);

        List<String> dsNames = ds.getDirectoryNames();
        assertTrue(dsNames.contains("nasaDataSets"));

        Directory d = ds.getDirectory("nasaDataSets");

        Session session = d.getSession();
        assertNotNull(session);


        Map<String, Serializable> filter = new HashMap<>();
        filter.put("category", "322");

        DocumentModelList entries = session.query(filter);
        assertNotNull(entries);
        assertEquals(10, entries.totalSize());


        DocumentModel entry = session.getEntry("707");
        assertNotNull(entry);
        assertEquals("lunar-sample-atlas", (String) entry.getProperty("nasads", "slug"));*/

    }

}
