/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.compiler;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import io.btrace.core.Messages;
import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.OnError;
import io.btrace.core.annotations.OnExit;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.Sampled;
import io.btrace.core.extensions.Permission;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * This class tree visitor validates a BTrace program's ClassTree.
 *
 * @author A. Sundararajan
 */
public class VerifierVisitor extends TreeScanner<Void, Void> {
  private static final String ON_ERROR_TYPE = OnError.class.getName();
  private static final String ON_EXIT_TYPE = OnExit.class.getName();
  private static final String THROWABLE_TYPE = Throwable.class.getName();

  private final Verifier verifier;
  private String className;
  private String fqn;
  private boolean insideMethod;

  private boolean shortSyntax = false;
  // Legacy service type mirrors removed. Only Extension-based checks remain.
  private TypeMirror extensionTm = null;

  private boolean isInAnnotation = false;

  private final Set<String> eventFieldNames = new HashSet<>();
  private final Set<String> servicePackages = new HashSet<>();
  private final Set<String> injectedServiceTypes = new HashSet<>();

  /** Permissions required by extensions used in this probe */
  private final EnumSet<Permission> requiredPermissions = EnumSet.noneOf(Permission.class);

  // Probe-declared permissions removed; permissions enforced via manifest/runtime grants.

  private final TreeScanner<Void, Void> jfrFieldNameCollector =
      new TreeScanner<Void, Void>() {
        @Override
        public Void visitAnnotation(AnnotationTree node, Void o) {
          String annType = node.getAnnotationType().toString();
          if (annType.endsWith("Event")) {
            for (ExpressionTree et : node.getArguments()) {
              AssignmentTree t = (AssignmentTree) et;
              String name = t.getVariable().toString();
              if (name.equals("fields")) {
                processEventFields(t);
              }
            }
          }
          return super.visitAnnotation(node, o);
        }
      };

  public VerifierVisitor(Verifier verifier, Element clzElement) {
    this.verifier = verifier;
    // Legacy service types are no longer referenced at compile time.
    TypeElement extensionElement =
        verifier.getElementUtils().getTypeElement("io.btrace.core.extensions.Extension");
    if (extensionElement != null) {
      extensionTm = extensionElement.asType();
    }
  }

  @Override
  public Void visitMethodInvocation(MethodInvocationTree node, Void v) {
    Element e = getElement(node);
    if (e != null
        && (e.getKind() == ElementKind.METHOD || e.getKind() == ElementKind.CONSTRUCTOR)) {
      String name = e.getSimpleName().toString();

      // allow constructor calls
      if (name.equals("<init>")) {
        return super.visitMethodInvocation(node, v);
      }

      // The element enclosing a METHOD/CONSTRUCTOR is its declaring type, which is always a
      // TypeElement (class, interface, enum, annotation, or record). The previous do-while never
      // advanced `e`, so it spun forever whenever the declaring type was neither a CLASS nor an
      // INTERFACE (e.g. an enum-declared method such as TimeUnit.NANOSECONDS.toMillis()).
      Element enclosing = e.getEnclosingElement();
      TypeElement parent = (enclosing instanceof TypeElement) ? (TypeElement) enclosing : null;

      if (parent != null) {
        TypeMirror tm = parent.asType();
        String typeName = tm.toString();

        if (isSameClass(typeName)) {
          return super.visitMethodInvocation(node, v);
        }
        if (isBTraceClass(typeName)) {
          if (typeName.contains("BTraceUtils")) {
            if (e.getSimpleName().contentEquals("setEventField")) {
              String nameValue = node.getArguments().get(1).toString();
              if (!eventFieldNames.contains(nameValue)) {
                reportError("jfr.event.invalid.field", node.getArguments().get(1));
              }
            }
          }
          return super.visitMethodInvocation(node, v);
        }
        // Allow extension APIs and injected service-derived types
        // Allow direct calls on injected extensions as well
        if (extensionTm != null && verifier.getTypeUtils().isSubtype(tm, extensionTm)) {
          return super.visitMethodInvocation(node, v);
        }
        // Also allow calls on any field types annotated with @Injected
        // and on types in the same package/sub-packages as injected services
        if (injectedServiceTypes.contains(typeName) || isServiceDerivedType(typeName)) {
          return super.visitMethodInvocation(node, v);
        }
      }
    }
    reportError("no.method.calls", node);
    return super.visitMethodInvocation(node, v);
  }

