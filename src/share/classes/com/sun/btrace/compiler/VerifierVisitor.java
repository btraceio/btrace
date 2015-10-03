/*
 * Copyright (c) 2008, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.btrace.BTraceUtils;
import java.lang.reflect.Method;
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
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.Sampled;
import com.sun.btrace.util.Messages;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final Verifier verifier;
    private String className;
    private boolean insideMethod;
    private volatile static Method[] btraceMethods;
    private volatile static Class[] btraceClasses;
    private volatile static ExecutableElement[] sharedMethods;

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
        sharedMethods = shared.toArray(new ExecutableElement[shared.size()]);
        btraceServiceTm = verifier.getElementUtils().getTypeElement("com.sun.btrace.services.spi.BTraceService").asType();
        runtimeServiceTm = verifier.getElementUtils().getTypeElement("com.sun.btrace.services.spi.RuntimeService").asType();
        simpleServiceTm = verifier.getElementUtils().getTypeElement("com.sun.btrace.services.spi.SimpleService").asType();
        serviceInjectorTm = verifier.getElementUtils().getTypeElement("com.sun.btrace.services.api.Service").asType();
    }

    @Override
    public Boolean visitMethodInvocation(MethodInvocationTree node, Void v) {

        ExpressionTree xt = node.getMethodSelect();
        if (xt.getKind() == Tree.Kind.MEMBER_SELECT) {
            MemberSelectTree mst = (MemberSelectTree)xt;
            xt = mst.getExpression();
            TypeMirror tm = getType(xt);
            if (verifier.getTypeUtils().isSubtype(tm, serviceInjectorTm)) {
                if (validateInjectionParams(node)) {
                    return super.visitMethodInvocation(node, v);
                } else {
                    return reportError("service.injector.literals", node);
                }
            } else if (verifier.getTypeUtils().isSubtype(tm, btraceServiceTm)) {
                return super.visitMethodInvocation(node, v);
            }
        }

        ExpressionTree methodSelect = node.getMethodSelect();
        if (methodSelect.getKind() == Tree.Kind.IDENTIFIER) {
            String name = ((IdentifierTree)methodSelect).getName().toString();
            int numArgs = 0;
            List<? extends Tree> args = node.getArguments();
            if (args != null) {
                numArgs = args.size();
            }

            if (name.equals("super") ||
                isBTraceMethod(name, numArgs) ||
                isSharedMethod(name, numArgs)) {
                return super.visitMethodInvocation(node, v);
            } // else fall through ..
        } else if (methodSelect.getKind() == Tree.Kind.MEMBER_SELECT) {
            String name = ((MemberSelectTree)methodSelect).getIdentifier().toString();
            JCTree et = (JCTree)((MemberSelectTree)methodSelect).getExpression();

            String clzName = et.type != null ? (et.type.tsym != null ? et.type.tsym.getQualifiedName().toString() : null) : null;
            if (clzName == null) {
                clzName = et.toString();
            }

            if (clzName.equals("BTraceUtils") ||
                clzName.equals("com.sun.btrace.BTraceUtils") ||
                clzName.startsWith("BTraceUtils.") ||
                clzName.startsWith("com.sun.btrace.BTraceUtils.") ||
                isBTraceClass(clzName)) {
                int numArgs = 0;
                List<? extends Tree> args = node.getArguments();
                if (args != null) {
                    numArgs = args.size();
                }
                if (isBTraceMethod(name, numArgs)) {
                    return super.visitMethodInvocation(node, v);
                } // else fall through..
            } // else fall through..
        } // else fall through..
        return reportError("no.method.calls", node);
    }

    @Override
    public Boolean visitAssert(AssertTree node, Void v) {
        return reportError("no.asserts", node);
    }

    @Override
    public Boolean visitAssignment(AssignmentTree node, Void v) {
        if (checkLValue(node.getVariable())) {
            return super.visitAssignment(node, v);
        } else {
            return Boolean.FALSE;
        }
    }

    @Override
    public Boolean visitCompoundAssignment(CompoundAssignmentTree node, Void v) {
        if (checkLValue(node.getVariable())) {
            return super.visitCompoundAssignment(node, v);
        } else {
            return Boolean.FALSE;
        }
    }

    @Override
    public Boolean visitCatch(CatchTree node, Void v) {
        return reportError("no.catch", node);
    }

    @Override
    public Boolean visitClass(ClassTree node, Void v) {
        // check for local class
        if (insideMethod) {
            return reportError("no.local.class", node);
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
                return reportError("no.nested.class", m);
            }

            if (m.getKind() == Tree.Kind.VARIABLE) {
                VariableTree vt = (VariableTree)m;
                boolean isStatic = isStatic(vt.getModifiers().getFlags());
                if (shortSyntax) {
                    if (isStatic) {
                        return reportError("no.static.variables", m);
                    }
                } else {
                    if (! isStatic) {
                        return reportError("no.instance.variables", m);
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
                return reportError("object.superclass.required", superClass);
            }
        }

        // should not implement interfaces
        List<? extends Tree> interfaces = node.getImplementsClause();
        if (interfaces != null && interfaces.size() > 0) {
            return reportError("no.interface.implementation", interfaces.get(0));
        }

        ModifiersTree mt = node.getModifiers();
        if (!shortSyntax && ! isPublic(mt.getFlags())) {
            return reportError("class.should.be.public", node);
        }
        List<? extends AnnotationTree> anno = mt.getAnnotations();
        if (anno == null || anno.isEmpty()) {
            return reportError("not.a.btrace.program", node);
        }
        String btrace = BTrace.class.getName();
        for (AnnotationTree at : anno) {
            String name = at.getAnnotationType().toString();
            if (name.equals(btrace) ||
                name.equals("BTrace")) {
                String oldClassName = className;
                try {
                    className = node.getSimpleName().toString();
                    return super.visitClass(node, v);
                } finally {
                    className = oldClassName;
                }
            }
        }
        return reportError("not.a.btrace.program", node);
    }

    @Override
    public Boolean visitDoWhileLoop(DoWhileLoopTree node, Void v) {
        return reportError("no.do.while", node);
    }

    @Override
    public Boolean visitEnhancedForLoop(EnhancedForLoopTree node, Void v) {
        return reportError("no.enhanced.for", node);
    }

    @Override
    public Boolean visitForLoop(ForLoopTree node, Void v) {
        return reportError("no.for.loop", node);
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
                if (!checkSampling(node)) {
                    return false;
                }
                if (isExitHandler(node)) {
                    if (node.getParameters().size() != 1 || ! "int".equals(node.getParameters().get(0).getType().toString())) {
                        reportError("onexit.invalid", node);
                        return false;
                    }
                }
                if (isErrorHandler(node)) {
                    Element thrElement = getElement(node.getParameters().get(0).getType());
                    if (node.getParameters().size() != 1 || ! "java.lang.Throwable".equals(thrElement.toString())) {
                        reportError("onerror.invalid", node);
                        return false;
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
                    boolean err = true;
                    if (isStatic(flags)) {
                        err &= reportError("no.static.method", node);
                    }
                    if (isSynchronized(flags)) {
                        err &= reportError("no.synchronized.methods", node);
                    }
                    if (err) {
                        return false;
                    }
                } else {
                    boolean isStatic = isStatic(flags);
                    if (isStatic) {
                        boolean isPublic = isPublic(node.getModifiers().getFlags());
                        if (isPublic) {
                            if (isSynchronized(flags)) {
                                return reportError("no.synchronized.methods", node);
                            } else {
                                return super.visitMethod(node, v);
                            }
                        } else {
                            // force the "public" modifier only on the annotated methods
                            if (isAnnotated(node)) {
                                return reportError("method.should.be.public", node);
                            }
                            return super.visitMethod(node, v);
                        }
                    } else {
                        return reportError("no.instance.method", node);
                    }
                }

                return false;
            }
        } finally {
            insideMethod = oldInsideMethod;
        }
    }

    @Override
    public Boolean visitNewArray(NewArrayTree node, Void v) {
        return reportError("no.array.creation", node);
    }

    @Override
    public Boolean visitNewClass(NewClassTree node, Void v) {
        return reportError("no.new.object", node);
    }

    @Override
    public Boolean visitReturn(ReturnTree node, Void v) {
        if (node.getExpression() != null) {
            TreePath tp = verifier.getTreeUtils().getPath(verifier.getCompilationUnit(), node);
            while (tp != null) {
                tp = tp.getParentPath();
                Tree leaf = tp.getLeaf();
                if (leaf.getKind() == Tree.Kind.METHOD) {
                    if (isAnnotated((MethodTree)leaf)) {
                        return reportError("return.type.should.be.void", node);
                    } else {
                        return super.visitReturn(node, v);
                    }
                }
            }
        }
        return super.visitReturn(node, v);
    }

    @Override
    public Boolean visitMemberSelect(MemberSelectTree node, Void v) {
        if (node.getIdentifier().contentEquals("class")) {
            TypeMirror tm = getType(node.getExpression());
            if (!verifier.getTypeUtils().isSubtype(tm, btraceServiceTm)) {
                return reportError("no.class.literals", node);
            }
        }
        return super.visitMemberSelect(node, v);
    }

    @Override
    public Boolean visitSynchronized(SynchronizedTree node, Void v) {
        return reportError("no.synchronized.blocks", node);
    }

    @Override
    public Boolean visitThrow(ThrowTree node, Void v) {
        return reportError("no.throw", node);
    }

    @Override
    public Boolean visitTry(TryTree node, Void v) {
        return reportError("no.try", node);
    }

    @Override
    public Boolean visitVariable(VariableTree vt, Void p) {
        VariableElement ve = (VariableElement)getElement(vt);

        if (ve.getEnclosingElement().getKind() == ElementKind.CLASS) {
            // only applying to fields
            if (verifier.getTypeUtils().isSubtype(ve.asType(), btraceServiceTm)) {
                Injected i = ve.getAnnotation(Injected.class);
                if (i == null) {
                    return reportError("missing.injected", vt);
                } else {
                    switch (i.value()) {
                        case RUNTIME: {
                            if (!verifier.getTypeUtils().isSubtype(ve.asType(), runtimeServiceTm)) {
                                return reportError("injected.no.runtime", vt);
                            }
                            break;
                        }
                        case SIMPLE: {
                            if (!verifier.getTypeUtils().isSubtype(ve.asType(), simpleServiceTm)) {
                                return reportError("injected.no.simple", vt);
                            }
                            break;
                        }
                    }
                }
                if (vt.getInitializer() != null) {
                    return reportError("injected.no.initializer", vt.getInitializer());
                }
            }
        }

        return super.visitVariable(vt, p);
    }

    @Override
    public Boolean visitWhileLoop(WhileLoopTree node, Void v)  {
        return reportError("no.while.loop", node);
    }

    @Override
    public Boolean visitOther(Tree node, Void v) {
        return reportError("no.other", node);
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
            if (annFqn.equals("com.sun.btrace.annotations.OnError")) {
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
            if (annFqn.equals("com.sun.btrace.annotations.OnExit")) {
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

    private static boolean isBTraceMethod(String name, int numArgs) {
        initBTraceClassesAndMethods();
        for (Method m : btraceMethods) {
            if (m.getName().equals(name) &&
                m.getParameterTypes().length == numArgs) {
                return true;
            } else if (m.getName().equals(name) &&
                    m.getParameterTypes().length < numArgs &&
                    m.isVarArgs()) {
            	return true;
            }
        }
        return false;
    }

    private static boolean isSharedMethod(String name, int numArgs) {
        if (sharedMethods == null) return false;

        for(ExecutableElement m : sharedMethods) {
            if (m.getSimpleName().contentEquals(name) &&
                m.getParameters().size() == numArgs) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBTraceClass(String name) {
        initBTraceClassesAndMethods();
        String postFix = "$" + name;
        for (Class c : btraceClasses) {
            if (c.getName().endsWith(postFix)) {
                return true;
            }
        }
        return false;
    }

    private static void initBTraceClassesAndMethods() {
        if (btraceMethods == null) {
            synchronized (VerifierVisitor.class) {
                if (btraceMethods == null) {
                    Collection<Method> methods = new ArrayList<Method>();
                    Collection<Class> classes = new ArrayList<Class>();
                    methods.addAll(Arrays.asList(BTraceUtils.class.getDeclaredMethods()));
                    for(Class clz : BTraceUtils.class.getDeclaredClasses()) {
                        classes.add(clz);
                        methods.addAll(Arrays.asList(clz.getDeclaredMethods()));
                    }
                    btraceMethods = methods.toArray(new Method[methods.size()]);
                    btraceClasses = classes.toArray(new Class[classes.size()]);
                }
            }
        }
    }

    private Boolean reportError(String msg, Tree node) {
        SourcePositions srcPos = verifier.getSourcePositions();
        CompilationUnitTree compUnit = verifier.getCompilationUnit();
        if (compUnit != null) {
            long pos = srcPos.getStartPosition(compUnit, node);
            long line = compUnit.getLineMap().getLineNumber(pos);
            String name = compUnit.getSourceFile().getName();
            msg = String.format("%s:%d:%s", name, line, Messages.get(msg));
            verifier.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
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
