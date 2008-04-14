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

package org.netbeans.modules.btrace.project.queries;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.swing.event.ChangeListener;
import org.netbeans.api.java.queries.BinaryForSourceQuery;
import org.netbeans.api.java.queries.BinaryForSourceQuery.Result;
import org.netbeans.spi.java.queries.BinaryForSourceQueryImplementation;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.openide.util.ChangeSupport;
import org.openide.util.Exceptions;

import org.netbeans.modules.btrace.project.java.api.common.SourceRoots;
import org.netbeans.modules.btrace.project.ui.customizer.BTraceProjectProperties;
/**
 *
 * @author Tomas Zezula
 */
public class BinaryForSourceQueryImpl implements BinaryForSourceQueryImplementation {        
    
    
    private final Map<URL,BinaryForSourceQuery.Result>  cache = new HashMap<URL,BinaryForSourceQuery.Result>();
    private final SourceRoots  src;
    private final PropertyEvaluator eval;
    private final AntProjectHelper helper;
    
    /** Creates a new instance of BinaryForSourceQueryImpl */
    public BinaryForSourceQueryImpl(SourceRoots src, SourceRoots test, AntProjectHelper helper, PropertyEvaluator eval) {
        assert src != null;
        assert helper != null;
        assert eval != null;        
        this.src = src;
        this.eval = eval;
        this.helper = helper;
    }
    
    public Result findBinaryRoots(URL sourceRoot) {
        assert sourceRoot != null;
        BinaryForSourceQuery.Result result = cache.get(sourceRoot);
        if (result == null) {
            for (URL root : this.src.getRootURLs()) {
                if (root.equals(sourceRoot)) {
                    result = new R (BTraceProjectProperties.BUILD_CLASSES_DIR);
                    cache.put (sourceRoot,result);
                    break;
                }
            }
        }
        return result;
    }
    
    class R implements BinaryForSourceQuery.Result, PropertyChangeListener {
        
        private final String propName;
        private final ChangeSupport changeSupport = new ChangeSupport(this);
        
        R (final String propName) {
            assert propName != null;
            this.propName = propName;
            eval.addPropertyChangeListener(this);
        }
        
        public URL[] getRoots() {
            String val = eval.getProperty(propName);
            if (val != null) {                
                File f = helper.resolveFile(val);
                if (f != null) {
                    try {
                        return new URL[] {f.toURI().toURL()};
                    } catch (MalformedURLException e) {
                        Exceptions.printStackTrace(e);
                    }
                }
            }
            return new URL[0];
        }

        public void addChangeListener(ChangeListener l) {
            assert l != null;
            changeSupport.addChangeListener(l);
        }

        public void removeChangeListener(ChangeListener l) {
            assert l != null;
            changeSupport.removeChangeListener(l);
        }

        public void propertyChange(PropertyChangeEvent event) {
            changeSupport.fireChange();
        }
}

}