  private boolean isServiceDerivedType(String typeName) {
    // Check if this type is in the same package as any registered service
    // This makes the verifier cooperate with the extension system automatically
    for (String servicePackage : servicePackages) {
      if (typeName.startsWith(servicePackage + ".")) {
        return true;
      }
    }
    return false;
  }

  private boolean isSameClass(String typeName) {
    return fqn.equals(typeName);
  }

  private boolean isBTraceClass(String typeName) {
    return typeName.equals("io.btrace.core.BTraceUtils")
        || typeName.startsWith("io.btrace.core.BTraceUtils.")
        || typeName.equals("io.btrace.BTrace");
  }

  @Override
  public Void visitAssert(AssertTree node, Void v) {
    reportError("no.asserts", node);
    return super.visitAssert(node, v);
  }

  @Override
  public Void visitAssignment(AssignmentTree node, Void v) {
    checkLValue(node.getVariable());
    return super.visitAssignment(node, v);
  }

  @Override
  public Void visitCompoundAssignment(CompoundAssignmentTree node, Void v) {
    checkLValue(node.getVariable());
    return super.visitCompoundAssignment(node, v);
  }

  @Override
  public Void visitCatch(CatchTree node, Void v) {
    reportError("no.catch", node);
    return super.visitCatch(node, v);
  }

  @Override
  public Void visitClass(ClassTree node, Void v) {
    // check for local class
    if (insideMethod) {
      reportError("no.local.class", node);
    }

    // check for short BTrace syntax (inferring redundant access qualifiers)
    Set<Modifier> mods = node.getModifiers().getFlags();
    if (!mods.contains(Modifier.PRIVATE)
        && !mods.contains(Modifier.PROTECTED)
        && !mods.contains(Modifier.PUBLIC)) {
      shortSyntax = true;
    }
    // check for inner and nested class
    List<? extends Tree> members = node.getMembers();
    for (Tree m : members) {
      if (m.getKind() == Tree.Kind.CLASS) {
        reportError("no.nested.class", m);
      }

      if (m.getKind() == Tree.Kind.VARIABLE) {
        VariableTree vt = (VariableTree) m;
        boolean isStatic = isStatic(vt.getModifiers().getFlags());
        if (shortSyntax) {
          if (isStatic) {
            reportError("no.static.variables", m);
          }
        } else {
          if (!isStatic) {
            reportError("no.instance.variables", m);
          }
        }
      }
    }

    // should extend java.lang.Object
    Tree superClass = node.getExtendsClause();
    if (superClass != null) {
      String name = superClass.toString();
      if (!name.equals("Object") && !name.equals("java.lang.Object")) {
        reportError("object.superclass.required", superClass);
      }
    }

    // should not implement interfaces
    List<? extends Tree> interfaces = node.getImplementsClause();
    if (interfaces != null && interfaces.size() > 0) {
      reportError("no.interface.implementation", interfaces.get(0));
    }

    ModifiersTree mt = node.getModifiers();
    if (!shortSyntax && !isPublic(mt.getFlags())) {
      reportError("class.should.be.public", node);
    }
    List<? extends AnnotationTree> anno = mt.getAnnotations();
    if (anno != null && !anno.isEmpty()) {
      String btrace = BTrace.class.getName();
      boolean isBTrace = false;
      for (AnnotationTree at : anno) {
        String name = at.getAnnotationType().toString();
        if (name.equals(btrace) || name.equals("BTrace")) {
          isBTrace = true;
        }
        // RequestPermission annotations removed; nothing to collect here.
      }
      if (isBTrace) {
        String oldClassName = className;
        try {
          className = node.getSimpleName().toString();
          fqn = getElement(node).asType().toString();
          Void result = super.visitClass(node, v);
          // Permissions are enforced against agent grants at runtime.
          return result;
        } finally {
          className = oldClassName;
        }
      }
    }
    reportError("not.a.btrace.program", node);
    return null;
  }

