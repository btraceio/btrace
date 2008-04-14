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

package org.netbeans.modules.btrace.project.ui.packageview;

import java.awt.Image;
import org.netbeans.api.java.queries.AccessibilityQuery;
import org.netbeans.api.queries.VisibilityQuery;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;

// XXX needs unit test

/**
 * Provides display name and icon utilities for
 * {@link PackageViewChildren.PackageNode} and {@link PackageListView.PackageItem}.
 * @author Jesse Glick
 */
public final class PackageDisplayUtils {

    private PackageDisplayUtils() {}
    
    /** whether to turn on #42589 */
    private static final boolean TRUNCATE_PACKAGE_NAMES =
        Boolean.getBoolean("org.netbeans.spi.java.project.support.ui.packageView.TRUNCATE_PACKAGE_NAMES"); // NOI18N

    private static final Image PACKAGE = Utilities.loadImage("org/netbeans/spi/java/project/support/ui/package.gif"); // NOI18N
    private static final Image PACKAGE_EMPTY = Utilities.loadImage("org/netbeans/spi/java/project/support/ui/packageEmpty.gif"); // NOI18N
    private static final Image PACKAGE_PRIVATE = Utilities.loadImage("org/netbeans/spi/java/project/support/ui/packagePrivate.gif"); // NOI18N
    private static final Image PACKAGE_PUBLIC = Utilities.loadImage("org/netbeans/spi/java/project/support/ui/packagePublic.gif"); // NOI18N

    /**
     * Find the proper display label for a package.
     * @param pkg the actual folder
     * @param pkgname the dot-separated package name (<code>""</code> for default package)
     * @return an appropriate display label for it
     */
    public static String getDisplayLabel(String pkgname) {
        return computePackageName(pkgname, TRUNCATE_PACKAGE_NAMES);
    }
    
    /**
     * Find the proper tool tip for a package.
     * May have more info than the display label.
     * @param pkg the actual folder
     * @param pkgname the dot-separated package name (<code>""</code> for default package)
     * @return an appropriate display label for it
     */
    public static String getToolTip(FileObject pkg, String pkgname) {
        String pkglabel = computePackageName(pkgname, false);
        Boolean b = AccessibilityQuery.isPubliclyAccessible(pkg);
        if (b != null) {
            if (b.booleanValue()) {
                return NbBundle.getMessage(PackageDisplayUtils.class, "LBL_public_package", pkglabel);
            } else {
                return NbBundle.getMessage(PackageDisplayUtils.class, "LBL_private_package", pkglabel);
            }
        } else {
            return NbBundle.getMessage(PackageDisplayUtils.class, "LBL_package", pkglabel);
        }
    }
    
    /**
     * Get package name.
     * Handles default package specially.
     * @param truncate if true, show a truncated version to save display space
     */
    private static String computePackageName(String pkgname, boolean truncate) {
        if (pkgname.length() == 0) {
            return NbBundle.getMessage(PackageDisplayUtils.class, "LBL_DefaultPackage"); // NOI18N
        } else {
            if (truncate) {
                // #42589: keep only first letter of first package component, up to three of others
                return pkgname.replaceFirst("^([^.])[^.]+\\.", "$1.").replaceAll("([^.]{3})[^.]+\\.", "$1."); // NOI18N
            } else {
                return pkgname;
            }
        }
    }

     
    
    /**
     * Find the proper display icon for a package.
     * @param pkg the actual folder
     * @param pkgname the dot-separated package name (<code>""</code> for default package)
     * @return an appropriate display icon for it
     */
    public static Image getIcon(FileObject pkg, String pkgname) {
        return getIcon( pkg, pkgname, isEmpty(pkg) );
    }
    
    /** Performance optiomization if the the isEmpty status is alredy known.
     * 
     */
    public static Image getIcon(FileObject pkg, String pkgname, boolean empty ) {
        if ( empty ) {
            return PACKAGE_EMPTY;
        } else {
            Boolean b = pkg.isValid() ? AccessibilityQuery.isPubliclyAccessible(pkg) : null;
            if (b != null) {
                if (b.booleanValue()) {
                    return PACKAGE_PUBLIC;
                } else {
                    return PACKAGE_PRIVATE;
                }
            } else {
                return PACKAGE;
            } 
        }
    }
    
    
    /**
     * Check whether a package is empty (devoid of files except for subpackages).
     */
    public static boolean isEmpty( FileObject fo ) {    
        return isEmpty (fo, true );
    }

    /**
     * Check whether a package is empty (devoid of files except for subpackages).
     * @param recurse specifies whether to check if subpackages are empty too.
     */
    public static boolean isEmpty( FileObject fo, boolean recurse ) {            
        FileObject[] kids = fo.getChildren();
        for( int i = 0; i < kids.length; i++ ) {
            // XXX consider using group.contains() here
            if ( !kids[i].isFolder() && VisibilityQuery.getDefault().isVisible( kids[i] ) ) {
                return false;
            }  
            else if (recurse && VisibilityQuery.getDefault().isVisible( kids[i] ) && !isEmpty(kids[i])) {
                    return false;
            }
        }
        return true;
    }
    
    /**
     * Check whether a package should be displayed.
     * It should be displayed if {@link VisibilityQuery} says it should be,
     * and it is either completely empty, or contains files (as opposed to
     * containing some subpackages but no files).
     */
    public static boolean isSignificant(FileObject pkg) throws IllegalArgumentException {
        if (!pkg.isFolder()) {
            throw new IllegalArgumentException("Not a folder"); // NOI18N
        }
        // XXX consider using group.contains() here
        if (!VisibilityQuery.getDefault().isVisible(pkg)) {
            return false;
        }
        FileObject[] kids = pkg.getChildren();
        boolean subpackages = false;
        for (int i = 0; i < kids.length; i++) {
            if (!VisibilityQuery.getDefault().isVisible(kids[i])) {
                continue;
            }
            if (kids[i].isData()) {
                return true;
            } else {
                subpackages = true;
            }
        }
        return !subpackages;
    }
    
}
