/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.netbeans.modules.btrace.project;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ant.AntArtifact;
import org.netbeans.api.project.ant.AntBuildExtender;
import org.netbeans.modules.btrace.project.classpath.BTraceProjectClassPathExtender;
import org.netbeans.modules.btrace.project.classpath.BTraceProjectClassPathModifier;
import org.netbeans.modules.btrace.project.classpath.ClassPathProviderImpl;
import org.netbeans.modules.btrace.project.java.api.common.SourceRoots;
import org.netbeans.modules.btrace.project.java.api.common.ant.UpdateHelper;
import org.netbeans.modules.btrace.project.java.api.common.ant.UpdateImplementation;
import org.netbeans.modules.btrace.project.java.api.common.queries.QuerySupport;
import org.netbeans.modules.btrace.project.ui.BTraceLogicalViewProvider;
import org.netbeans.modules.btrace.project.ui.customizer.BTraceProjectProperties;
import org.netbeans.modules.btrace.project.ui.customizer.CustomizerProviderImpl;
import org.netbeans.spi.java.project.support.ExtraSourceJavadocSupport;
import org.netbeans.spi.java.project.support.LookupMergerSupport;
import org.netbeans.spi.java.project.support.ui.BrokenReferencesSupport;
import org.netbeans.spi.project.AuxiliaryConfiguration;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.project.support.ant.AntProjectListener;
import org.netbeans.spi.project.support.ant.GeneratedFilesHelper;
import org.netbeans.spi.project.support.ant.ReferenceHelper;
import org.openide.util.Lookup;
import org.openide.util.Utilities;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.SubprojectProvider;
import org.netbeans.spi.project.ant.AntArtifactProvider;
import org.netbeans.spi.project.ant.AntBuildExtenderFactory;
import org.netbeans.spi.project.ant.AntBuildExtenderImplementation;
import org.netbeans.spi.project.support.LookupProviderSupport;
import org.netbeans.spi.project.support.ant.AntProjectEvent;
import org.netbeans.spi.project.support.ant.EditableProperties;
import org.netbeans.spi.project.support.ant.ProjectXmlSavedHook;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.netbeans.spi.project.ui.PrivilegedTemplates;
import org.netbeans.spi.project.ui.ProjectOpenedHook;
import org.netbeans.spi.project.ui.RecommendedTemplates;
import org.netbeans.spi.project.ui.support.UILookupMergerSupport;
import org.netbeans.spi.queries.FileEncodingQueryImplementation;
import org.openide.DialogDisplayer;
import org.openide.ErrorManager;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import org.netbeans.modules.btrace.project.queries.BinaryForSourceQueryImpl;

/**
 * Represents one BTrace project
 * Based on BTraceProject
 * @author Jesse Glick
 * @author Jaroslav Bachorik
  */
public class BTraceProject implements Project, AntProjectListener {
    
    private static final Icon BTRACE_PROJECT_ICON = new ImageIcon(Utilities.loadImage("org/netbeans/modules/btrace/project/ui/resources/j2seProject.png")); // NOI18N
    private static final Logger LOG = Logger.getLogger(BTraceProject.class.getName());

    private final AuxiliaryConfiguration aux;
    private final AntProjectHelper helper;
    private final PropertyEvaluator eval;
    private final ReferenceHelper refHelper;
    private final GeneratedFilesHelper genFilesHelper;
    private final Lookup lookup;
    private final UpdateHelper updateHelper;
    private SourceRoots sourceRoots;
    private final ClassPathProviderImpl cpProvider;
    private final BTraceProjectClassPathModifier cpMod;

    private AntBuildExtender buildExtender;

