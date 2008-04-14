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

package org.netbeans.modules.btrace.project.ui;


import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.HelpCtx;
import org.openide.util.actions.NodeAction;
import org.netbeans.spi.project.support.ant.EditableProperties;

import java.util.Iterator;

/**
 * Action for removing an ClassPathRoot. The action looks up
 * the {@link RemoveClassPathRootAction.Removable} in the
 * activated node's Lookups and delegates to it.
 * @author Tomas Zezula
 */
final class RemoveClassPathRootAction extends NodeAction {

    /**
     * Implementation of this interfaces has to be placed
     * into the node's Lookup to allow {@link RemoveClassPathRootAction}
     * on the node.
     */
    static interface Removable {
        /**
         * Checks if the classpath root can be removed
         * @return returns true if the action should be enabled
         */
        public boolean canRemove ();

        /**
         * Removes the classpath root
         */
        public abstract void remove ();
    }

    protected void performAction(Node[] activatedNodes) {
        for (int i=0; i<activatedNodes.length; i++) {
            Removable removable = (Removable) activatedNodes[i].getLookup().lookup(Removable.class);
            if (removable!=null) {
                removable.remove();
            }
        }
    }

    protected boolean enable(Node[] activatedNodes) {
        for (int i=0; i<activatedNodes.length; i++) {
            Removable removable = (Removable) activatedNodes[i].getLookup().lookup(Removable.class);
            if (removable==null) {
                return false;
            }
            if (!removable.canRemove()) {
                return false;
            }
        }
        return true;
    }

    public String getName() {
        return NbBundle.getMessage (RemoveClassPathRootAction.class,"CTL_RemoveProject");
    }

    public HelpCtx getHelpCtx() {
        return new HelpCtx (RemoveClassPathRootAction.class);
    }

    protected boolean asynchronous() {
        return false;
    }

    /**
     * Checks if the reference is still used in the project.
     * @param props the array of {@link EditableProperties} which
     * should be checked.
     * @param reference
     * @return true if the reference is used
     */
    public static boolean isReferenced (EditableProperties[] props, String reference) {
        for (int i=0; i< props.length; i++) {
            for (Iterator it = props[i].values().iterator(); it.hasNext();) {
                String value = (String) it.next ();
                if (value != null && value.indexOf(reference)>=0) {
                    return true;
                }
            }
        }
        return false;
    }
}
