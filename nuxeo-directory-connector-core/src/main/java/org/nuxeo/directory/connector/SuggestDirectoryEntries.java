package org.nuxeo.directory.connector;

/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 *     <a href="mailto:grenard@nuxeo.com">Guillaume</a>
 */

import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.i18n.I18NUtils;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.QName;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.directory.Directory;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.ui.select2.common.Select2Common;

/**
 * SuggestDirectoryEntries Operation
 *
 * @since 5.7.3
 */
@Operation(id = SuggestDirectoryEntries.ID, category = Constants.CAT_SERVICES, label = "Get directory entries", description = "Get the entries of a directory. This is returning a blob containing a serialized JSON array. The input document, if specified, is used as a context for a potential local configuration of the directory.", addToStudio = false)
public class SuggestDirectoryEntries {

    /**
     * @since 5.9.3
     */
    Collator collator;

    /**
     * Convenient class to build JSON serialization of results.
     *
     * @since 5.7.2
     */
    private class JSONAdapter implements Comparable<JSONAdapter> {

        private final Map<String, JSONAdapter> children;

        private final Session session;

        private final Schema schema;

        private boolean isRoot = false;

        private Boolean isLeaf = null;

        private JSONObject obj;

        public JSONAdapter(Session session, Schema schema) {
            this.session = session;
            this.schema = schema;
            children = new HashMap<String, JSONAdapter>();
            // We are the root node
            this.isRoot = true;
        }

        public JSONAdapter(Session session, Schema schema, DocumentModel entry) throws PropertyException {
            this(session, schema);
            // Carry entry, not root
            isRoot = false;
            // build JSON object for this entry
            obj = new JSONObject();
            for (Field field : schema.getFields()) {
                QName fieldName = field.getName();
                String key = fieldName.getLocalName();
                Serializable value = entry.getPropertyValue(fieldName.getPrefixedName());
                if (label.equals(key)) {
                    if (localize && !dbl10n) {
                        // translations are in messages*.properties files
                        value = translate(value.toString());
                    }
                    obj.element(Select2Common.LABEL, value);
                }
                obj.element(key, value);

            }
            if (displayObsoleteEntries) {
                if (obj.containsKey(Select2Common.OBSOLETE_FIELD_ID) && obj.getInt(Select2Common.OBSOLETE_FIELD_ID) > 0) {
                    obj.element(Select2Common.WARN_MESSAGE_LABEL, getObsoleteWarningMessage());
                }
            }
        }