    BTraceProject(AntProjectHelper helper) throws IOException {
        this.helper = helper;
        eval = createEvaluator();
        aux = helper.createAuxiliaryConfiguration();
        for (int v = 4; v < 10; v++) {
            if (aux.getConfigurationFragment("data", "http://www.netbeans.org/ns/btrace-project/" + v, true) != null) { // NOI18N
                throw Exceptions.attachLocalizedMessage(new IOException("too new"), // NOI18N
                        NbBundle.getMessage(BTraceProject.class, "BTraceProject.too_new", FileUtil.getFileDisplayName(helper.getProjectDirectory())));
            }
        }
        refHelper = new ReferenceHelper(helper, aux, eval);
        buildExtender = AntBuildExtenderFactory.createAntExtender(new BTraceExtenderImplementation());
    /// TODO replace this GeneratedFilesHelper with the default one when fixing #101710
        genFilesHelper = new GeneratedFilesHelper(helper, buildExtender);

        UpdateImplementation updateProject = new UpdateProjectImpl(this, helper, aux);
        this.updateHelper = new UpdateHelper(updateProject, helper);

        this.cpProvider = new ClassPathProviderImpl(this.helper, evaluator(), getSourceRoots()); //Does not use APH to get/put properties/cfgdata
        this.cpMod = new BTraceProjectClassPathModifier(this, this.updateHelper, eval, refHelper);
        
        final BTraceActionProvider actionProvider = new BTraceActionProvider( this, this.updateHelper );
        lookup = createLookup(aux, actionProvider);
        actionProvider.startFSListener();
        
        helper.addAntProjectListener(this);
    }

    /**
     * Returns the project directory
     * @return the directory the project is located in
     */
    public FileObject getProjectDirectory() {
        return helper.getProjectDirectory();
    }

    public String toString() {
        return "BTraceProject[" + FileUtil.getFileDisplayName(getProjectDirectory()) + "]"; // NOI18N
    }
    
    private PropertyEvaluator createEvaluator() {
        PropertyEvaluator baseEval1 = PropertyUtils.sequentialPropertyEvaluator(
                helper.getStockPropertyPreprovider(),
                helper.getPropertyProvider(AntProjectHelper.PRIVATE_PROPERTIES_PATH));
        return PropertyUtils.sequentialPropertyEvaluator(
                helper.getStockPropertyPreprovider(),
                helper.getPropertyProvider(AntProjectHelper.PRIVATE_PROPERTIES_PATH),
                helper.getProjectLibrariesPropertyProvider(),
                PropertyUtils.userPropertiesProvider(baseEval1, "user.properties.file", FileUtil.toFile(getProjectDirectory())), // NOI18N
                helper.getPropertyProvider(AntProjectHelper.PROJECT_PROPERTIES_PATH));
    }
        
    public PropertyEvaluator evaluator() {
        return eval;
    }

    public ReferenceHelper getReferenceHelper () {
        return this.refHelper;
    }
    
    public Lookup getLookup() {
        return lookup;
    }
    
    public AntProjectHelper getAntProjectHelper() {
        return helper;
    }

