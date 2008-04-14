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

package org.netbeans.modules.btrace.project.java.api.common.ant;

import java.io.IOException;
import org.netbeans.spi.project.support.ant.EditableProperties;
import org.w3c.dom.Element;

/**
 * Interface that has to be implemented in order to use {@link UpdateHelper}. It represents and does the project update
 * process itself.
 * @author Tomas Mysik
 * @see UpdateHelper
 */
public interface UpdateImplementation {

    /**
     * Return <code>true</code> if the project is of current version.
     * @return <code>true</code> if the project is of current version.
     */
    boolean isCurrent();

    /**
     * Return <code>true</code> if the project can be updated.
     * @return <code>true</code> if the project can be updated.
     */
    boolean canUpdate();

    /**
     * Saving of update. If the project is of current version it should probably do nothing.
     * @param props project properties to be saved, can be <code>null</code>. There's no need to save them because
     *          {@link UpdateHelper} does it.
     * @throws IOException if error occurs during saving.
     */
    void saveUpdate(final EditableProperties props) throws IOException;

    /**
     * Creates probably an in memory update of shared configuration data and return it.
     * @return the configuration data that is available.
     * @see {@link UpdateHelper#getPrimaryConfigurationData(boolean)}
     */
    Element getUpdatedSharedConfigurationData();

    /**
     * Creates probably an in memory update of project properties.
     * @return a set of properties.
     * @see {@link UpdateHelper#getProperties(String)}
     */
    EditableProperties getUpdatedProjectProperties();
}