        @Override
        public int compareTo(JSONAdapter other) {
            if (other != null) {
                int i = this.getOrder() - other.getOrder();
                if (i != 0) {
                    return i;
                } else {
                    return getCollator().compare(this.getLabel(), other.getLabel());
                }
            } else {
                return -1;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            JSONAdapter other = (JSONAdapter) obj;
            if (!getOuterType().equals(other.getOuterType())) {
                return false;
            }
            if (this.obj == null) {
                if (other.obj != null) {
                    return false;
                }
            } else if (!this.obj.equals(other.obj)) {
                return false;
            }
            return true;
        }

        public JSONArray getChildrenJSONArray() {
            JSONArray result = new JSONArray();
            for (JSONAdapter ja : getSortedChildren()) {
                // When serializing in JSON, we are now able to COMPUTED_ID
                // which is the chained path of the entry (i.e absolute path
                // considering its ancestor)
                ja.getObj().element(Select2Common.COMPUTED_ID,
                        (!isRoot ? (getComputedId() + keySeparator) : "") + ja.getId());
                ja.getObj().element(Select2Common.ABSOLUTE_LABEL,
                        (!isRoot ? (getAbsoluteLabel() + absoluteLabelSeparator) : "") + ja.getLabel());
                result.add(ja.toJSONObject());
            }
            return result;
        }

        public String getComputedId() {
            return isRoot ? null : obj.optString(Select2Common.COMPUTED_ID);
        }

        public String getId() {
            return isRoot ? null : obj.optString(Select2Common.ID);
        }

        public String getLabel() {
            return isRoot ? null : obj.optString(Select2Common.LABEL);
        }

        public String getAbsoluteLabel() {
            return isRoot ? null : obj.optString(Select2Common.ABSOLUTE_LABEL);
        }

        public JSONObject getObj() {
            return obj;
        }

        public int getOrder() {
            return isRoot ? -1 : obj.optInt(Select2Common.DIRECTORY_ORDER_FIELD_NAME);
        }

        private SuggestDirectoryEntries getOuterType() {
            return SuggestDirectoryEntries.this;
        }

        public String getParentId() {
            return isRoot ? null : obj.optString(Select2Common.PARENT_FIELD_ID);
        }

        public List<JSONAdapter> getSortedChildren() {
            if (children == null) {
                return null;
            }
            List<JSONAdapter> result = new ArrayList<JSONAdapter>(children.values());
            Collections.sort(result);
            return result;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((obj == null) ? 0 : obj.hashCode());
            return result;
        }

        /**
         * Does the associated vocabulary / directory entry have child entries.
         *
         * @return true if it has children
         * @since 5.7.2
         */
        public boolean isLeaf() {
            if (isLeaf == null) {
                if (isChained) {
                    String id = getId();
                    if (id != null) {
                        Map<String, Serializable> filter = new HashMap<String, Serializable>();
                        filter.put(Select2Common.PARENT_FIELD_ID, getId());
                        try {
                            isLeaf = session.query(filter, Collections.emptySet(),  new HashMap<String, String>(), false, 1, -1).isEmpty();
                        } catch (DirectoryException ce) {
                            log.error("Could not retrieve children of entry", ce);
                            isLeaf = true;
                        }
                    } else {
                        isLeaf = true;
                    }
                } else {
                    isLeaf = true;
                }
            }
            return isLeaf;
        }

        public boolean isObsolete() {
            return isRoot ? false : obj.optInt(Select2Common.OBSOLETE_FIELD_ID) > 0;
        }

        private void mergeJsonAdapter(JSONAdapter branch) {
            JSONAdapter found = children.get(branch.getLabel());
            if (found != null) {
                // I already have the given the adapter as child, let's merge
                // all its children.
                for (JSONAdapter branchChild : branch.children.values()) {
                    found.mergeJsonAdapter(branchChild);
                }
            } else {
                // First time I see this adapter, I adopt it.
                // We use label as key, this way display will be alphabetically
                // sorted
                children.put(branch.getLabel(), branch);
            }
        }

        public JSONAdapter push(final JSONAdapter newEntry) throws PropertyException {
            String parentIdOfNewEntry = newEntry.getParentId();
            if (parentIdOfNewEntry != null && !parentIdOfNewEntry.isEmpty()) {
                // The given adapter has a parent which could already be in my
                // descendants
                if (parentIdOfNewEntry.equals(this.getId())) {
                    // this is the parent. We must insert the given adapter
                    // here. We merge all its
                    // descendants
                    mergeJsonAdapter(newEntry);
                    return this;
                } else {
                    // I am not the parent, let's check if I could be the
                    // parent
                    // of one the ancestor.
                    final String parentId = newEntry.getParentId();
                    DocumentModel parent = session.getEntry(parentId);
                    if (parent == null) {
                        if (log.isInfoEnabled()) {
                            log.info(String.format("parent %s not found for entry %s", parentId, newEntry.getId()));
                        }
                        mergeJsonAdapter(newEntry);
                        return this;
                    } else {
                        return push(new JSONAdapter(session, schema, parent).push(newEntry));
                    }
                }
            } else {
                // The given adapter has no parent, I can merge it in my
                // descendants.
                mergeJsonAdapter(newEntry);
                return this;
            }
        }

        private JSONObject toJSONObject() {
            if (isLeaf()) {
                return getObj();
            } else {
                // This entry has sub entries in the directory.
                // Ruled by Select2: an optionGroup is selectable or not
                // whether
                // we provide an Id or not in the JSON object.
                if (canSelectParent) {
                    // Make it selectable, keep as it is
                    return getObj().element("children", getChildrenJSONArray());
                } else {
                    // We don't want it to be selectable, we just serialize the
                    // label
                    return new JSONObject().element(Select2Common.LABEL, getLabel()).element("children",
                            getChildrenJSONArray());
                }
            }
        }

        public String toString() {
            return obj != null ? obj.toString() : null;
        }

    }

    private static final Log log = LogFactory.getLog(SuggestDirectoryEntries.class);

    public static final String ID = "Directory.SuggestEntries";

    @Context
    protected OperationContext ctx;

    @Context
    protected DirectoryService directoryService;

    @Context
    protected SchemaManager schemaManager;

    @Param(name = "directoryName", required = true)
    protected String directoryName;

    @Param(name = "localize", required = false)
    protected boolean localize;

    @Param(name = "lang", required = false)
    protected String lang;

    @Param(name = "searchTerm", alias = "prefix", required = false)
    protected String prefix;

    @Param(name = "labelFieldName", required = false)
    protected String labelFieldName = Select2Common.DIRECTORY_DEFAULT_LABEL_COL_NAME;

    @Param(name = "dbl10n", required = false)
    protected boolean dbl10n = false;

    @Param(name = "canSelectParent", required = false)
    protected boolean canSelectParent = false;

    @Param(name = "filterParent", required = false)
    protected boolean filterParent = false;

    @Param(name = "keySeparator", required = false)
    protected String keySeparator = Select2Common.DEFAULT_KEY_SEPARATOR;

    @Param(name = "displayObsoleteEntries", required = false)
    protected boolean displayObsoleteEntries = false;