  @Override
  public Void visitDoWhileLoop(DoWhileLoopTree node, Void v) {
    reportError("no.do.while", node);
    return super.visitDoWhileLoop(node, v);
  }

  @Override
  public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void v) {
    reportError("no.enhanced.for", node);
    return super.visitEnhancedForLoop(node, v);
  }

  @Override
  public Void visitForLoop(ForLoopTree node, Void v) {
    reportError("no.for.loop", node);
    return super.visitForLoop(node, v);
  }

  @Override
  public Void visitMethod(MethodTree node, Void v) {
    boolean oldInsideMethod = insideMethod;
    insideMethod = true;
    try {
      Name name = node.getName();
      if (name.contentEquals("<init>")) {
        return super.visitMethod(node, v);
      } else {
        checkSampling(node);

        if (isExitHandler(node)) {
          if (node.getParameters().size() != 1
              || !"int".equals(node.getParameters().get(0).getType().toString())) {
            reportError("onexit.invalid", node);
            return super.visitMethod(node, v);
          }
        }
        if (isErrorHandler(node)) {
          Element thrElement = getElement(node.getParameters().get(0).getType());
          if (node.getParameters().size() != 1 || !THROWABLE_TYPE.equals(thrElement.toString())) {
            reportError("onerror.invalid", node);
          }
        }

        for (VariableTree vt : node.getParameters()) {
          vt.accept(
              new TreeScanner<Void, Void>() {
                @Override
                public Void visitAnnotation(AnnotationTree at, Void p) {
                  isInAnnotation = true;
                  try {
                    return super.visitAnnotation(at, p);
                  } finally {
                    isInAnnotation = false;
                  }
                }
              },
              null);
        }

        Set<Modifier> flags = node.getModifiers().getFlags();
        if (shortSyntax) {
          if (isStatic(flags)) {
            reportError("no.static.method", node);
          }
          if (isSynchronized(flags)) {
            reportError("no.synchronized.methods", node);
          }
        } else {
          boolean isStatic = isStatic(flags);
          if (isStatic) {
            boolean isPublic = isPublic(node.getModifiers().getFlags());
            if (isPublic) {
              if (isSynchronized(flags)) {
                reportError("no.synchronized.methods", node);
              }
            } else {
              // force the "public" modifier only on the annotated methods
              if (isAnnotated(node)) {
                reportError("method.should.be.public", node);
              }
            }
          } else {
            reportError("no.instance.method", node);
          }
        }

        node.accept(jfrFieldNameCollector, null);

        return super.visitMethod(node, v);
      }
    } finally {
      insideMethod = oldInsideMethod;
    }
  }

  private void addEventFieldNames(AnnotationTree at) {
    for (ExpressionTree et1 : at.getArguments()) {
      addEventFieldName((AssignmentTree) et1);
    }
  }

  private void addEventFieldName(AssignmentTree assignmentTree) {
    String varName = assignmentTree.getVariable().toString();
    if (varName.equals("name")) {
      eventFieldNames.add(assignmentTree.getExpression().toString());
    }
  }

  @Override
  public Void visitNewArray(NewArrayTree node, Void v) {
    if (!isInAnnotation) {
      reportError("no.array.creation", node);
    }
    return super.visitNewArray(node, v);
  }

  @Override
  public Void visitNewClass(NewClassTree node, Void v) {
    Element e = getElement(node);
    TypeElement te = (TypeElement) e.getEnclosingElement();
    reportError("no.new.object", node);
    return super.visitNewClass(node, v);
  }

  @Override
  public Void visitReturn(ReturnTree node, Void v) {
    if (node.getExpression() != null) {
      TreePath tp = verifier.getTreeUtils().getPath(verifier.getCompilationUnit(), node);
      while (tp != null) {
        tp = tp.getParentPath();
        Tree leaf = tp.getLeaf();
        if (leaf.getKind() == Tree.Kind.METHOD) {
          if (isAnnotated((MethodTree) leaf)) {
            reportError("return.type.should.be.void", node);
          } else {
            return super.visitReturn(node, v);
          }
        }
      }
    }
    return super.visitReturn(node, v);
  }

  @Override
  public Void visitMemberSelect(MemberSelectTree node, Void v) {
    if (!isInAnnotation) {
      if (node.getIdentifier().contentEquals("class")) {
        TypeMirror tm = getType(node.getExpression());
        String typeName = tm != null ? tm.toString() : "";
        // Allow class literals only for @Injected service types
        if (!injectedServiceTypes.contains(typeName)) {
          reportError("no.class.literals", node);
        }
      }
    }
    return super.visitMemberSelect(node, v);
  }

  @Override
  public Void visitAnnotation(AnnotationTree node, Void unused) {
    try {
      isInAnnotation = true;
      return super.visitAnnotation(node, unused);
    } finally {
      isInAnnotation = false;
    }
  }

  @Override
  public Void visitSynchronized(SynchronizedTree node, Void v) {
    reportError("no.synchronized.blocks", node);
    return super.visitSynchronized(node, v);
  }

  @Override
  public Void visitThrow(ThrowTree node, Void v) {
    reportError("no.throw", node);
    return super.visitThrow(node, v);
  }

  @Override
  public Void visitTry(TryTree node, Void v) {
    reportError("no.try", node);
    return super.visitTry(node, v);
  }

  @Override
  public Void visitVariable(VariableTree vt, Void p) {
    VariableElement ve = (VariableElement) getElement(vt);

    if (ve.getEnclosingElement().getKind() == ElementKind.CLASS) {
      // only applying to fields
      Injected injected = ve.getAnnotation(Injected.class);
      if (injected != null) {
        // Track the injected service/interface type and its package for later method-call checks
        String serviceTypeName = ve.asType().toString();
        injectedServiceTypes.add(serviceTypeName);
        int lastDot = serviceTypeName.lastIndexOf('.');
        if (lastDot > 0) {
          String servicePackage = serviceTypeName.substring(0, lastDot);
          servicePackages.add(servicePackage);
        }

        // Validate that the injected service type is declared by some extension
        if (!isDeclaredExtensionService(serviceTypeName)) {
          reportError("invalid.injected.service", vt);
        }
        if (vt.getInitializer() != null) {
          reportError("injected.no.initializer", vt.getInitializer());
        }
        // Best effort: if the field type is itself an Extension subtype, collect permissions
        if (extensionTm != null && verifier.getTypeUtils().isSubtype(ve.asType(), extensionTm)) {
          collectExtensionPermissions(ve.asType());
        }
      } else {
        // JFR field name collection still applies for non-injected fields
        vt.accept(jfrFieldNameCollector, null);
      }
    }

    return super.visitVariable(vt, p);
  }

  /**
   * Returns true if the given service class name is declared by any extension.
   *
   * <p>Compile-time validation notes: checks first via the annotation processing type model (which
   * sees -cp JARs), then falls back to scanning jar manifests on the JVM classpath. The agent
   * performs a definitive runtime check (Client#validateDeclaredServices).
   */
  private boolean isDeclaredExtensionService(String serviceClassName) {
    // Primary: use the annotation processing Elements API — sees the compilation classpath
    TypeElement te = verifier.getElementUtils().getTypeElement(serviceClassName);
    if (te != null) {
      for (javax.lang.model.element.AnnotationMirror am : te.getAnnotationMirrors()) {
        String annName = am.getAnnotationType().asElement().toString();
        if ("io.btrace.core.extensions.ServiceDescriptor".equals(annName)) {
          return true;
        }
      }
    }
    // Fallback: scan JAR manifests visible to the JVM classloader
    String resourceName = serviceClassName.replace('.', '/') + ".class";
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = getClass().getClassLoader();
    }
    try {
      URL res = cl != null ? cl.getResource(resourceName) : null;
      if (res != null && "jar".equals(res.getProtocol())) {
        String spec = res.getFile();
        int idx = spec.indexOf('!');
        if (idx > 0) {
          String jarUrl = spec.substring(0, idx);
          if (jarUrl.startsWith("file:")) {
            jarUrl = jarUrl.substring(5);
          }
          String jarPath = URLDecoder.decode(jarUrl, StandardCharsets.UTF_8.name());
          if (declaresServiceInJar(jarPath, serviceClassName)) {
            return true;
          }
        }
      }
      String cp = System.getProperty("java.class.path", "");
      String[] parts = cp.split(java.io.File.pathSeparator);
      for (String p : parts) {
        if (p.endsWith(".jar")) {
          try {
            if (declaresServiceInJar(p, serviceClassName)) {
              return true;
            }
          } catch (Exception ignored) {
          }
        }
      }
    } catch (Exception ignored) {
    }
    return false;
  }

  private boolean declaresServiceInJar(String jarPath, String serviceClassName) {
    try (JarFile jf = new JarFile(jarPath)) {
      Manifest mf = jf.getManifest();
      if (mf != null) {
        Attributes attrs = mf.getMainAttributes();
        String services = attrs.getValue("BTrace-Extension-Services");
        if (services != null && !services.trim().isEmpty()) {
          for (String s : services.split("[,\\s]+")) {
            if (serviceClassName.equals(s.trim())) {
              return true;
            }
          }
        }
      }
      java.util.zip.ZipEntry props = jf.getEntry("META-INF/btrace-extension.properties");
      if (props != null) {
        Properties pr = new Properties();
        try (java.io.InputStream is = jf.getInputStream(props)) {
          pr.load(is);
        }
        String services = pr.getProperty("services");
        if (services != null && !services.trim().isEmpty()) {
          for (String s : services.split("[,\\s]+")) {
            if (serviceClassName.equals(s.trim())) {
              return true;
            }
          }
        }
      }
    } catch (Exception ignored) {
    }
    return false;
  }

  private void processEventFields(AssignmentTree t) {
    if (t.getExpression() instanceof AnnotationTree) {
      AnnotationTree at = (AnnotationTree) t.getExpression();
      addEventFieldNames(at);
    } else if (t.getExpression() instanceof NewArrayTree) {
      for (ExpressionTree et2 : ((NewArrayTree) t.getExpression()).getInitializers()) {
        AnnotationTree at = (AnnotationTree) et2;
        addEventFieldNames(at);
      }
    }
  }

  @Override
  public Void visitWhileLoop(WhileLoopTree node, Void v) {
    reportError("no.while.loop", node);
    return super.visitWhileLoop(node, v);
  }

  @Override
  public Void visitOther(Tree node, Void v) {
    reportError("no.other", node);
    return super.visitOther(node, v);
  }

  private boolean isStatic(Set<Modifier> modifiers) {
    for (Modifier m : modifiers) {
      if (m == Modifier.STATIC) {
        return true;
      }
    }
    return false;
  }

  private boolean isSynchronized(Set<Modifier> modifiers) {
    for (Modifier m : modifiers) {
      if (m == Modifier.SYNCHRONIZED) {
        return true;
      }
    }
    return false;
  }

  private boolean isPublic(Set<Modifier> modifiers) {
    for (Modifier m : modifiers) {
      if (m == Modifier.PUBLIC) {
        return true;
      }
    }
    return false;
  }

  private boolean isErrorHandler(MethodTree node) {
    for (AnnotationTree at : node.getModifiers().getAnnotations()) {
      if (ON_ERROR_TYPE.equals(verifier.resolveAnnotationTypeName(at))) {
        return true;
      }
    }
    return false;
  }

  private boolean isExitHandler(MethodTree node) {
    for (AnnotationTree at : node.getModifiers().getAnnotations()) {
      if (ON_EXIT_TYPE.equals(verifier.resolveAnnotationTypeName(at))) {
        return true;
      }
    }
    return false;
  }

  private boolean isAnnotated(MethodTree node) {
    for (AnnotationTree at : node.getModifiers().getAnnotations()) {
      String resolvedName = verifier.resolveAnnotationTypeName(at);
      if (resolvedName != null && resolvedName.startsWith("io.btrace.core.annotations")) {
        return true;
      }
    }
    return false;
  }

  private void checkSampling(MethodTree node) {
    ExecutableElement ee = (ExecutableElement) getElement(node);

    Sampled s = ee.getAnnotation(Sampled.class);
    OnMethod om = ee.getAnnotation(OnMethod.class);

    if (s != null && om != null) {
      Kind k = om.location().value();
      switch (k) {
        case ENTRY:
        case RETURN:
        case ERROR:
        case CALL:
          {
            return;
          }
        default:
          {
            // noop
          }
      }
      reportError("sampler.invalid.location", node);
    }
  }

  private void checkLValue(Tree variable) {
    if (variable.getKind() == Tree.Kind.ARRAY_ACCESS) {
      reportError("no.assignment", variable);
      return;
    }

    if (variable.getKind() != Tree.Kind.IDENTIFIER) {
      if (className != null) {
        String name = variable.toString();
        name = name.substring(0, name.lastIndexOf('.'));
        if (!className.equals(name)) {
          reportError("no.assignment", variable);
        }
      } else {
        reportError("no.assignment", variable);
      }
    }
  }

  private void reportError(String msg, Tree node) {
    SourcePositions srcPos = verifier.getSourcePositions();
    CompilationUnitTree compUnit = verifier.getCompilationUnit();
    if (compUnit != null) {
      long pos = srcPos.getStartPosition(compUnit, node);
      long line = compUnit.getLineMap().getLineNumber(pos);
      String name = compUnit.getSourceFile().getName();
      Element e = getElement(node);
      msg = String.format("%s:%d:%s [%s]", name, line, Messages.get(msg), e);
      verifier.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
    } else {
      verifier.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
    }
  }

  private Element getElement(Tree t) {
    TreePath tp = verifier.getTreeUtils().getPath(verifier.getCompilationUnit(), t);
    Element e = verifier.getTreeUtils().getElement(tp);
    if (e == null) {
      switch (t.getKind()) {
        case NEW_CLASS:
          e =
              verifier
                  .getTreeUtils()
                  .getElement(new TreePath(tp, ((NewClassTree) t).getIdentifier()));
          break;
        case THROW:
          e = verifier.getTreeUtils().getElement(new TreePath(tp, ((ThrowTree) t).getExpression()));
          break;
      }
      if (e == null) {
        verifier.getMessager().printMessage(Diagnostic.Kind.ERROR, t.toString());
      }
    }
    return e;
  }

  private TypeMirror getType(Tree t) {
    TreePath tp = verifier.getTreeUtils().getPath(verifier.getCompilationUnit(), t);
    return verifier.getTreeUtils().getTypeMirror(tp);
  }

  // Collect extension permissions from manifest (canonical). Legacy annotations removed.
  private void collectExtensionPermissions(TypeMirror extensionType) {
    Element extensionElement = verifier.getTypeUtils().asElement(extensionType);
    if (extensionElement == null) {
      return;
    }

    // Also try to read permissions from the extension API JAR manifest
    // Attribute: BTrace-Extension-Permissions: CSV of Permission names
    try {
      String className = extensionType.toString();
      String resourceName = className.replace('.', '/') + ".class";
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      if (cl == null) {
        cl = getClass().getClassLoader();
      }
      URL res = cl != null ? cl.getResource(resourceName) : null;
      if (res != null && "jar".equals(res.getProtocol())) {
        String spec = res.getFile();
        int idx = spec.indexOf('!');
        if (idx > 0) {
          String jarUrl = spec.substring(0, idx);
          if (jarUrl.startsWith("file:")) {
            jarUrl = jarUrl.substring(5);
          }
          String jarPath = URLDecoder.decode(jarUrl, StandardCharsets.UTF_8.name());
          try (JarFile jf = new JarFile(jarPath)) {
            Manifest mf = jf.getManifest();
            if (mf != null) {
              Attributes attrs = mf.getMainAttributes();
              String perms = attrs.getValue("BTrace-Extension-Permissions");
              if (perms != null && !perms.trim().isEmpty()) {
                String[] parts = perms.split("[,\\s]+");
                for (String p : parts) {
                  try {
                    requiredPermissions.add(Permission.valueOf(p.trim()));
                  } catch (IllegalArgumentException ignored) {
                    // ignore unknown entries
                  }
                }
              }
            }
          }
        }
      }
    } catch (Exception ignored) {
      // Best-effort only; ignore any IO errors
    }
  }

  // No compile-time check for probe-declared permissions; runtime grants enforce permissions.
}
