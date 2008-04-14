package org.netbeans.modules.btrace.project.java.api.common.queries;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.project.support.ant.EditableProperties;
import org.netbeans.spi.queries.FileEncodingQueryImplementation;
import org.openide.loaders.CreateFromTemplateAttributesProvider;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;

/**
 * Default implementation of {@link CreateFromTemplateAttributesProvider}.
 *
 * @author Andrei Badea
 */
class TemplateAttributesProviderImpl implements CreateFromTemplateAttributesProvider {

    private final AntProjectHelper helper;
    private final FileEncodingQueryImplementation encodingQuery;

    public TemplateAttributesProviderImpl(AntProjectHelper helper, FileEncodingQueryImplementation encodingQuery) {
        super();
        this.helper = helper;
        this.encodingQuery = encodingQuery;
    }

    public Map<String, ?> attributesFor(DataObject template, DataFolder target, String name) {
        EditableProperties props = helper.getProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH);
        String license = props.getProperty("project.license"); // NOI18N
        Charset charset = encodingQuery.getEncoding(target.getPrimaryFile());
        String encoding = (charset != null) ? charset.name() : null;
        if (license == null && encoding == null) {
            return null;
        } else {
            Map<String, String> values = new HashMap<String, String>();
            if (license != null) {
                values.put("license", license); // NOI18N
            }
            if (encoding != null) {
                values.put("encoding", encoding); // NOI18N
            }
            return Collections.singletonMap("project", values); // NOI18N
        }
    }
}
