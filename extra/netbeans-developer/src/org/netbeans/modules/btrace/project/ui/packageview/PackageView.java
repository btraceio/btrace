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

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Image;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.plaf.UIResource;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.queries.VisibilityQuery;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;

/**
 * Factory for package views.
 * @see org.netbeans.spi.project.ui.LogicalViewProvider
 * @author Jesse Glick
 */
public class PackageView {
        
    private PackageView() {}
    
    /**
     * Create a node which will contain package-oriented view of a source group.
     * <p>
     * The precise structure of this node is <em>not</em> specified by the API
     * and is subject to arbitrary change (perhaps at user option).
     * Callers should not make assumptions about the nature of subnodes, the
     * code or display names of certain nodes, and so on. You may use cookies/lookup
     * to find if particular subnodes correspond to folders or files.
     * </p>
     * @param group a source group which should be represented
     * @return node which will display packages in given group
     */
    public static Node createPackageView( SourceGroup group ) {
        return new RootNode (group);                
    }
    
    /**
     * Finds the node representing given object, if any.
     * The current implementation works only for {@link org.openide.filesystems.FileObject}s
     * and {@link org.openide.loaders.DataObject}s.
     * @param rootNode a node some descendant of which should contain the object
     * @param object object to find
     * @return a node representing the given object, or null if no such node was found
     */
    public static Node findPath(Node rootNode, Object object) {
        
        PackageRootNode.PathFinder pf = rootNode.getLookup().lookup(PackageRootNode.PathFinder.class);
        
        if ( pf != null ) {
            return pf.findPath( rootNode, object );
        } else {
            TreeRootNode.PathFinder pf2 = rootNode.getLookup().lookup(TreeRootNode.PathFinder.class);
            if (pf2 != null) {
                return pf2.findPath(rootNode, object);
            } else {
                return null;
            }
        }
    }
    
    /**
     * Create a list or combo box model suitable for {@link javax.swing.JList} from a source group
     * showing all Java packages in the source group.
     * To display it you will also need {@link #listRenderer}.
     * <p>No particular guarantees are made as to the nature of the model objects themselves,
     * except that {@link Object#toString} will give the fully-qualified package name
     * (or <code>""</code> for the default package), regardless of what the renderer
     * actually displays.</p>
     * @param group a Java-like source group
     * @return a model of its packages
     * @since org.netbeans.modules.java.project/1 1.3 
     */
    
    public static ComboBoxModel createListView(SourceGroup group) {        
        SortedSet<PackageItem> data = new TreeSet<PackageItem>();
        findNonExcludedPackages(null, data, group.getRootFolder(), group, false);
        return new DefaultComboBoxModel(data.toArray(new PackageItem[data.size()]));
    }
    
    /** Fills given collection with flattened packages under given folder
     *@param target The collection to be filled
     *@param fo The folder to be scanned
     * @param group the group to scan
     * @param createPackageItems if false the collection will be filled with file objects; if
     *       true PackageItems will be created.
     * @param showProgress whether to show a progress handle or not
     */
    static void findNonExcludedPackages(PackageViewChildren children, Collection<PackageItem> target, FileObject fo, SourceGroup group, boolean showProgress) {
        if (showProgress) {
            ProgressHandle progress = ProgressHandleFactory.createHandle(NbBundle.getMessage(PackageView.class, "PackageView.find_packages_progress", FileUtil.getFileDisplayName(fo)));
            progress.start(1000);
            findNonExcludedPackages(children, target, fo, group, progress, 0, 1000);
            progress.finish();
        } else {
            findNonExcludedPackages(children, target, fo, group, null, 0, 0);
        }
    }

    private static void findNonExcludedPackages(PackageViewChildren children, Collection<PackageItem> target, FileObject fo, SourceGroup group, ProgressHandle progress, int start, int end) {
        
        assert fo.isFolder() : "Package view only accepts folders"; // NOI18N
        
        if (progress != null) {
            String path = FileUtil.getRelativePath(children.getRoot(), fo);
            assert path != null : fo + " in " + children.getRoot();
            progress.progress(path.replace('/', '.'), start);
        }
        
        if (!fo.isValid()) {
            return;
        }
               
        if ( !VisibilityQuery.getDefault().isVisible( fo ) ) {
            return; // Don't show hidden packages
        }
        
        boolean hasSubfolders = false;
        boolean hasFiles = false;
        List<FileObject> folders = new ArrayList<FileObject>();
        for (FileObject kid : fo.getChildren()) {
            // XXX could use PackageDisplayUtils.isSignificant here
            if (kid.isValid() && VisibilityQuery.getDefault().isVisible(kid) && group.contains(kid)) {
                if (kid.isFolder()) {
                    folders.add(kid);
                    hasSubfolders = true;
                } 
                else {
                    hasFiles = true;
                }
            }
        }
        if (hasFiles || !hasSubfolders) {
            if (target != null) {
                target.add( new PackageItem(group, fo, !hasFiles ) );
            }
            else {
                if (fo.isValid()) {
                    children.add(fo, !hasFiles, false);
                }
            }
        }
        if (!folders.isEmpty()) {
            int diff = (end - start) / folders.size();
            int c = 0;
            for (FileObject kid : folders) {
                // Do this after adding the parent, so we get a pre-order traversal.
                // Also see PackageViewChildren.findChild: prefer to get root first.
                findNonExcludedPackages(children, target, kid, group, progress, start + c * diff, start + (c + 1) * diff);
                c++;
            }
        }
    }
         
//    public static ComboBoxModel createListView(SourceGroup group) {
//        DefaultListModel model = new DefaultListModel();
//        SortedSet/*<PackageItem>*/ items = new TreeSet();
//        FileObject root = group.getRootFolder();
//        if (PackageDisplayUtils.isSignificant(root)) {
//            items.add(new PackageItem(group, root));
//        }
//        Enumeration/*<FileObject>*/ files = root.getChildren(true);
//        while (files.hasMoreElements()) {
//            FileObject f = (FileObject) files.nextElement();
//            if (f.isFolder() && PackageDisplayUtils.isSignificant(f)) {
//                items.add(new PackageItem(group, f));
//            }
//        }
//        return new DefaultComboBoxModel(items.toArray(new PackageItem[items.size()]));
//    }
    
    
    /**
     * Create a renderer suited to rendering models created using {@link #createListView}.
     * The exact nature of the display is not specified.
     * Instances of String can also be rendered.
     * @return a suitable package renderer
     * @since org.netbeans.modules.java.project/1 1.3 
     */
    public static ListCellRenderer listRenderer() {
        return new PackageListCellRenderer();
    }
    
