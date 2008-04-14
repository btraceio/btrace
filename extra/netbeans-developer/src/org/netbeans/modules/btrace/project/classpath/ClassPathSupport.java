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

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.netbeans.api.project.ant.AntArtifact;
import org.netbeans.api.project.libraries.Library;
import org.netbeans.api.project.libraries.LibraryManager;
import org.netbeans.modules.btrace.project.java.api.common.ant.UpdateHelper;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.project.support.ant.EditableProperties;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.netbeans.spi.project.support.ant.ReferenceHelper;

/**
 *
 * @author Petr Hrebejk
 */
public class ClassPathSupport {
                
    // Prefixes and suffixes of classpath
    private static final String LIBRARY_PREFIX = "${libs."; // NOI18N
    private static final String LIBRARY_SUFFIX = ".classpath}"; // NOI18N

    private PropertyEvaluator evaluator;
    private final ReferenceHelper referenceHelper;
    private AntProjectHelper antProjectHelper;
    private Set<String> wellKnownPaths;
    private String antArtifactPrefix;
    private UpdateHelper updateHelper;
        
    /** Creates a new instance of ClassPathSupport */
    public  ClassPathSupport( PropertyEvaluator evaluator, 
                              ReferenceHelper referenceHelper,
                              AntProjectHelper antProjectHelper,
                              UpdateHelper updateHelper,
                              String[] wellKnownPaths,
                              String antArtifactPrefix ) {
        this.evaluator = evaluator;
        this.referenceHelper = referenceHelper;
        assert referenceHelper != null;
        this.antProjectHelper = antProjectHelper;
        this.wellKnownPaths = wellKnownPaths == null ? null : new HashSet<String>(Arrays.asList(wellKnownPaths));
        this.antArtifactPrefix = antArtifactPrefix;
        this.updateHelper = updateHelper;
    }
    
    /** Creates list of <CODE>Items</CODE> from given property.
     */    
    public Iterator<Item> itemsIterator( String propertyValue ) {
        // XXX More performance frendly impl. would retrun a lazzy iterator.
        return itemsList( propertyValue ).iterator();
    }
    
    public List<Item> itemsList( String propertyValue ) {    
        
        String pe[] = PropertyUtils.tokenizePath( propertyValue == null ? "": propertyValue ); // NOI18N        
        List<Item> items = new ArrayList<Item>( pe.length );        
        for( int i = 0; i < pe.length; i++ ) {
            Item item;

            // First try to find out whether the item is well known classpath
            if ( isWellKnownPath( pe[i] ) ) {
                // Some well know classpath
                item = Item.create( pe[i] );
            } 
            else if ( isLibrary( pe[i] ) ) {
                //Library from library manager
                String libraryName = getLibraryNameFromReference(pe[i]);
                assert libraryName != null : "Not a library reference: "+pe[i];
                Library library = referenceHelper.findLibrary(libraryName);
                if ( library == null ) {
                    item = Item.createBroken( Item.TYPE_LIBRARY, pe[i] );
                }
                else {
                    item = Item.create( library, pe[i] );
                }
            } 
            else if ( isAntArtifact( pe[i] ) ) {
                // Ant artifact from another project
                Object[] ret = referenceHelper.findArtifactAndLocation(pe[i]);                
                if ( ret[0] == null || ret[1] == null ) {
                    item = Item.createBroken( Item.TYPE_ARTIFACT, pe[i] );
                }
                else {
                    //fix of issue #55316
                    AntArtifact artifact = (AntArtifact)ret[0];
                    URI uri = (URI)ret[1];
                    File usedFile = antProjectHelper.resolveFile(evaluator.evaluate(pe[i]));
                    File artifactFile = new File (artifact.getScriptLocation().toURI().resolve(uri).normalize());
                    if (usedFile.equals(artifactFile)) {
                        item = Item.create( artifact, uri, pe[i] );
                    }
                    else {
                        item = Item.createBroken( Item.TYPE_ARTIFACT, pe[i] );
                    }
                }
            } else {
                // Standalone jar or property
                String eval = evaluator.evaluate( pe[i] );
                File f = null;
                if (eval != null) {
                    f = antProjectHelper.resolveFile( eval );
                }
                
                if ( f == null || !f.exists() ) {
                    item = Item.createBroken( eval, pe[i] );
                }
                else {
                    item = Item.create( eval, pe[i] );
                }

                //TODO these should be encapsulated in the Item class 
                // but that means we need to pass evaluator and antProjectHelper there.
                f = null;
                String ref = item.getSourceReference();                
                if (ref != null) {
                    eval = evaluator.evaluate( ref );                    
                    if (eval != null && !eval.contains(Item.SOURCE_START)) {
                        f = new File(eval); //antProjectHelper.resolveFile( eval );
                    }
                }
                File f2 = null;
                ref = item.getJavadocReference();
                if (ref != null) {
                    eval = evaluator.evaluate( ref );
                    f2 = null;
                    if (eval != null && !eval.contains(Item.JAVADOC_START)) {
                        f2 = new File(eval); //antProjectHelper.resolveFile( eval );
                    }
                }
                item.setInitialSourceAndJavadoc(f, f2);
            }
            
            items.add( item );
           
        }

        return items;
        
    }
    