    /**
     * @since 8.2
     */
    @Param(name = "limit", required = false)
    protected int limit = 100;

    /**
     * Fetch mode. If not contains, then starts with.
     *
     * @since 5.9.2
     */
    @Param(name = "contains", required = false)
    protected boolean contains = false;

    /**
     * Choose if sort is case sensitive
     *
     * @since 5.9.3
     */
    @Param(name = "caseSensitive", required = false)
    protected boolean caseSensitive = false;

    /**
     * Separator to display absolute label
     *
     * @since 5.9.2
     */
    @Param(name = "absoluteLabelSeparator", required = false)
    protected String absoluteLabelSeparator = "/";

    private String label = null;

    private boolean isChained = false;

    private String obsoleteWarningMessage = null;

    protected String getLang() {
        if (lang == null) {
            lang = (String) ctx.get("lang");
            if (lang == null) {
                lang = Select2Common.DEFAULT_LANG;
            }
        }
        return lang;
    }

    protected Locale getLocale() {
        return new Locale(getLang());
    }

    /**
     * @since 5.9.3
     */
    protected Collator getCollator() {
        if (collator == null) {
            collator = Collator.getInstance(getLocale());
            if (caseSensitive) {
                collator.setStrength(Collator.TERTIARY);
            } else {
                collator.setStrength(Collator.SECONDARY);
            }
        }
        return collator;
    }

    protected String getObsoleteWarningMessage() {
        if (obsoleteWarningMessage == null) {
            obsoleteWarningMessage = I18NUtils.getMessageString("messages", "obsolete", new Object[0], getLocale());
        }
        return obsoleteWarningMessage;
    }

    @OperationMethod
    public Blob run() {
        Directory directory = directoryService.getDirectory(directoryName);
        if (directory == null) {
            log.error("Could not find directory with name " + directoryName);
            return null;
        }
        try (Session session = directory.getSession()) {
            String schemaName = directory.getSchema();
            Schema schema = schemaManager.getSchema(schemaName);

            Field parentField = schema.getField(Select2Common.PARENT_FIELD_ID);
            isChained = parentField != null;

            String parentDirectory = directory.getParentDirectory();
            if (parentDirectory == null || parentDirectory.isEmpty() || parentDirectory.equals(directoryName)) {
                parentDirectory = null;
            }

            DocumentModelList entries = null;
            boolean postFilter = true;

            label = Select2Common.getLabelFieldName(schema, dbl10n, labelFieldName, getLang());

            Map<String, Serializable> filter = new HashMap<String, Serializable>();
            if (!displayObsoleteEntries) {
                filter.put(Select2Common.OBSOLETE_FIELD_ID, Long.valueOf(0));
            }
            Set<String> fullText = new TreeSet<String>();
            if (dbl10n || !localize) {
                postFilter = false;
                // do the filtering at directory level
                if (prefix != null && !prefix.isEmpty()) {
                    // filter.put(directory.getIdField(), prefix);
                    String computedPrefix = prefix;
                    if (contains) {
                        computedPrefix = '%' + computedPrefix;
                    }
                    filter.put(label, computedPrefix);
                    fullText.add(label);
                }
                if (filter.isEmpty()) {
                    // No filtering and we want the obsolete. We take all the
                    // entries
                    entries = session.getEntries();
                } else {
                    // We at least filter with prefix or/and exclude the
                    // obsolete
                    entries = session.query(filter, fullText,  new HashMap<String, String>(), false, limit, 0);
                }
            } else {
                // Labels are translated in properties file, we have to post
                // filter manually on all the entries
                if (filter.isEmpty()) {
                    // We want the obsolete. We take all the entries
                    entries = session.getEntries();
                } else {
                    // We want to exclude the obsolete
                    entries = session.query(filter, fullText,  new HashMap<String, String>(), false, limit, 0);
                }
            }

            JSONAdapter jsonAdapter = new JSONAdapter(session, schema);

            for (DocumentModel entry : entries) {
                JSONAdapter adapter = new JSONAdapter(session, schema, entry);
                if (!filterParent && isChained && parentDirectory == null) {
                    if (!adapter.isLeaf()) {
                        continue;
                    }
                }

                if (prefix != null && !prefix.isEmpty() && postFilter) {
                    if (contains) {
                        if (!adapter.getLabel().toLowerCase().contains(prefix.toLowerCase())) {
                            continue;
                        }
                    } else {
                        if (!adapter.getLabel().toLowerCase().startsWith(prefix.toLowerCase())) {
                            continue;
                        }
                    }
                }

                jsonAdapter.push(adapter);

            }
            return Blobs.createBlob(jsonAdapter.getChildrenJSONArray().toString(), "application/json");
        }
    }

    protected String translate(final String key) {
        if (key == null) {
            return "";
        }
        return I18NUtils.getMessageString("messages", key, new Object[0], getLocale());
    }

}