    private Lookup createLookup(final AuxiliaryConfiguration aux,
            final ActionProvider actionProvider) {
        final SubprojectProvider spp = refHelper.createSubprojectProvider();        
        FileEncodingQueryImplementation encodingQuery = QuerySupport.createFileEncodingQuery(evaluator(), BTraceProjectProperties.SOURCE_ENCODING);
        final Lookup base = Lookups.fixed(new Object[] {
            BTraceProject.this,
            new Info(),
            aux,
            helper.createCacheDirectoryProvider(),
            spp,
            actionProvider,
            new BTraceLogicalViewProvider(this, this.updateHelper, evaluator(), spp, refHelper),
            // new J2SECustomizerProvider(this, this.updateHelper, evaluator(), refHelper),
            new CustomizerProviderImpl(this, this.updateHelper, evaluator(), refHelper, this.genFilesHelper),        
            new ClassPathProviderMerger(cpProvider),
            QuerySupport.createCompiledSourceForBinaryQuery(helper, evaluator(), getSourceRoots(), null),
            QuerySupport.createJavadocForBinaryQuery(helper, evaluator()),
            new AntArtifactProviderImpl(),
            new ProjectXmlSavedHookImpl(),
            UILookupMergerSupport.createProjectOpenHookMerger(new ProjectOpenedHookImpl()),
            QuerySupport.createSourceLevelQuery(evaluator()),
            new BTraceSources (this.helper, evaluator(), getSourceRoots(), null),
            QuerySupport.createSharabilityQuery(helper, evaluator(), getSourceRoots(), null),
            QuerySupport.createFileBuiltQuery(helper, evaluator(), getSourceRoots(), null),
            new RecommendedTemplatesImpl (this.updateHelper),
            new BTraceProjectClassPathExtender(cpMod),
            buildExtender,
            cpMod,
            this, // never cast an externally obtained Project to BTraceProject - use lookup instead
            new BTraceProjectOperations(this),
            UILookupMergerSupport.createPrivilegedTemplatesMerger(),
            UILookupMergerSupport.createRecommendedTemplatesMerger(),
            LookupProviderSupport.createSourcesMerger(),
            encodingQuery,
            QuerySupport.createTemplateAttributesProvider(helper, encodingQuery),
            ExtraSourceJavadocSupport.createExtraSourceQueryImplementation(this, helper, eval),
            LookupMergerSupport.createSFBLookupMerger(),
            ExtraSourceJavadocSupport.createExtraJavadocQueryImplementation(this, helper, eval),
            LookupMergerSupport.createJFBLookupMerger(),
            new BinaryForSourceQueryImpl(this.sourceRoots, null, this.helper, this.eval) //Does not use APH to get/put properties/cfgdata
        });
        return LookupProviderSupport.createCompositeLookup(base, "Projects/org-netbeans-modules-java-BTraceProject/Lookup"); //NOI18N
    }
    
    public UpdateHelper getUpdateHelper() {
        return this.updateHelper;
    }
    
    public ClassPathProviderImpl getClassPathProvider () {
        return this.cpProvider;
    }
    
    public BTraceProjectClassPathModifier getProjectClassPathModifier () {
        return this.cpMod;
    }

    public void configurationXmlChanged(AntProjectEvent ev) {
        if (ev.getPath().equals(AntProjectHelper.PROJECT_XML_PATH)) {
            // Could be various kinds of changes, but name & displayName might have changed.
            Info info = (Info)getLookup().lookup(ProjectInformation.class);
            info.firePropertyChange(ProjectInformation.PROP_NAME);
            info.firePropertyChange(ProjectInformation.PROP_DISPLAY_NAME);
        }
    }

    public void propertiesChanged(AntProjectEvent ev) {
        // currently ignored (probably better to listen to evaluator() if you need to)
    }
    
    // Package private methods -------------------------------------------------
    
    /**
     * Returns the source roots of this project
     * @return project's source roots
     */
    public synchronized SourceRoots getSourceRoots() {        
        if (this.sourceRoots == null) { //Local caching, no project metadata access
            this.sourceRoots = SourceRoots.create(updateHelper, evaluator(), getReferenceHelper(),
                    BTraceProjectType.PROJECT_CONFIGURATION_NAMESPACE, "source-roots", false, "src.{0}{1}.dir"); //NOI18N
       }
        return this.sourceRoots;
    }
        
    File getTestClassesDirectory() {
        String testClassesDir = evaluator().getProperty(BTraceProjectProperties.BUILD_TEST_CLASSES_DIR);
        if (testClassesDir == null) {
            return null;
        }
        return helper.resolveFile(testClassesDir);
    }
    
    // Currently unused (but see #47230):
    /** Store configured project name. */
    public void setName(final String name) {
        ProjectManager.mutex().writeAccess(new Mutex.Action<Void>() {
            public Void run() {
                Element data = helper.getPrimaryConfigurationData(true);
                // XXX replace by XMLUtil when that has findElement, findText, etc.
                NodeList nl = data.getElementsByTagNameNS(BTraceProjectType.PROJECT_CONFIGURATION_NAMESPACE, "name");
                Element nameEl;
                if (nl.getLength() == 1) {
                    nameEl = (Element) nl.item(0);
                    NodeList deadKids = nameEl.getChildNodes();
                    while (deadKids.getLength() > 0) {
                        nameEl.removeChild(deadKids.item(0));
                    }
                } else {
                    nameEl = data.getOwnerDocument().createElementNS(BTraceProjectType.PROJECT_CONFIGURATION_NAMESPACE, "name");
                    data.insertBefore(nameEl, /* OK if null */data.getChildNodes().item(0));
                }
                nameEl.appendChild(data.getOwnerDocument().createTextNode(name));
                helper.putPrimaryConfigurationData(data, true);
                return null;
            }
        });
    }




