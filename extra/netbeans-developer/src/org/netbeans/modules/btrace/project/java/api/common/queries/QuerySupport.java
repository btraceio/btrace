/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2007 Sun Microsystems, Inc.
 */

package org.netbeans.modules.btrace.project.java.api.common.queries;

import org.netbeans.modules.btrace.project.java.api.common.SourceRoots;
import org.netbeans.spi.java.queries.JavadocForBinaryQueryImplementation;
import org.netbeans.spi.java.queries.SourceForBinaryQueryImplementation;
import org.netbeans.spi.java.queries.SourceLevelQueryImplementation;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.netbeans.spi.queries.FileBuiltQueryImplementation;
import org.netbeans.spi.queries.FileEncodingQueryImplementation;
import org.netbeans.spi.queries.SharabilityQueryImplementation;
import org.openide.loaders.CreateFromTemplateAttributesProvider;
import org.openide.util.Parameters;

/**
 * Support class for creating different types of queries implementations.
 * @author Tomas Mysik
 */
public final class QuerySupport {

    private QuerySupport() {
    }

    /**
     * Create a new query to provide information about where Java sources
     * corresponding to binaries (classfiles) can be found.
     * @param helper {@link AntProjectHelper} used for resolving files, e.g. output directory.
     * @param evaluator {@link PropertyEvaluator} used for obtaining project properties.
     * @param srcRoots a list of source roots.
     * @param testRoots a list of test roots.
     * @return {@link SourceForBinaryQueryImplementation} to provide information about where Java sources can be found.
     * @see SourceForBinaryQueryImplementation
     */
    public static SourceForBinaryQueryImplementation createCompiledSourceForBinaryQuery(AntProjectHelper helper,
            PropertyEvaluator evaluator, SourceRoots srcRoots, SourceRoots testRoots) {
        Parameters.notNull("helper", helper); // NOI18N
        Parameters.notNull("evaluator", evaluator); // NOI18N
        Parameters.notNull("srcRoots", srcRoots); // NOI18N

        return new CompiledSourceForBinaryQueryImpl(helper, evaluator, srcRoots, testRoots);
    }

    /**
     * Create a new query to provide information about encoding of a file. The returned query listens to the changes
     * in particular property values.
     * @param eval {@link PropertyEvaluator} used for obtaining the value of source encoding.
     * @param sourceEncodingPropertyName the source encoding property name.
     * @return a {@link FileEncodingQueryImplementation} to provide information about encoding of a file.
     */
    public static FileEncodingQueryImplementation createFileEncodingQuery(PropertyEvaluator eval,
            String sourceEncodingPropertyName) {
        Parameters.notNull("eval", eval); // NOI18N
        Parameters.notNull("sourceEncodingPropertyName", sourceEncodingPropertyName); // NOI18N

        return new FileEncodingQueryImpl(eval, sourceEncodingPropertyName);
    }

    /**
     * Create a new query to find Javadoc. The returned query listens on changes of the Javadoc directory.
     * @param helper {@link AntProjectHelper} used for resolving files, e.g. output directory.
     * @param evaluator {@link PropertyEvaluator} used for obtaining the Javadoc root.
     * @return a {@link JavadocForBinaryQueryImplementation} to find Javadoc.
     */
    public static JavadocForBinaryQueryImplementation createJavadocForBinaryQuery(AntProjectHelper helper,
            PropertyEvaluator evaluator) {
        Parameters.notNull("helper", helper); // NOI18N
        Parameters.notNull("evaluator", evaluator); // NOI18N

        return new JavadocForBinaryQueryImpl(helper, evaluator);
    }

    /**
     * Create a new query to provide information about files sharability. The returned query listens to the changes
     * in particular source roots.
     * @param helper {@link AntProjectHelper} used for creating a query itself.
     * @param evaluator a {@link PropertyEvaluator property evaluator} to interpret paths with.
     * @param srcRoots a list of source roots to treat as sharable.
     * @param testRoots a list of test roots to treat as sharable.
     * @param additionalSourceRoots additional paths to treat as sharable (just pure property names, do not
     *          use <i>${</i> and <i>}</i> characters). Can be <code>null</code>.
     * @return a {@link SharabilityQueryImplementation} to provide information about files sharability.
     */
    public static SharabilityQueryImplementation createSharabilityQuery(AntProjectHelper helper,
            PropertyEvaluator evaluator, SourceRoots srcRoots, SourceRoots testRoots,
            String... additionalSourceRoots) {
        Parameters.notNull("helper", helper); // NOI18N
        Parameters.notNull("evaluator", evaluator); // NOI18N
        Parameters.notNull("srcRoots", srcRoots); // NOI18N

        return new SharabilityQueryImpl(helper, evaluator, srcRoots, testRoots, additionalSourceRoots);
    }

    /**
     * Create a new query to provide information about files sharability without any additional source roots. See
     * {@link #createSharabilityQuery(AntProjectHelper, PropertyEvaluator, SourceRoots, SourceRoots, String...)
     * createSharabilityQuery()}
     * for more information.
     */
    public static SharabilityQueryImplementation createSharabilityQuery(AntProjectHelper helper,
            PropertyEvaluator evaluator, SourceRoots srcRoots, SourceRoots testRoots) {

        return createSharabilityQuery(helper, evaluator, srcRoots, testRoots, (String[]) null);
    }

    /**
     * Create a new query to find out specification source level of Java source files.
     * @param evaluator {@link PropertyEvaluator} used for obtaining needed properties.
     * @return a {@link SourceLevelQueryImplementation} to find out specification source level of Java source files.
     */
    public static SourceLevelQueryImplementation createSourceLevelQuery(PropertyEvaluator evaluator) {
        Parameters.notNull("evaluator", evaluator); // NOI18N

        return new SourceLevelQueryImpl(evaluator);
    }

    /**
     * Create a new query to test whether a file can be considered to be built (up to date). The returned query
     * listens to the changes in particular source roots.
     * @param helper {@link AntProjectHelper} used for creating a query itself.
     * @param evaluator {@link PropertyEvaluator} used for obtaining needed properties.
     * @param sourceRoots a list of source roots.
     * @param testRoots a list of test roots.
     * @return a {@link FileBuiltQueryImplementation} to test whether a file can be considered to be built (up to date).
     */
    public static FileBuiltQueryImplementation createFileBuiltQuery(AntProjectHelper helper,
            PropertyEvaluator evaluator, SourceRoots sourceRoots, SourceRoots testRoots) {
        Parameters.notNull("helper", helper); // NOI18N
        Parameters.notNull("evaluator", evaluator); // NOI18N
        Parameters.notNull("sourceRoots", sourceRoots); // NOI18N

        return new FileBuiltQueryImpl(helper, evaluator, sourceRoots, testRoots);
    }

    /**
     * Creates an implementation of {@link CreateFromTemplateAttributesProvider} providing
     * attributes for the project license and encoding.
     *
     * @param helper {@link AntProjectHelper} used for reading the project properties.
     * @param encodingQuery {@link FileEncodingQueryImplementation} used to obtain an encoding.
     * @return a {@code CreateFromTemplateAttributesProvider}.
     *
     * @since 1.1
     */
    public static CreateFromTemplateAttributesProvider createTemplateAttributesProvider(AntProjectHelper helper, FileEncodingQueryImplementation encodingQuery) {
        Parameters.notNull("helper", helper);
        Parameters.notNull("encodingQuery", encodingQuery);
        return new TemplateAttributesProviderImpl(helper, encodingQuery);
    }
}
