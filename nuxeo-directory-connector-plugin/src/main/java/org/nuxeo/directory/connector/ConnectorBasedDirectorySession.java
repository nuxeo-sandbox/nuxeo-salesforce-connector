package org.nuxeo.directory.connector;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.directory.BaseSession;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Reference;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.runtime.api.Framework;

/**
 * Session for Directories based on a contributed connector
 *
 * @author tiry
 *
 */
public class ConnectorBasedDirectorySession extends BaseSession implements
        Session {

    protected final ConnectorBasedDirectory directory;

    protected EntryConnector connector;

    public ConnectorBasedDirectorySession(ConnectorBasedDirectory directory,
            EntryConnector connector) {
        this.directory = directory;
        this.connector = connector;
    }

    public boolean authenticate(String username, String password)
            throws DirectoryException {
        return connector.authenticate(username, password);
    }

    public void close() {
        if (connector!=null) {
            connector.close();
        }
    }

    public void commit() {
        if (connector!=null) {
            connector.commit();
        }
    }

    public void rollback() throws DirectoryException {
        if (connector!=null) {
            connector.rollback();
        }
    }

    public DocumentModel createEntry(Map<String, Object> fieldMap)
            throws DirectoryException {
        throw new IllegalAccessError("Connector Directory is read only");
    }

    public DocumentModel getEntry(String id) throws DirectoryException {
        return getEntry(id, true);
    }

    protected Map<String, Object> translate(Map<String, Object> map) {
        Schema schema = Framework.getLocalService(SchemaManager.class).getSchema(directory.getSchema());
        Map<String, Object> newMap = new HashMap<>();

        Map<String, String> mapping = directory.descriptor.getMapping();
        for (Field field : schema.getFields()) {
            String fieldId = field.getName().getLocalName();
            if (mapping.containsKey(fieldId)) {
                newMap.put(fieldId, field.getType().encode(map.get(mapping.get(fieldId))));
            } else {
                newMap.put(fieldId, map.get(fieldId));
            }
        }
        return newMap;
    }

    public DocumentModel getEntry(String id, boolean fetchReferences)
            throws DirectoryException {
        // XXX no references here

        Map<String, Object> map = connector.getEntryMap(id);
        if (map == null) {
            return null;
        }

        // manage translation
        map = translate(map);

        try {
            DocumentModel entry = BaseSession.createEntryModel(null,
                    directory.schemaName, id, map);

            if (fetchReferences) {
                for (Reference reference : directory.getReferences()) {
                    List<String> targetIds = reference.getTargetIdsForSource(entry.getId());
                    try {
                        entry.setProperty(directory.schemaName,
                                reference.getFieldName(), targetIds);
                    } catch (NuxeoException e) {
                        throw new DirectoryException(e);
                    }
                }
            }
            return entry;
        } catch (PropertyException e) {
            throw new DirectoryException(e);
        }
    }

    public void updateEntry(DocumentModel docModel) throws DirectoryException {
        throw new IllegalAccessError("Connector Directory is read only");
    }

    public DocumentModelList getEntries() throws DirectoryException {
        DocumentModelList list = new DocumentModelListImpl();
        for (String id : connector.getEntryIds()) {
            list.add(getEntry(id));
        }
        return list;
    }

    public void deleteEntry(String id) throws DirectoryException {
        throw new IllegalAccessError("Connector Directory is read only");
    }

    // given our storage model this doesn't even make sense, as id field is
    // unique
    public void deleteEntry(String id, Map<String, String> map)
            throws DirectoryException {
        throw new DirectoryException("Not implemented");
    }

    public void deleteEntry(DocumentModel docModel) throws DirectoryException {
        deleteEntry(docModel.getId());
    }

    public String getIdField() {
        return directory.idField;
    }

    public String getPasswordField() {
        return directory.passwordField;
    }

    public boolean isAuthenticating() {
        return directory.passwordField != null;
    }

    public boolean isReadOnly() {
        return true;
    }

    public DocumentModelList query(Map<String, Serializable> filter)
            throws DirectoryException {
        return query(filter, connector.getFullTextConfig());
    }

    public DocumentModelList query(Map<String, Serializable> filter,
            Set<String> fulltext) throws DirectoryException {
        return query(filter, fulltext, Collections.<String, String> emptyMap());
    }

    public DocumentModelList query(Map<String, Serializable> filter,
            Set<String> fulltext, Map<String, String> orderBy)
            throws DirectoryException {
        return query(filter, fulltext, orderBy, true);
    }

    public DocumentModelList query(Map<String, Serializable> filter,
            Set<String> fulltext, Map<String, String> orderBy,
            boolean fetchReferences) throws DirectoryException {
        DocumentModelList results = new DocumentModelListImpl();
        // canonicalize filter
        Map<String, Serializable> filt = new HashMap<String, Serializable>();
        for (Entry<String, Serializable> e : filter.entrySet()) {
            String fieldName = e.getKey();
            if (!directory.schemaSet.contains(fieldName)) {
                continue;
            }
            filt.put(fieldName, e.getValue());
        }

        List<String> ids = connector.queryEntryIds(filt, fulltext);
        if (ids != null) {
            for (String id : ids) {
                results.add(getEntry(id));
            }
        }

        // order entries
        if (orderBy != null && !orderBy.isEmpty()) {
            directory.orderEntries(results, orderBy);
        }
        return results;
    }

    public List<String> getProjection(Map<String, Serializable> filter,
            String columnName) throws DirectoryException {
        return getProjection(filter, Collections.<String> emptySet(),
                columnName);
    }

    public List<String> getProjection(Map<String, Serializable> filter,
            Set<String> fulltext, String columnName) throws DirectoryException {
        DocumentModelList l = query(filter, fulltext);
        List<String> results = new ArrayList<String>(l.size());
        for (DocumentModel doc : l) {
            Object value;
            try {
                value = doc.getProperty(directory.schemaName, columnName);
            } catch (NuxeoException e) {
                throw new DirectoryException(e);
            }
            if (value != null) {
                results.add(value.toString());
            } else {
                results.add(null);
            }
        }
        return results;
    }

    public DocumentModel createEntry(DocumentModel entry)
            throws NuxeoException {
        Map<String, Object> fieldMap = entry.getProperties(directory.schemaName);
        return createEntry(fieldMap);
    }

    public boolean hasEntry(String id) throws NuxeoException {
        return connector.hasEntry(id);
    }

}