    // Private innerclasses ----------------------------------------------------
    //when #110886 gets implemented, this class is obsolete
    private final class Info implements ProjectInformation {
        
        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        private WeakReference<String> cachedName = null;
        
        Info() {}
        
        void firePropertyChange(String prop) {
            pcs.firePropertyChange(prop, null, null);
            synchronized (pcs) {
                cachedName = null;
            }
        }
        
        public String getName() {
            return PropertyUtils.getUsablePropertyName(getDisplayName());
        }
        
        public String getDisplayName() {
            synchronized (pcs) {
                if (cachedName != null) {
                    String dn = cachedName.get();
                    if (dn != null) {
                        return dn;
                    }
                }
            }
            String dn = ProjectManager.mutex().readAccess(new Mutex.Action<String>() {
                public String run() {
                    Element data = updateHelper.getPrimaryConfigurationData(true);
                    // XXX replace by XMLUtil when that has findElement, findText, etc.
                    NodeList nl = data.getElementsByTagNameNS(BTraceProjectType.PROJECT_CONFIGURATION_NAMESPACE, "name"); // NOI18N
                    if (nl.getLength() == 1) {
                        nl = nl.item(0).getChildNodes();
                        if (nl.getLength() == 1 && nl.item(0).getNodeType() == Node.TEXT_NODE) {
                            return ((Text) nl.item(0)).getNodeValue();
                        }
                    }
                    return "???"; // NOI18N
                }
            });
            synchronized (pcs) {
                cachedName = new WeakReference<String>(dn);
            }
            return dn;
        }
        
        public Icon getIcon() {
            return BTRACE_PROJECT_ICON;
        }
        
