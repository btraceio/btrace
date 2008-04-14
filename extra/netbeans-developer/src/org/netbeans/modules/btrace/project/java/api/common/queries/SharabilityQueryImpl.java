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
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2007 Sun
 * Microsystems, Inc. All Rights Reserved.
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
 */

package org.netbeans.modules.btrace.project.java.api.common.queries;

import java.io.File;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.openide.util.Mutex;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.btrace.project.java.api.common.SourceRoots;
import org.netbeans.spi.queries.SharabilityQueryImplementation;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;

/**
 * Default implementation of {@link SharabilityQueryImplementation} which is capable to take more sources.
 * It listens to the changes in particular property values.
 * @author Tomas Zezula, Tomas Mysik
 */
class SharabilityQueryImpl implements SharabilityQueryImplementation, PropertyChangeListener {

    private final AntProjectHelper helper;
    private final PropertyEvaluator evaluator;
    private final SourceRoots srcRoots;
    private final List<String> additionalSourceRoots;
    private SharabilityQueryImplementation delegate;

    public SharabilityQueryImpl(AntProjectHelper helper, PropertyEvaluator evaluator, SourceRoots srcRoots,
            SourceRoots testRoots, String... additionalSourceRoots) {
        assert helper != null;
        assert evaluator != null;
        assert srcRoots != null;

        this.helper = helper;
        this.evaluator = evaluator;
        this.srcRoots = srcRoots;
        if (additionalSourceRoots != null) {
            this.additionalSourceRoots = Collections.unmodifiableList(Arrays.asList(additionalSourceRoots));
        } else {
            this.additionalSourceRoots = Collections.<String>emptyList();
        }
        this.srcRoots.addPropertyChangeListener(this);
    }

    public int getSharability(final File file) {
        return ProjectManager.mutex().readAccess(new Mutex.Action<Integer>() {
            public Integer run() {
                synchronized (SharabilityQueryImpl.this) {
                    if (delegate == null) {
                        delegate = createDelegate();
                    }
                    return delegate.getSharability(file);
                }
            }
        });
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (SourceRoots.PROP_ROOT_PROPERTIES.equals(evt.getPropertyName())) {
            synchronized (this) {
                delegate = null;
            }
        }
    }

    private SharabilityQueryImplementation createDelegate() {
        String[] srcProps = srcRoots.getRootProperties();
        String[] buildDirectories = new String[] {"${dist.dir}", "${build.dir}"}; // NOI18N

        int size = srcProps.length;
        size += additionalSourceRoots.size();
        List<String> props = new ArrayList<String>(size);

        for (String src : srcProps) {
            props.add("${" + src + "}"); // NOI18N
        }
        props.addAll(additionalSourceRoots);

        return helper.createSharabilityQuery(evaluator, props.toArray(new String[props.size()]), buildDirectories);
    }
}
