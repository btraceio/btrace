/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.btrace.compiler;

import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.tools.Diagnostic;
import com.sun.source.tree.*;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.Injected;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.OnError;
import com.sun.btrace.annotations.OnExit;
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.Sampled;
import com.sun.btrace.util.Messages;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * This class tree visitor validates a BTrace program's ClassTree.
 *
 * @author A. Sundararajan
 */
public class VerifierVisitor extends TreeScanner<Boolean, Void> {
    private static final String ON_ERROR_TYPE = OnError.class.getName();
    private static final String ON_EXIT_TYPE = OnExit.class.getName();
    private static final String THROWABLE_TYPE = Throwable.class.getName();

    private final Verifier verifier;
    private String className;
    private String fqn;
    private boolean insideMethod;

    private boolean shortSyntax = false;
    private TypeMirror btraceServiceTm = null;
    private TypeMirror runtimeServiceTm = null;
    private TypeMirror simpleServiceTm = null;
    private TypeMirror serviceInjectorTm = null;

    public VerifierVisitor(Verifier verifier, Element clzElement) {
        this.verifier = verifier;
        Collection<ExecutableElement> shared = new ArrayList<>();
        for(Element e : clzElement.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD && e.getModifiers().containsAll(EnumSet.of(Modifier.STATIC, Modifier.PRIVATE))) {
                shared.add((ExecutableElement)e);
            }
        }
        btraceServiceTm = verifier.getElementUtils().getTypeElement("com.sun.btrace.services.spi.BTraceService").asType();
        runtimeServiceTm = verifier.getElementUtils().getTypeElement("com.sun.btrace.services.spi.RuntimeService").asType();
        simpleServiceTm = verifier.getElementUtils().getTypeElement("com.sun.btrace.services.spi.SimpleService").asType();
        serviceInjectorTm = verifier.getElementUtils().getTypeElement("com.sun.btrace.services.api.Service").asType();
    }

    @Override
    public Boolean visitMethodInvocation(MethodInvocationTree node, Void v) {
        Element e = getElement(node);
        if (e.getKind() == ElementKind.METHOD || e.getKind() == ElementKind.CONSTRUCTOR) {
            String name = ((ExecutableElement)e).getSimpleName().toString();

            // allow constructor calls
            if (name.equals("<init>")) {
                return super.visitMethodInvocation(node, v);
            }

            Element parent = null;
            do {
                parent = e.getEnclosingElement();
            } while (parent != null && (parent.getKind() != ElementKind.CLASS && parent.getKind() != ElementKind.INTERFACE));

            if (parent != null) {
                TypeMirror tm = parent.asType();
                String typeName = tm.toString();

                if (isSameClass(typeName)) {
                    return super.visitMethodInvocation(node, v);
                }
                if (isBTraceClass(typeName)) {
                    return super.visitMethodInvocation(node, v);
                }
                // check service injection
                if (verifier.getTypeUtils().isSubtype(tm, btraceServiceTm)) {
                    return super.visitMethodInvocation(node, v);
                }
                if (verifier.getTypeUtils().isSubtype(tm, serviceInjectorTm)) {
                    if (!validateInjectionParams(node)) {
                        reportError("service.injector.literals", node);
                    }

                    return super.visitMethodInvocation(node, v);
                }
            }
        }
        reportError("no.method.calls", node);
        return super.visitMethodInvocation(node, v);
    }

    private boolean isSameClass(String typeName) {
        return fqn.equals(typeName);
    }

    private boolean isBTraceClass(String typeName) {
        return typeName.equals("com.sun.btrace.BTraceUtils") || typeName.startsWith("com.sun.btrace.BTraceUtils.");
    }

    @Override
    public Boolean visitAssert(AssertTree node, Void v) {
        reportError("no.asserts", node);
        return super.visitAssert(node, v);
    }

    @Override
    public Boolean visitAssignment(AssignmentTree node, Void v) {
        checkLValue(node.getVariable());
        return super.visitAssignment(node, v);
    }

    @Override
    public Boolean visitCompoundAssignment(CompoundAssignmentTree node, Void v) {
        checkLValue(node.getVariable());
        return super.visitCompoundAssignment(node, v);
    }

    @Override
    public Boolean visitCatch(CatchTree node, Void v) {
        reportError("no.catch", node);
        return super.visitCatch(node, v);
    }

    @Override
    public Boolean visitClass(ClassTree node, Void v) {
        // check for local class
        if (insideMethod) {
            reportError("no.local.class", node);
        }

        // check for short BTrace syntax (inferring redundant access qualifiers)
        Set<Modifier> mods = node.getModifiers().getFlags();
        if (!mods.contains(Modifier.PRIVATE) &&
            !mods.contains(Modifier.PROTECTED) &&
            !mods.contains(Modifier.PUBLIC))
        {
            shortSyntax = true;
        }
        // check for inner and nested class
        List<? extends Tree> members = node.getMembers();
        for (Tree m : members) {
            if (m.getKind() == Tree.Kind.CLASS) {
                reportError("no.nested.class", m);
            }

            if (m.getKind() == Tree.Kind.VARIABLE) {
                VariableTree vt = (VariableTree)m;
                boolean isStatic = isStatic(vt.getModifiers().getFlags());
                if (shortSyntax) {
                    if (isStatic) {
                        reportError("no.static.variables", m);
                    }
                } else {
                    if (! isStatic) {
                        reportError("no.instance.variables", m);
                    }
                }
            }
        }

        // should extend java.lang.Object
        Tree superClass = node.getExtendsClause();
        if (superClass != null) {
            String name = superClass.toString();
            if (!name.equals("Object") &&
                !name.equals("java.lang.Object")) {
                reportError("object.superclass.required", superClass);
            }
        }

        // should not implement interfaces
        List<? extends Tree> interfaces = node.getImplementsClause();
        if (interfaces != null && interfaces.size() > 0) {
            reportError("no.interface.implementation", interfaces.get(0));
        }

        ModifiersTree mt = node.getModifiers();
        if (!shortSyntax && ! isPublic(mt.getFlags())) {
            reportError("class.should.be.public", node);
        }
        List<? extends AnnotationTree> anno = mt.getAnnotations();
        if (anno != null && !anno.isEmpty()) {
            String btrace = BTrace.class.getName();
            for (AnnotationTree at : anno) {
                String name = at.getAnnotationType().toString();
                if (name.equals(btrace) ||
                    name.equals("BTrace")) {
                    String oldClassName = className;
                    try {
                        className = node.getSimpleName().toString();
                        fqn = getElement(node).asType().toString();
                        return super.visitClass(node, v);
                    } finally {
                        className = oldClassName;
                    }
                }
            }
        }
        return reportError("not.a.btrace.program", node);
    }

    @Override
    public Boolean visitDoWhileLoop(DoWhileLoopTree node, Void v) {
        reportError("no.do.while", node);
        return super.visitDoWhileLoop(node, v);
    }

    @Override
    public Boolean visitEnhancedForLoop(EnhancedForLoopTree node, Void v) {
        reportError("no.enhanced.for", node);
        return super.visitEnhancedForLoop(node, v);
    }

    @Override
    public Boolean visitForLoop(ForLoopTree node, Void v) {
        reportError("no.for.loop", node);
        return super.visitForLoop(node, v);
    }

    @Override
    public Boolean visitMethod(MethodTree node, Void v) {
        boolean oldInsideMethod = insideMethod;
        insideMethod = true;
        try {
            Name name = node.getName();
            if (name.contentEquals("<init>")) {
                return super.visitMethod(node, v);
            } else {
                checkSampling(node);

                if (isExitHandler(node)) {
                    if (node.getParameters().size() != 1 || ! "int".equals(node.getParameters().get(0).getType().toString())) {
                        reportError("onexit.invalid", node);
                        return super.visitMethod(node, v);
                    }
                }
                if (isErrorHandler(node)) {
                    Element thrElement = getElement(node.getParameters().get(0).getType());
                    if (node.getParameters().size() != 1 || ! THROWABLE_TYPE.equals(thrElement.toString())) {
                        reportError("onerror.invalid", node);
                    }
                }

                final Map<String, Integer> annotationHisto = new HashMap<>();
                for(VariableTree vt : node.getParameters()) {
                    vt.accept(new TreeScanner<Void, Void>() {
                        @Override
                        public Void visitAnnotation(AnnotationTree at, Void p) {
                            String annType = at.getAnnotationType().toString();
                            Integer cnt = annotationHisto.get(annType);
                            if (cnt == null) {
                                cnt = 0;
                            } else {
                                reportError("multiple.param.annotation", at);
                                cnt++;
                            }
                            annotationHisto.put(annType, cnt);
                            return super.visitAnnotation(at, p);
                        }
                    }, null);
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

                return super.visitMethod(node, v);
            }
        } finally {
            insideMethod = oldInsideMethod;
        }
    }

    @Override
    public Boolean visitNewArray(NewArrayTree node, Void v) {
        reportError("no.array.creation", node);
        return super.visitNewArray(node, v);
    }

    @Override
    public Boolean visitNewClass(NewClassTree node, Void v) {
        reportError("no.new.object", node);
        return super.visitNewClass(node, v);
    }

    @Override
    public Boolean visitReturn(ReturnTree node, Void v) {
        reportError("return.type.should.be.void", node);
        return super.visitReturn(node, v);
    }

    @Override
    public Boolean visitMemberSelect(MemberSelectTree node, Void v) {
        if (node.getIdentifier().contentEquals("class")) {
            TypeMirror tm = getType(node.getExpression());
            if (!verifier.getTypeUtils().isSubtype(tm, btraceServiceTm)) {
                reportError("no.class.literals", node);
            }
        }
        return super.visitMemberSelect(node, v);
    }

    @Override
    public Boolean visitSynchronized(SynchronizedTree node, Void v) {
        reportError("no.synchronized.blocks", node);
        return super.visitSynchronized(node, v);
    }

    @Override
    public Boolean visitThrow(ThrowTree node, Void v) {
        reportError("no.throw", node);
        return super.visitThrow(node, v);
    }

    @Override
    public Boolean visitTry(TryTree node, Void v) {
        reportError("no.try", node);
        return super.visitTry(node, v);
    }

    @Override
    public Boolean visitVariable(VariableTree vt, Void p) {
        VariableElement ve = (VariableElement)getElement(vt);

        if (ve.getEnclosingElement().getKind() == ElementKind.CLASS) {
            // only applying to fields
            if (verifier.getTypeUtils().isSubtype(ve.asType(), btraceServiceTm)) {
                Injected i = ve.getAnnotation(Injected.class);
                if (i == null) {
                    reportError("missing.injected", vt);
                } else {
                    switch (i.value()) {
                        case RUNTIME: {
                            if (!verifier.getTypeUtils().isSubtype(ve.asType(), runtimeServiceTm)) {
                                reportError("injected.no.runtime", vt);
                            }
                            break;
                        }
                        case SIMPLE: {
                            if (!verifier.getTypeUtils().isSubtype(ve.asType(), simpleServiceTm)) {
                                reportError("injected.no.simple", vt);
                            }
                            break;
                        }
                    }
                }
                if (vt.getInitializer() != null) {
                    reportError("injected.no.initializer", vt.getInitializer());
                }
            }
        }

        return super.visitVariable(vt, p);
    }

    @Override
    public Boolean visitWhileLoop(WhileLoopTree node, Void v)  {
        reportError("no.while.loop", node);
        return super.visitWhileLoop(node, v);
    }

    @Override
    public Boolean visitOther(Tree node, Void v) {
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
        ModifiersTree mt = node.getModifiers();
        List<? extends AnnotationTree> annos = mt.getAnnotations();
        for(AnnotationTree at : annos) {
            String annFqn = ((JCTree)at.getAnnotationType()).type.tsym.getQualifiedName().toString();
            if (annFqn.equals(ON_ERROR_TYPE)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExitHandler(MethodTree node) {
        ModifiersTree mt = node.getModifiers();
        List<? extends AnnotationTree> annos = mt.getAnnotations();
        for(AnnotationTree at : annos) {
            String annFqn = ((JCTree)at.getAnnotationType()).type.tsym.getQualifiedName().toString();
            if (annFqn.equals(ON_EXIT_TYPE)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAnnotated(MethodTree node) {
        ModifiersTree mt = node.getModifiers();
        List<? extends AnnotationTree> annos = mt.getAnnotations();
        for(AnnotationTree at : annos) {
            String annFqn = ((JCTree)at.getAnnotationType()).type.tsym.getQualifiedName().toString();
            if (annFqn.startsWith("com.sun.btrace.annotations")) {
                return true;
            }
        }
        return false;
    }

    private boolean checkSampling(MethodTree node) {
        TreePath mPath = verifier.getTreeUtils().getPath(verifier.getCompilationUnit(), node);
        ExecutableElement ee = (ExecutableElement)verifier.getTreeUtils().getElement(mPath);

        Sampled s = ee.getAnnotation(Sampled.class);
        OnMethod om = ee.getAnnotation(OnMethod.class);

        if (s != null && om != null) {
            Kind k = om.location().value();
            switch (k) {
                case ENTRY:
                case RETURN:
                case ERROR:
                case CALL: {
                    return true;
                }
            }
            reportError("sampler.invalid.location", node);
            return false;
        }
        return true;
    }

    private boolean checkLValue(Tree variable) {
        if (variable.getKind() == Tree.Kind.ARRAY_ACCESS) {
            reportError("no.assignment", variable);
            return false;
        }

        if (variable.getKind() != Tree.Kind.IDENTIFIER) {
            if (className != null) {
                String name = variable.toString();
                name = name.substring(0, name.lastIndexOf("."));
                if (! className.equals(name)) {
                    reportError("no.assignment", variable);
                    return false;
                }
            } else {
                reportError("no.assignment", variable);
                return false;
            }
        }
        return true;
    }

    private Boolean reportError(String msg, Tree node) {
        SourcePositions srcPos = verifier.getSourcePositions();
        CompilationUnitTree compUnit = verifier.getCompilationUnit();
        if (compUnit != null) {
            long pos = srcPos.getStartPosition(compUnit, node);
            long line = compUnit.getLineMap().getLineNumber(pos);
            String name = compUnit.getSourceFile().getName();
            msg = String.format("%s:%d:%s", name, line, Messages.get(msg));
            verifier.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, getElement(node));
        } else {
            verifier.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
        }
        return Boolean.FALSE;
    }

    private Element getElement(Tree t) {
        TreePath tp = verifier.getTreeUtils().getPath(verifier.getCompilationUnit(), t);
        return verifier.getTreeUtils().getElement(tp);
    }

    private TypeMirror getType(Tree t) {
        TreePath tp = verifier.getTreeUtils().getPath(verifier.getCompilationUnit(), t);
        return verifier.getTreeUtils().getTypeMirror(tp);
    }

    private boolean validateInjectionParams(MethodInvocationTree node) {
        boolean allLiterals = true;
        outer:
        for(ExpressionTree arg : node.getArguments()) {
            switch (arg.getKind()) {
                case MEMBER_SELECT: {
                    if (!arg.toString().endsWith(".class")) {
                        allLiterals = false;
                        break outer;
                    }
                }
                case STRING_LITERAL: {
                    // allowed parameters
                    break;
                }
                default: {
                    allLiterals = false;
                    break outer;
                }
            }
        }
        return allLiterals;
    }

}