        public Project getProject() {
            return BTraceProject.this;
        }
        
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            pcs.addPropertyChangeListener(listener);
        }
        
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            pcs.removePropertyChangeListener(listener);
        }
        
    }
    
    private final class ProjectXmlSavedHookImpl extends ProjectXmlSavedHook {
        
        ProjectXmlSavedHookImpl() {}
        
        protected void projectXmlSaved() throws IOException {
            //May be called by {@link AuxiliaryConfiguration#putConfigurationFragment}
            //which didn't affect the btraceproject 
            if (updateHelper.isCurrent()) {
                //Refresh build-impl.xml only for btraceproject/1
                genFilesHelper.refreshBuildScript(
                    GeneratedFilesHelper.BUILD_IMPL_XML_PATH,
                    BTraceProject.class.getResource("resources/build-impl.xsl"),
                    false);
                genFilesHelper.refreshBuildScript(
                    BTraceProjectUtil.getBuildXmlName(BTraceProject.this),
                    BTraceProject.class.getResource("resources/build.xsl"),
                    false);
            }
        }    
    }
    
    private final class ProjectOpenedHookImpl extends ProjectOpenedHook {
        
        ProjectOpenedHookImpl() {}
        private static final String JAX_RPC_NAMESPACE="http://www.netbeans.org/ns/j2se-project/jax-rpc"; //NOI18N
        private static final String JAX_RPC_CLIENTS="web-service-clients"; //NOI18N
        private static final String JAX_RPC_CLIENT="web-service-client"; //NOI18N
        
        protected void projectOpened() {
            // Check up on build scripts.
            try {
                if (updateHelper.isCurrent()) {
                    //Refresh build-impl.xml only for btraceproject/1
                    genFilesHelper.refreshBuildScript(
                        GeneratedFilesHelper.BUILD_IMPL_XML_PATH,
                        BTraceProject.class.getResource("resources/build-impl.xsl"),
                        true);
                    genFilesHelper.refreshBuildScript(
                        BTraceProjectUtil.getBuildXmlName(BTraceProject.this),
                        BTraceProject.class.getResource("resources/build.xsl"),
                        true);
                }                
            } catch (IOException e) {
                ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, e);
            }
            
            // register project's classpaths to GlobalPathRegistry            
            GlobalPathRegistry.getDefault().register(ClassPath.BOOT, cpProvider.getProjectClassPaths(ClassPath.BOOT));
            GlobalPathRegistry.getDefault().register(ClassPath.SOURCE, cpProvider.getProjectClassPaths(ClassPath.SOURCE));
            GlobalPathRegistry.getDefault().register(ClassPath.COMPILE, cpProvider.getProjectClassPaths(ClassPath.COMPILE));

//            //register updater of main.class
//            //the updater is active only on the opened projects
//	    mainClassUpdater = new MainClassUpdater (BTraceProject.this, eval, updateHelper,
//                    cpProvider.getProjectClassPaths(ClassPath.SOURCE)[0], BTraceProjectProperties.MAIN_CLASS);

            // Make it easier to run headless builds on the same machine at least.
            try {
                getProjectDirectory().getFileSystem().runAtomicAction(new FileSystem.AtomicAction() {
                    public void run () throws IOException {
                        ProjectManager.mutex().writeAccess(new Mutex.Action<Void>() {
                            public Void run() {
                                EditableProperties ep = updateHelper.getProperties(AntProjectHelper.PRIVATE_PROPERTIES_PATH);
                                File buildProperties = new File(System.getProperty("netbeans.user"), "build.properties"); // NOI18N
                                ep.setProperty("user.properties.file", buildProperties.getAbsolutePath()); //NOI18N

                                // set jaxws.endorsed.dir property (for endorsed mechanism to be used with wsimport, wsgen)
                                setJaxWsEndorsedDirProperty(ep);

                                // move web-service-clients one level up from in project.xml
                                // WS should be part of auxiliary configuration
                                Element data = helper.getPrimaryConfigurationData(true);
                                NodeList nodes = data.getElementsByTagName(JAX_RPC_CLIENTS);
                                if(nodes.getLength() > 0) {                        
                                    Element oldJaxRpcClients = (Element) nodes.item(0);
                                    Document doc = createNewDocument();
                                    Element newJaxRpcClients = doc.createElementNS(JAX_RPC_NAMESPACE, JAX_RPC_CLIENTS);
                                    NodeList childNodes = oldJaxRpcClients.getElementsByTagName(JAX_RPC_CLIENT);
                                    for (int i=0;i<childNodes.getLength();i++) {                            
                                        Element oldJaxRpcClient = (Element) childNodes.item(i);
                                        Element newJaxRpcClient = doc.createElementNS(JAX_RPC_NAMESPACE, JAX_RPC_CLIENT);
                                        NodeList nodeProps = oldJaxRpcClient.getChildNodes();
                                        for (int j=0;j<nodeProps.getLength();j++) {
                                            Node n = nodeProps.item(j);
                                            if (n instanceof Element) {
                                                Element oldProp = (Element) n;
                                                Element newProp = doc.createElementNS(JAX_RPC_NAMESPACE, oldProp.getLocalName());
                                                String text = oldProp.getTextContent();
                                                newProp.setTextContent(text);
                                                newJaxRpcClient.appendChild(newProp);
                                            }
                                        }
                                        newJaxRpcClients.appendChild(newJaxRpcClient);
                                    }
                                    aux.putConfigurationFragment(newJaxRpcClients, true);
                                    data.removeChild(oldJaxRpcClients);
                                    helper.putPrimaryConfigurationData(data, true);
                                }

                                updateHelper.putProperties(AntProjectHelper.PRIVATE_PROPERTIES_PATH, ep);
                                ep = helper.getProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH);
                                if (!ep.containsKey(BTraceProjectProperties.INCLUDES)) {
                                    ep.setProperty(BTraceProjectProperties.INCLUDES, "**"); // NOI18N
                                }
                                if (!ep.containsKey(BTraceProjectProperties.EXCLUDES)) {
                                    ep.setProperty(BTraceProjectProperties.EXCLUDES, ""); // NOI18N
                                }
                                helper.putProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH, ep);
                                try {
                                    ProjectManager.getDefault().saveProject(BTraceProject.this);
                                } catch (IOException e) {
                                    //#91398 provide a better error message in case of read-only location of project.
                                    if (!BTraceProject.this.getProjectDirectory().canWrite()) {
                                        NotifyDescriptor nd = new NotifyDescriptor.Message(NbBundle.getMessage(BTraceProject.class, "ERR_ProjectReadOnly",
                                                BTraceProject.this.getProjectDirectory().getName()));
                                        DialogDisplayer.getDefault().notify(nd);
                                    } else {
                                        ErrorManager.getDefault().notify(e);
                                    }
                                }
                                return null;
                            }
                        });
                    }
                });            
            } catch (IOException e) {
                Exceptions.printStackTrace(e);
            }
            BTraceLogicalViewProvider physicalViewProvider = getLookup().lookup(BTraceLogicalViewProvider.class);
            if (physicalViewProvider != null &&  physicalViewProvider.hasBrokenLinks()) {   
                BrokenReferencesSupport.showAlert();
            }
            String prop = eval.getProperty(BTraceProjectProperties.SOURCE_ENCODING);
            if (prop != null) {
                try {
                    Charset c = Charset.forName(prop);
                } catch (IllegalCharsetNameException e) {
                    //Broken property, log & ignore
                    LOG.warning("Illegal charset: " + prop+ " in project: " + FileUtil.getFileDisplayName(getProjectDirectory())); //NOI18N
                }
                catch (UnsupportedCharsetException e) {
                    //todo: Needs UI notification like broken references.
                    LOG.warning("Unsupported charset: " + prop+ " in project: " + FileUtil.getFileDisplayName(getProjectDirectory())); //NOI18N
                }
            }
        }
        
        protected void projectClosed() {
            // just do if the whole project was not deleted...
            if (getProjectDirectory().isValid()) {
                // Probably unnecessary, but just in case:
                try {
                    ProjectManager.getDefault().saveProject(BTraceProject.this);
                } catch (IOException e) {
                    if (!BTraceProject.this.getProjectDirectory().canWrite()) {
                        // #91398 - ignore, we already reported on project open. 
                        // not counting with someone setting the ro flag while the project is opened.
                    } else {
                        ErrorManager.getDefault().notify(e);
                    }
                }
            }
            
            // unregister project's classpaths to GlobalPathRegistry            
            GlobalPathRegistry.getDefault().unregister(ClassPath.BOOT, cpProvider.getProjectClassPaths(ClassPath.BOOT));
            GlobalPathRegistry.getDefault().unregister(ClassPath.SOURCE, cpProvider.getProjectClassPaths(ClassPath.SOURCE));
            GlobalPathRegistry.getDefault().unregister(ClassPath.COMPILE, cpProvider.getProjectClassPaths(ClassPath.COMPILE));
//            if (mainClassUpdater != null) {
//                mainClassUpdater.unregister ();
//                mainClassUpdater = null;
//            }
        }
        
    }
    
    /**
     * Exports the main JAR as an official build product for use from other scripts.
     * The type of the artifact will be {@link AntArtifact#TYPE_JAR}.
     */
    private final class AntArtifactProviderImpl implements AntArtifactProvider {

        public AntArtifact[] getBuildArtifacts() {
            return new AntArtifact[] {
                new BTraceProjectAntArtifact (BTraceProject.this,
                        JavaProjectConstants.ARTIFACT_TYPE_JAR,
                        "dist.jar", "jar", "clean"), // NOI18N
            };
        }

    }
    
    private static final class RecommendedTemplatesImpl implements RecommendedTemplates, PrivilegedTemplates {
        RecommendedTemplatesImpl (UpdateHelper helper) {
            this.helper = helper;
        }
        
        private UpdateHelper helper;
        
        // List of primarily supported templates
        
        private static final String[] APPLICATION_TYPES = new String[] {     
            "btrace-scripting"      // NOI18N
        };
//            "java-classes",         // NOI18N
//            "java-main-class",      // NOI18N
//            "java-forms",           // NOI18N
//            "gui-java-application", // NOI18N
//            "java-beans",           // NOI18N
//            "persistence",          // NOI18N
//            "oasis-XML-catalogs",   // NOI18N
//            "XML",                  // NOI18N
//            "ant-script",           // NOI18N
//            "ant-task",             // NOI18N
//            "web-service-clients",  // NOI18N
//            "wsdl",                 // NOI18N
//            // "servlet-types",     // NOI18N
//            // "web-types",         // NOI18N
//            "junit",                // NOI18N
//            // "MIDP",              // NOI18N
//            "simple-files"          // NOI18N        
//        };
        
        private static final String[] PRIVILEGED_NAMES = new String[] {
            "Templates/BTrace_Script/Script.java"
        };
        
        public String[] getRecommendedTypes() {
            return APPLICATION_TYPES;
        }
        
        public String[] getPrivilegedTemplates() {
            return PRIVILEGED_NAMES;
        }
        
    }
    
