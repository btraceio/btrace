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
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import io.btrace.core.Messages;
import io.btrace.core.annotations.BTrace;

/**
 * An annotation processor that validates a BTrace program. Safety rules (such as no loops, no
 * new/throw etc.) are enforced. This uses javac's Tree API in addition to JSR 269.
 *
 * @author A. Sundararajan
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class Verifier extends AbstractProcessor implements TaskListener {
  private final List<String> classNames = new ArrayList<>();
  private final List<CompilationUnitTree> compUnits = new ArrayList<>();
  private final AttributionTaskListener listener = new AttributionTaskListener();
  private Trees treeUtils;
  private ClassTree currentClass;

  @Override
  public synchronized void init(ProcessingEnvironment pe) {
    super.init(pe);
    treeUtils = Trees.instance(pe);
    JavacTask javacTask = JavacTask.instance(processingEnv);
    javacTask.addTaskListener(listener);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    return true;
  }

  @Override
  public void started(TaskEvent e) {
    if (e.getKind() == TaskEvent.Kind.ENTER) {
      CompilationUnitTree ct = e.getCompilationUnit();
      if (ct != null) {
        compUnits.add(ct);
      }
    }
  }

  @Override
  public void finished(TaskEvent e) {
    if (e.getKind() != TaskEvent.Kind.ANALYZE) return;
    if (processingEnv == null) {
      return;
    }
    TypeElement elem = e.getTypeElement();
    TreePath compilationRoot = new TreePath(e.getCompilationUnit());
    for (Tree t : e.getCompilationUnit().getTypeDecls()) {
      if (t.getKind() == Tree.Kind.CLASS) {
        if (elem.equals(getTreeUtils().getElement(new TreePath(compilationRoot, t)))) {
          currentClass = (ClassTree) t;
          break;
        }
      }
    }
    if (currentClass != null) {
      verify(currentClass, elem);
    }
  }

  List<String> getClassNames() {
    return classNames;
  }

  CompilationUnitTree getCompilationUnit() {
    for (CompilationUnitTree ct : compUnits) {
      for (Tree clazz : ct.getTypeDecls()) {
        if (clazz.equals(currentClass)) {
          return ct;
        }
      }
    }
    return null;
  }

  Trees getTreeUtils() {
    return treeUtils;
  }

  SourcePositions getSourcePositions() {
    return treeUtils.getSourcePositions();
  }

  ProcessingEnvironment getProcessingEnvironment() {
    return processingEnv;
  }

  Messager getMessager() {
    return processingEnv.getMessager();
  }

  Elements getElementUtils() {
    return processingEnv.getElementUtils();
  }

  Types getTypeUtils() {
    return processingEnv.getTypeUtils();
  }

  Locale getLocale() {
    return processingEnv.getLocale();
  }

  /**
   * Resolves the fully-qualified type name of an annotation in the current compilation unit.
   * Returns {@code null} if the type cannot be resolved or is not an annotation type.
   */
  String resolveAnnotationTypeName(AnnotationTree annotation) {
    Trees treeApi = getTreeUtils();
    Tree annotationTypeTree = annotation.getAnnotationType();
    TreePath typePath = treeApi.getPath(getCompilationUnit(), annotationTypeTree);
    Element resolved = treeApi.getElement(typePath);
    if (resolved != null && resolved.getKind() == ElementKind.ANNOTATION_TYPE) {
      return ((TypeElement) resolved).getQualifiedName().toString();
    }
    return null;
  }

  // verify each BTrace class
  private void verify(ClassTree ct, Element topElement) {
    currentClass = ct;
    CompilationUnitTree cut = getCompilationUnit();
    String className = ct.getSimpleName().toString();
    ExpressionTree pkgName = cut.getPackageName();
    if (pkgName != null) {
      className = pkgName + "." + className;
    }
    classNames.add(className);
    if (hasTrustedAnnotation(ct, topElement)) {
      return;
    }
    ct.accept(new VerifierVisitor(this, topElement), null);
  }

  /** Detects if the class is annotated as @BTrace(trusted=true). */
  private boolean hasTrustedAnnotation(ClassTree ct, Element topElement) {
    for (AnnotationTree annotation : ct.getModifiers().getAnnotations()) {
      String qualifiedName = resolveAnnotationTypeName(annotation);
      if (!BTrace.class.getName().equals(qualifiedName)) {
        continue;
      }
      // now we have @BTrace, look for unsafe = xxx or trusted = xxx
      for (ExpressionTree attr : annotation.getArguments()) {
        if (attr.getKind() != Tree.Kind.ASSIGNMENT) {
          continue;
        }
        AssignmentTree assignment = (AssignmentTree) attr;
        String attrName = ((IdentifierTree) assignment.getVariable()).getName().toString();
        if (!"unsafe".equals(attrName) && !"trusted".equals(attrName)) {
          continue;
        }
        // now rhs is the value of @BTrace.unsafe.
        // The value can be complex (!!true, 1 == 2, etc.) - we support only booleans
        String attrValue = assignment.getExpression().toString();
        if ("true".equals(attrValue)) {
          return true; // bingo!
        } else if (!"false".equals(attrValue)) {
          processingEnv
              .getMessager()
              .printMessage(Kind.WARNING, Messages.get("no.complex.unsafe.value"), topElement);
        }
      }
    }
    return false;
  }

  /** A task listener that invokes the processor whenever a class is fully analyzed. */
  private final class AttributionTaskListener implements TaskListener {

    @Override
    public void finished(TaskEvent e) {
      if (e.getKind() != TaskEvent.Kind.ANALYZE) return;
      TypeElement elem = e.getTypeElement();
      TreePath compilationRoot = new TreePath(e.getCompilationUnit());
      for (Tree t : e.getCompilationUnit().getTypeDecls()) {
        if (t.getKind() == Tree.Kind.CLASS) {
          if (elem.equals(getTreeUtils().getElement(new TreePath(compilationRoot, t)))) {
            currentClass = (ClassTree) t;
            break;
          }
        }
      }
      if (currentClass != null) {
        verify(currentClass, elem);
      }
    }

    @Override
    public void started(TaskEvent e) {}
  }
}