    /** Converts list of classpath items into array of Strings.
     * !! This method creates references in the project !!
     */
    public String[] encodeToStrings(Iterator<Item> classpath) {
        
        List<String> result = new ArrayList<String>();
        
        while( classpath.hasNext() ) {

            Item item = classpath.next();
            String reference = null;
            switch( item.getType() ) {

                case Item.TYPE_JAR:
                    reference = item.getReference();
                    if ( item.isBroken() ) {
                        break;
                    }
                    if (reference == null) {
                        // New file
                        String file = item.getFilePath();
                        // pass null as expected artifact type to always get file reference
                        reference = referenceHelper.createForeignFileReferenceAsIs(file, null);
                        item.property = reference;
                    }
                    if (item.hasChangedSource()) {
                        if (item.getSourceFilePath() != null) {
                            referenceHelper.createExtraForeignFileReferenceAsIs(item.getSourceFilePath(), item.getSourceProperty());
                        } else {
                            //oh well, how do I do this otherwise??
                            EditableProperties ep = updateHelper.getProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH);
                            ep.remove(item.getSourceProperty());
                            updateHelper.putProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH, ep);
                        }
                    }
                    if (item.hasChangedJavadoc()) {
                        if (item.getJavadocFilePath() != null) {
                            referenceHelper.createExtraForeignFileReferenceAsIs(item.getJavadocFilePath(), item.getJavadocProperty());
                        } else {
                            //oh well, how do I do this otherwise??
                            EditableProperties ep = updateHelper.getProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH);
                            ep.remove(item.getJavadocProperty());
                            updateHelper.putProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH, ep);
                        }
                    }
                    break;
                case Item.TYPE_LIBRARY:
                    reference = item.getReference();
                    if ( item.isBroken() ) {
                        break;
                    }                    
                    Library library = item.getLibrary();                                       
                    if (reference == null) {
                        if ( library == null ) {
                            break;
                        }
                        reference = getLibraryReference( item );
                    }
                    break;    
                case Item.TYPE_ARTIFACT:
                    reference = item.getReference();
                    if ( item.isBroken() ) {
                        break;
                    }
                    AntArtifact artifact = item.getArtifact();
                    if ( reference == null) {
                        if ( artifact == null ) {
                            break;
                        }
                        reference = referenceHelper.addReference( item.getArtifact(), item.getArtifactURI());
                    }
                    break;
                case Item.TYPE_CLASSPATH:
                    reference = item.getReference();
                    break;
            }
            
            if ( reference != null ) {
                result.add( reference );
            }

        }

        String[] items = new String[ result.size() ];
        for( int i = 0; i < result.size(); i++) {
            if ( i < result.size() - 1 ) {
                items[i] = result.get( i ) + ":";
            }
            else  {       
                items[i] = result.get(i);
            }
        }
        
        return items;
    }
    
    public void updateJarReference(Item item) {
        String eval = evaluator.evaluate( item.getReference() );

        item.object = eval;

        //TODO these should be encapsulated in the Item class 
        // but that means we need to pass evaluator and antProjectHelper there.
        String ref = item.getSourceReference();
        if (ref != null) {
            eval = evaluator.evaluate( ref );
            if (eval != null && !eval.contains(Item.SOURCE_START)) {
                item.setSourceFilePath(eval);
            }
        }
        ref = item.getJavadocReference();
        if (ref != null) {
            eval = evaluator.evaluate( ref );
            if (eval != null && !eval.contains(Item.JAVADOC_START)) {
                item.setJavadocFilePath(eval);
            }
        }        
    }
    
    public String getLibraryReference( Item item ) {
        if ( item.getType() != Item.TYPE_LIBRARY ) {
            throw new IllegalArgumentException( "Item must be of type LIBRARY" );
        }
        return referenceHelper.createLibraryReference(item.getLibrary(), "classpath"); // NOI18N
    }
    
    // Private methods ---------------------------------------------------------

    private boolean isWellKnownPath( String property ) {
        return wellKnownPaths == null ? false : wellKnownPaths.contains( property );
    }
    
    private boolean isAntArtifact( String property ) {        
        return antArtifactPrefix == null ? false : property.startsWith( antArtifactPrefix );
    }
    
    private static boolean isLibrary( String property ) {
        return property.startsWith(LIBRARY_PREFIX) && property.endsWith(LIBRARY_SUFFIX);
    }
        
    // Innerclasses ------------------------------------------------------------
    
    /** Item of the classpath.
     */    
    public static class Item {
        
        // Types of the classpath elements
        public static final int TYPE_JAR = 0;
        public static final int TYPE_LIBRARY = 1;
        public static final int TYPE_ARTIFACT = 2;
        public static final int TYPE_CLASSPATH = 3;
        
        private static final String REF_START = "${file.reference."; //NOI18N
        private static final int REF_START_INDEX = REF_START.length();
        private static final String JAVADOC_START = "${javadoc.reference."; //NOI18N
        private static final String SOURCE_START = "${source.reference."; //NOI18N
        
        private Object object;
        private URI artifactURI;
        private int type;
        private String property;
        private boolean broken;
        private String sourceFile;
        private String javadocFile;
        
        private File initialSourceFile;
        private File initialJavadocFile;
        private String libraryName;
        
        private Item( int type, Object object, String property, boolean broken ) {
            this.type = type;
            this.object = object;
            this.property = property;
            this.broken = broken;
        }
        
        private Item (int type, Object object, String property) {
            this (type, object, property, false);
        }
        
        private Item( int type, Object object, URI artifactURI, String property ) {
            this( type, object, property, false);
            this.artifactURI = artifactURI;
        }
        
              
        // Factory methods -----------------------------------------------------
        
        
        public static Item create( Library library, String property ) {
            if ( library == null ) {
                throw new IllegalArgumentException( "library must not be null" ); // NOI18N
            }
            Item itm = new Item( TYPE_LIBRARY, library, property);
            itm.libraryName = library.getName();
            itm.reassignLibraryManager( library.getManager() );
            return itm;
        }
        
        public static Item create( AntArtifact artifact, URI artifactURI, String property ) {
            if ( artifactURI == null ) {
                throw new IllegalArgumentException( "artifactURI must not be null" ); // NOI18N
            }
            if ( artifact == null ) {
                throw new IllegalArgumentException( "artifact must not be null" ); // NOI18N
            }
            return new Item( TYPE_ARTIFACT, artifact, artifactURI, property );
        }
        
        public static Item create( String path, String property ) {
            if ( path == null ) {
                throw new IllegalArgumentException( "path must not be null" ); // NOI18N
            }
            return new Item( TYPE_JAR, path, property );
        }
        
        public static Item create( String property ) {
            if ( property == null ) {
                throw new IllegalArgumentException( "property must not be null" ); // NOI18N
            }
            return new Item ( TYPE_CLASSPATH, null, property );
        }
        
        public static Item createBroken( int type, String property ) {
            if ( property == null ) {
                throw new IllegalArgumentException( "property must not be null in broken items" ); // NOI18N
            }
            Item itm = new Item( type, null, property, true);
            if (type == TYPE_LIBRARY) {
                Pattern LIBRARY_REFERENCE = Pattern.compile("\\$\\{libs\\.([^${}]+)\\.[^${}]+\\}"); // NOI18N
                Matcher m = LIBRARY_REFERENCE.matcher(property);
                if (m.matches()) {
                    itm.libraryName = m.group(1);
                } else {
                    assert false : property;
                }
            }
            return itm;
        }                
        
        public static Item createBroken( final String file, String property ) {
            if ( property == null ) {
                throw new IllegalArgumentException( "property must not be null in broken items" ); // NOI18N
            }
            return new Item( TYPE_JAR, file, property, true);
        }
        
        // Instance methods ----------------------------------------------------
        
        public int getType() {
            return type;
        }
        
        public Library getLibrary() {
            if ( getType() != TYPE_LIBRARY ) {
                throw new IllegalArgumentException( "Item is not of required type - LIBRARY" ); // NOI18N
            }
            assert object == null || object instanceof Library :
                "Invalid object type: "+object.getClass().getName()+" instance: "+object.toString()+" expected type: Library";   //NOI18N
            return (Library)object;
        }
        
        public String getFilePath() {
            if ( getType() != TYPE_JAR ) {
                throw new IllegalArgumentException( "Item is not of required type - JAR" ); // NOI18N
            }
            return (String)object;
        }
        
        public AntArtifact getArtifact() {
            if ( getType() != TYPE_ARTIFACT ) {
                throw new IllegalArgumentException( "Item is not of required type - ARTIFACT" ); // NOI18N
            }
            return (AntArtifact)object;
        }
        
        public URI getArtifactURI() {
            if ( getType() != TYPE_ARTIFACT ) {
                throw new IllegalArgumentException( "Item is not of required type - ARTIFACT" ); // NOI18N
            }
            return artifactURI;
        }
        
        public void reassignLibraryManager(LibraryManager newManager) {
            if (getType() != TYPE_LIBRARY) {
                throw new IllegalArgumentException(" reassigning only works for type - LIBRARY");
            }
            assert libraryName != null;
            if (getLibrary() == null || newManager != getLibrary().getManager()) {
                Library lib = newManager.getLibrary(libraryName);
                if (lib == null) {
                    broken = true;
                    object = null;
                } else {
                    object = lib;
                    broken = false;
                }
            }
        }
        
        public String getReference() {
            return property;
        }
        
        /**
         * only applicable to TYPE_JAR
         * 
         * @return
         */
        public String getSourceReference() {
            if (property == null || !property.startsWith(REF_START)) {
                return null;
            }            
            return SOURCE_START + property.substring(REF_START_INDEX);
        }
        
        public String getSourceProperty() {
            if (property == null || !property.startsWith(REF_START)) {
                return null;
            }
            final String sourceRef = getSourceReference();
            if (sourceRef == null) {
                return null;
            }
            return sourceRef.substring(2, sourceRef.length() - 1);
        }
        
        /**
         * only applicable to TYPE_JAR
         * 
         * @return
         */
        public String getJavadocReference() {
            if (property == null || !property.startsWith(REF_START)) {
                return null;
            }            
            return JAVADOC_START + property.substring(REF_START_INDEX);
        }
        
        public String getJavadocProperty() {
            if (property == null || !property.startsWith(REF_START)) {
                return null;
            }
            final String javadocRef = getJavadocReference();
            if (javadocRef == null) {
                return null;
            }
            return javadocRef.substring(2, javadocRef.length() - 1);
        }
        
        public boolean canEdit () {            
            if (isBroken()) {
                //Broken item cannot be edited
                return false;
            }
            if (getType() == TYPE_JAR) {
                //Jar can be edited only for ide created reference
                if (property == null) {
                    //Just added item, allow editing
                    return true;
                }                
                return getSourceReference() != null && getJavadocReference() != null;
            }
            else if (getType() == TYPE_LIBRARY) {
                //Library can be edited
                return true;
            }
            //Otherwise: project, classpath - cannot be edited 
            return false;
        }
        
        /**
         * only applicable to TYPE_JAR
         * 
         * @return
         */
        public String getSourceFilePath() {
            return sourceFile;
        }
        
        /**
         * only applicable to TYPE_JAR
         * 
         * @return
         */
        public String getJavadocFilePath() {
            return javadocFile;
        }
        
        /**
         * only applicable to TYPE_JAR
         * 
         * @return
         */
        public void setJavadocFilePath(String javadoc) {
            javadocFile = javadoc;
        }
        
        /**
         * only applicable to TYPE_JAR
         * 
         * @return
         */
        public void setSourceFilePath(String source) {
            sourceFile = source;
        }
        
        public void setInitialSourceAndJavadoc(File source, File javadoc) {
            initialSourceFile = source;
            initialJavadocFile = javadoc;
            if (source == null) {
                sourceFile = null;
            } else {
                sourceFile = source.getPath();
            }
            if (javadoc == null) {
                javadocFile = null;
            } else {
                javadocFile = javadoc.getPath();
            }
        }
        
        public boolean hasChangedSource() {
            if ((initialSourceFile == null) != (sourceFile == null)) {
                return true;
            }
            if (initialSourceFile != null && sourceFile != null) {
                
                return ! initialSourceFile.getPath().equals(sourceFile);
            }
            return true;
        }
        
        public boolean hasChangedJavadoc() {
            if ((initialJavadocFile == null) != (javadocFile == null)) {
                return true;
            }
            if (initialJavadocFile != null && javadocFile != null) {
                return ! initialJavadocFile.getPath().equals(javadocFile);
            }
            return true;
        }
        
        
        public boolean isBroken() {
            return this.broken;
        }
                        
        @Override
        public int hashCode() {
        
            int hash = getType();

            if ( this.broken ) {
                return 42;
            }
            
            switch ( getType() ) {
                case TYPE_ARTIFACT:
                    hash += getArtifact().getType().hashCode();                
                    hash += getArtifact().getScriptLocation().hashCode();
                    hash += getArtifactURI().hashCode();
                    break;
                case TYPE_CLASSPATH:
                    hash += property.hashCode();
                    break;
                default:
                    hash += object.hashCode();
            }

            return hash;
        }
    
        @Override
        public boolean equals( Object itemObject ) {

            if ( !( itemObject instanceof Item ) ) {
                return false;
            }
            
            Item item = (Item)itemObject;

            if ( getType() != item.getType() ) {
                return false;
            }
            
            if ( isBroken() != item.isBroken() ) {
                return false;
            }
            
            if ( isBroken() ) {
                return getReference().equals( item.getReference() );
            }

            switch ( getType() ) {
                case TYPE_ARTIFACT:
                    if ( getArtifact().getType() != item.getArtifact().getType() ) {
                        return false;
                    }

                    if ( !getArtifact().getScriptLocation().equals( item.getArtifact().getScriptLocation() ) ) {
                        return false;
                    }

                    if ( !getArtifactURI().equals( item.getArtifactURI() ) ) {
                        return false;
                    }
                    return true;
                case TYPE_CLASSPATH:
                    return property.equals( item.property );
                default:
                    return object.equals( item.object );
            }

        }
                
    }
    
    /**
     * Converts the ant reference to the name of the referenced property
     * @param ant reference
     * @param the name of the referenced property
     */ 
    public static String getAntPropertyName( String property ) {
        if ( property != null && 
             property.startsWith( "${" ) && // NOI18N
             property.endsWith( "}" ) ) { // NOI18N
            return property.substring( 2, property.length() - 1 ); 
        }
        else {
            return property;
        }
    }

    /**
     * Returns library name if given property represents library reference 
     * otherwise return null.
     * 
     * @param property property to test
     * @return library name or null
     */
    public static String getLibraryNameFromReference(String property) {
        if (!isLibrary(property)) {
            return null;
        }
        return property.substring(LIBRARY_PREFIX.length(), property.lastIndexOf('.')); //NOI18N
    }
}
