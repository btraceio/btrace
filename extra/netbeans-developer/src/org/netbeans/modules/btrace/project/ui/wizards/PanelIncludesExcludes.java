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
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
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

package org.netbeans.modules.btrace.project.ui.wizards;

import java.awt.Component;
import java.io.File;
import javax.swing.event.ChangeListener;
import org.netbeans.modules.btrace.project.ui.customizer.BTraceProjectProperties;
import org.netbeans.spi.java.project.support.ui.IncludeExcludeVisualizer;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;

/**
 * New project from existing sources panel to configure includes and excludes.
 */
class PanelIncludesExcludes implements WizardDescriptor.FinishablePanel {

    private final IncludeExcludeVisualizer viz;

    public PanelIncludesExcludes() {
        viz = new IncludeExcludeVisualizer();
    }

    public boolean isFinishPanel() {
        return true;
    }

    public Component getComponent() {
        return viz.getVisualizerPanel();
    }

    public void addChangeListener(ChangeListener l) {}

    public void removeChangeListener(ChangeListener l) {}

    public boolean isValid() {
        return true;
    }

    public void storeSettings(Object wiz) {
        WizardDescriptor w = (WizardDescriptor) wiz;
        w.putProperty(BTraceProjectProperties.INCLUDES, viz.getIncludePattern());
        w.putProperty(BTraceProjectProperties.EXCLUDES, viz.getExcludePattern());
    }

    public void readSettings(Object wiz) {
        WizardDescriptor w = (WizardDescriptor) wiz;
        String includes = (String) w.getProperty(BTraceProjectProperties.INCLUDES);
        if (includes == null) {
            includes = "**"; // NOI18N
        }
        viz.setIncludePattern(includes);
        String excludes = (String) w.getProperty(BTraceProjectProperties.EXCLUDES);
        if (excludes == null) {
            excludes = ""; // NOI18N
        }
        viz.setExcludePattern(excludes);
        File[] sourceRoots = (File[]) w.getProperty("sourceRoot");
        File[] testRoots = (File[]) w.getProperty("testRoot");
        File[] roots = new File[sourceRoots.length + testRoots.length];
        System.arraycopy(sourceRoots, 0, roots, 0, sourceRoots.length);
        System.arraycopy(testRoots, 0, roots, sourceRoots.length, testRoots.length);
        viz.setRoots(roots);
    }

    public HelpCtx getHelp() {
        return new HelpCtx(PanelIncludesExcludes.class);
    }

}