    /**
     * FilterNode which listens on the PackageViewSettings and changes the view to 
     * the package view or tree view
     *
     */
    private static final class RootNode extends FilterNode implements PropertyChangeListener {
        
        private SourceGroup sourceGroup;
        
        private RootNode (SourceGroup group) {
            super(getOriginalNode(group));
            this.sourceGroup = group;
//            JavaProjectSettings.addPropertyChangeListener(WeakListeners.propertyChange(this, JavaProjectSettings.class));
            group.addPropertyChangeListener(WeakListeners.propertyChange(this, group));
        }

        // XXX #98573: very crude, but what else to do? Want to call changeOriginal asynchronously.
        // But this could randomly screw up tests - not just PackageViewTest, but maybe others too.
        // (org.netbeans.modules.java.freeform.ui.ViewTest does not appear to be affected.)
        private static boolean IN_UNIT_TEST = false;
        static {
            try {
                Class.forName("junit.framework.TestCase");
                IN_UNIT_TEST = true;
            } catch (ClassNotFoundException e) {}
        }
        public void propertyChange (PropertyChangeEvent event) {
//            String prop = event.getPropertyName();
//            if (JavaProjectSettings.PROP_PACKAGE_VIEW_TYPE.equals(prop) || SourceGroup.PROP_CONTAINERSHIP.equals(prop)) {
//                if (IN_UNIT_TEST) {
//                    changeOriginal(getOriginalNode(sourceGroup), true);
//                } else {
//                    EventQueue.invokeLater(new Runnable() {
//                        public void run() {
//                            changeOriginal(getOriginalNode(sourceGroup), true);
//                        }
//                    });
//                }
//            }
        }
        
        private static Node getOriginalNode(SourceGroup group) {
            FileObject root = group.getRootFolder();
            //Guard condition, if the project is (closed) and deleted but not yet gced
            // and the view is switched, the source group is not valid.
            if ( root == null || !root.isValid()) {
                return new AbstractNode (Children.LEAF);
            }
            return new PackageRootNode(group);
//            switch (JavaProjectSettings.getPackageViewType()) {
//                case JavaProjectSettings.TYPE_PACKAGE_VIEW:
//                    return new PackageRootNode(group);
//                case JavaProjectSettings.TYPE_TREE:
//                    return new TreeRootNode(group);
//                default:
//                    assert false : "Unknown PackageView Type"; //NOI18N
//                    return new PackageRootNode(group);
//            }
        }        
    }
    
    /**
     * Model item representing one package.
     */
    static final class PackageItem implements Comparable<PackageItem> {
        
        private static Map<Image,Icon> image2icon = new IdentityHashMap<Image,Icon>();
        
        private final boolean empty;
        private final FileObject pkg;
        private final String pkgname;
        private Icon icon;
        
        public PackageItem(SourceGroup group, FileObject pkg, boolean empty) {
            this.pkg = pkg;
            this.empty = empty;
            String path = FileUtil.getRelativePath(group.getRootFolder(), pkg);
            assert path != null : "No " + pkg + " in " + group;
            pkgname = path.replace('/', '.');
        }
        
        public String toString() {
            return pkgname;
        }
        
        public String getLabel() {
            return PackageDisplayUtils.getDisplayLabel(pkgname);
        }
        
        public Icon getIcon() {
            if ( icon == null ) {
                Image image = PackageDisplayUtils.getIcon(pkg, pkgname, empty);                
                icon = image2icon.get(image);
                if ( icon == null ) {            
                    icon = new ImageIcon( image );
                    image2icon.put( image, icon );
                }
            }
            return icon;
        }

        public int compareTo(PackageItem p) {
            return pkgname.compareTo(p.pkgname);
        }
        
    }
    
    /**
     * The renderer which just displays {@link PackageItem#getLabel} and {@link PackageItem#getIcon}.
     */
    private static final class PackageListCellRenderer extends JLabel implements ListCellRenderer, UIResource {
        
        public PackageListCellRenderer() {
            setOpaque(true);
        }

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            // #93658: GTK needs name to render cell renderer "natively"
            setName("ComboBox.listRenderer"); // NOI18N
            
            if (value instanceof PackageItem) {
                PackageItem pkgitem = (PackageItem) value;
                setText(pkgitem.getLabel());
                setIcon(pkgitem.getIcon());
            } else {
                // #49954: render a specially inserted package somehow.
                String pkgitem = (String) value;
                setText(pkgitem);
                setIcon(null);
            }
            
            if ( isSelected ) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());             
            }
            else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            
            return this;
        }
        
        // #93658: GTK needs name to render cell renderer "natively"
        public String getName() {
            String name = super.getName();
            return name == null ? "ComboBox.renderer" : name;  // NOI18N
    }
    
    }
    
    
}
