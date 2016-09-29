/*
 * (C) Copyright 2006-2007 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 *
 * $Id: MemoryDirectory.java 30381 2008-02-20 20:12:09Z gracinet $
 */

package org.nuxeo.directory.connector;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.directory.AbstractDirectory;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.runtime.api.Framework;

/**
 * @author tiry
 */
public class ConnectorBasedDirectory extends AbstractDirectory {

    public final Set<String> schemaSet;

    public Map<String, Object> map;

    public ConnectorBasedDirectorySession session;

    public ConnectorBasedDirectory(ConnectorBasedDirectoryDescriptor descriptor) throws DirectoryException {
        super(descriptor);
        this.schemaSet = new HashSet<String>();
        SchemaManager sm = Framework.getLocalService(SchemaManager.class);
        Schema sch = sm.getSchema(descriptor.schemaName);
        if (sch == null) {
            throw new DirectoryException("Unknown schema : " + descriptor.schemaName);
        }
        Collection<Field> fields = sch.getFields();
        for (Field f : fields) {
            schemaSet.add(f.getName().getLocalName());
        }
        addReferences(descriptor.getInverseReferences());
    }

    @Override
    public ConnectorBasedDirectoryDescriptor getDescriptor() {
        return (ConnectorBasedDirectoryDescriptor) descriptor;
    }

    @Override
    public Session getSession() {
        if (session == null) {
            session = new ConnectorBasedDirectorySession(this, getDescriptor().getConnector());
        }
        return session;
    }

    @Override
    public void shutdown() {
        if (session != null) {
            session.close();
        }
        session = null;
    }

}