//    private static final class J2SEPropertyEvaluatorImpl implements J2SEPropertyEvaluator {
//        private PropertyEvaluator evaluator;
//        public J2SEPropertyEvaluatorImpl (PropertyEvaluator eval) {
//            evaluator = eval;
//        }
//        public PropertyEvaluator evaluator() {
//            return evaluator;
//        }
//    }

    private class BTraceExtenderImplementation implements AntBuildExtenderImplementation {
        //add targets here as required by the external plugins..
        public List<String> getExtensibleTargets() {
            String[] targets = new String[] {
                "-do-init", "-init-check", "-post-clean", "jar", "-pre-pre-compile","-do-compile","-do-compile-single" //NOI18N
            };
            return Arrays.asList(targets);
        }

        public Project getOwningProject() {
            return BTraceProject.this;
        }

    }
    
    private static final String ENDORSED_DIR_PROPERTY="jaxws.endorsed.dir"; //NOI18N
    
    /** Set jaxws.endorsed.dir property for wsimport, wsgen tasks
     *  to specify jvmarg value : -Djava.endorsed.dirs=${jaxws.endorsed.dir}"
     */
    public static void setJaxWsEndorsedDirProperty(EditableProperties ep) {
        String oldJaxWsEndorsedDirs = ep.getProperty(ENDORSED_DIR_PROPERTY);
        String javaVersion = System.getProperty("java.specification.version"); //NOI18N
        if ("1.6".equals(javaVersion)) { //NOI18N
            String jaxWsEndorsedDirs = getJaxWsApiDir();
            if (jaxWsEndorsedDirs!=null && !jaxWsEndorsedDirs.equals(oldJaxWsEndorsedDirs))
                ep.setProperty(ENDORSED_DIR_PROPERTY, jaxWsEndorsedDirs);
        } else {
            if (oldJaxWsEndorsedDirs!=null) {
                ep.remove(ENDORSED_DIR_PROPERTY);
            }
        }
    }
    
    private static String getJaxWsApiDir() {
        File file = InstalledFileLocator.getDefault().locate("modules/ext/jaxws21/api/jaxws-api.jar", null, false); // NOI18N
        if (file!=null) {
            return file.getParent();
        }
        return null;
    }
    
    private static final DocumentBuilder db;
    static {
        try {
            db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new AssertionError(e);
        }
    }
    private static Document createNewDocument() {
        // #50198: for thread safety, use a separate document.
        // Using XMLUtil.createDocument is much too slow.
        synchronized (db) {
            return db.newDocument();
        }
    }
}
