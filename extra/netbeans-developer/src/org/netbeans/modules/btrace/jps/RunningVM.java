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

package org.netbeans.modules.btrace.jps;


/**
 * A container for various information available for a running JVM.
 * Note that "VM flags" that we have for the VM in principle, is various -XX:+... options, which are supposed to
 * be used only by real expert users, or for debugging. We have them here just for completeness, but since they
 * are used very rarely, there is probably no reason to display them in the attach dialog or whatever.
 *
 * @author Misha Dmitriev
 */
public class RunningVM {
    //~ Instance fields ----------------------------------------------------------------------------------------------------------

    private String mainArgs;
    private String mainClass;
    private String vmArgs;
    private String vmFlags;
    private int pid;

    //~ Constructors -------------------------------------------------------------------------------------------------------------

    /** Creates a new instance of RunningVM */
    public RunningVM(int pid, String vmFlags, String vmArgs, String mainClass, String mainArgs) {
        this.pid = pid;
        this.vmFlags = vmFlags;
        this.vmArgs = vmArgs;
        this.mainClass = mainClass;
        this.mainArgs = mainArgs;
    }

    //~ Methods ------------------------------------------------------------------------------------------------------------------

    public String getMainArgs() {
        return mainArgs;
    }

    public String getMainClass() {
        return mainClass;
    }

    public int getPid() {
        return pid;
    }

    public String getVMArgs() {
        return vmArgs;
    }

    public String getVMFlags() {
        return vmFlags;
    }

    public String toString() {
        return getPid() + "  " + getVMFlags() + "  " + getVMArgs() + "  " + getMainClass() + "  " + getMainArgs(); // NOI18N
    }
}
