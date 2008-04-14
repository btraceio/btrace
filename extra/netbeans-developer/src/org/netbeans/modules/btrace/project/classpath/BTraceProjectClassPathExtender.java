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
package org.netbeans.modules.btrace.project.classpath;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.netbeans.spi.java.project.classpath.ProjectClassPathExtender;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.netbeans.api.project.libraries.Library;
import org.netbeans.api.project.ant.AntArtifact;

@Deprecated
public class BTraceProjectClassPathExtender implements ProjectClassPathExtender {
    
    private static final String CP_CLASS_PATH = "javac.classpath"; //NOI18N

    private final BTraceProjectClassPathModifier delegate;

    public BTraceProjectClassPathExtender (final BTraceProjectClassPathModifier delegate) {
        assert delegate != null;
        this.delegate = delegate;
    }

    public boolean addLibrary(final Library library) throws IOException {
        return addLibrary(CP_CLASS_PATH, library);
    }

    public boolean addLibrary(final String type, final Library library) throws IOException {
        return this.delegate.handleLibraries (new Library[] {library},type, BTraceProjectClassPathModifier.ADD);
    }

    public boolean addArchiveFile(final FileObject archiveFile) throws IOException {
        return addArchiveFile(CP_CLASS_PATH,archiveFile);
    }

    public boolean addArchiveFile(final String type, FileObject archiveFile) throws IOException {
        if (FileUtil.isArchiveFile(archiveFile)) {
            archiveFile = FileUtil.getArchiveRoot (archiveFile);
        }
        try {
            return this.delegate.handleRoots(new URI[]{archiveFile.getURL().toURI()}, type, BTraceProjectClassPathModifier.ADD, true);
        } catch (URISyntaxException ex) {
            IOException ioe = new IOException();
            ioe.initCause(ex);
            throw ioe;
        }
    }

    public boolean addAntArtifact(final AntArtifact artifact, final URI artifactElement) throws IOException {
        return addAntArtifact(CP_CLASS_PATH,artifact, artifactElement);
    }

    public boolean addAntArtifact(final String type, final AntArtifact artifact, final URI artifactElement) throws IOException {
        return this.delegate.handleAntArtifacts(new AntArtifact[] {artifact}, new URI[] {artifactElement},type,BTraceProjectClassPathModifier.ADD);
    }

}
