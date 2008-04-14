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
 * Portions Copyrighted 2007 Sun Microsystems, Inc.
 */
package org.netbeans.modules.btrace.project;

import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.spi.java.classpath.ClassPathProvider;
import org.netbeans.spi.project.LookupMerger;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 *
 * @author Tomas Zezula
 */
public final class ClassPathProviderMerger implements LookupMerger<ClassPathProvider> {
    
    private final ClassPathProvider defaultProvider;
    
    public ClassPathProviderMerger (final ClassPathProvider defaultProvider) {
        assert defaultProvider != null;
        this.defaultProvider = defaultProvider;
    }

    public Class<ClassPathProvider> getMergeableClass() {
        return ClassPathProvider.class;
    }

    public ClassPathProvider merge(Lookup lookup) {
        return new CPProvider (lookup);
    }
    
    
    private class CPProvider implements ClassPathProvider {
        
        private final Lookup lookup;

        public CPProvider(final Lookup lookup) {
            assert lookup != null;
            this.lookup = lookup;
        }                

        public ClassPath findClassPath(FileObject file, String type) {
            ClassPath result = defaultProvider.findClassPath(file, type);
            if (result != null) {
                return result;
            }
            for (ClassPathProvider cpProvider : lookup.lookupAll(ClassPathProvider.class)) {
                result = cpProvider.findClassPath(file, type);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }        
    }

}
