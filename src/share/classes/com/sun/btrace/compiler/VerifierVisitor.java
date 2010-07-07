/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace.compiler;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.tools.Diagnostic;
import com.sun.source.tree.*;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.btrace.BTraceUtils;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.util.Messages;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * This class tree visitor validates a BTrace program's ClassTree.
 *
 * @author A. Sundararajan
 */
public class VerifierVisitor extends TreeScanner<Boolean, Void> {
    private Verifier verifier;
    private String className;
    private boolean insideMethod;
    private volatile static Method[] btraceMethods;
    private volatile static Class[] btraceClasses;

    public VerifierVisitor(Verifier verifier, Element clzElement) {
        this.verifier = verifier;
        Collection<ExecutableElement> shared = new ArrayList<ExecutableElement>();
        for(Element e : clzElement.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD && e.getModifiers().containsAll(EnumSet.of(Modifier.STATIC, Modifier.PRIVATE))) {
                shared.add((ExecutableElement)e);
            }
        }
    }

    public Boolean visitMethodInvocation(MethodInvocationTree node, Void v) {
        ExpressionTree methodSelect = node.getMethodSelect();
        if (methodSelect.getKind() == Tree.Kind.IDENTIFIER) {
            String name = ((IdentifierTree)methodSelect).getName().toString();
            int numArgs = 0;
            List<? extends Tree> args = node.getArguments();
            if (args != null) {
                numArgs = args.size();
            }

            if (name.equals("super") || 
                isBTraceMethod(name, numArgs)) {
                return super.visitMethodInvocation(node, v);
            } // else fall through ..
        } else if (methodSelect.getKind() == Tree.Kind.MEMBER_SELECT) {
            String name = ((MemberSelectTree)methodSelect).getIdentifier().toString();
            String clzName = ((MemberSelectTree)methodSelect).getExpression().toString();
            if (clzName.startsWith("BTraceUtils.") ||
                clzName.startsWith("com.sun.btrace.BTraceUtils.") ||
                isBTraceClass(clzName)) {
//                name = name.substring(name.lastIndexOf(".") + 1);
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

    public Boolean visitAssert(AssertTree node, Void v) {
        return reportError("no.asserts", node);        
    }

    public Boolean visitBinary(BinaryTree node, Void v) {
        if (node.getKind() == Tree.Kind.PLUS) {
            Tree left = node.getLeftOperand();
            Tree right = node.getRightOperand();
            if (left.getKind() == Tree.Kind.STRING_LITERAL ||
                right.getKind() == Tree.Kind.STRING_LITERAL) {
                return reportError("no.string.concatenation", node);
            }
        }
        return super.visitBinary(node, v);
    }

    public Boolean visitAssignment(AssignmentTree node, Void v) {        
        if (checkLValue(node.getVariable())) {
            return super.visitAssignment(node, v);
        } else {
            return Boolean.FALSE;
        }
    }

    public Boolean visitCompoundAssignment(CompoundAssignmentTree node, Void v) {
        if (checkLValue(node.getVariable())) {
            return super.visitCompoundAssignment(node, v);
        } else {
            return Boolean.FALSE;
        }
    }

    public Boolean visitCatch(CatchTree node, Void v) {
        return reportError("no.catch", node);        
    }

    public Boolean visitClass(ClassTree node, Void v) {
        // check for local class
        if (insideMethod) {
            return reportError("no.local.class", node);            
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
                if (isStatic) {
                    return reportError("no.static.variables", m);
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
        if (! isPublic(mt.getFlags())) {
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

    public Boolean visitDoWhileLoop(DoWhileLoopTree node, Void v) {
        return reportError("no.do.while", node);        
    }

    public Boolean visitEnhancedForLoop(EnhancedForLoopTree node, Void v) {
        return reportError("no.enhanced.for", node);        
    }

    public Boolean visitForLoop(ForLoopTree node, Void v) {
        return reportError("no.for.loop", node);        
    }

    public Boolean visitMethod(MethodTree node, Void v) {
        boolean oldInsideMethod = insideMethod;         
        insideMethod = true;
        try {
            Name name = node.getName();
            if (name.contentEquals("<init>")) {
                return super.visitMethod(node, v);
            } else {
                Set<Modifier> flags = node.getModifiers().getFlags();
//                boolean isStatic = isStatic(flags);
//                if (isStatic) {
//                    boolean isPublic = isPublic(node.getModifiers().getFlags());
//                    if (isPublic) {
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

                return super.visitMethod(node, v);
//                    } else {
//                        // force the "public" modifier only on the annotated methods
//                        if (isAnnotated(node)) {
//                            return reportError("method.should.be.public", node);
//                        }
//                        return super.visitMethod(node, v);
//                    }
//                } else {
//                    return reportError("no.instance.method", node);
//                }

            }
        } finally {
            insideMethod = oldInsideMethod;
        }
    }

    public Boolean visitNewArray(NewArrayTree node, Void v) {
        return reportError("no.array.creation", node);        
    }

    public Boolean visitNewClass(NewClassTree node, Void v) {
        return reportError("no.new.object", node);        
    }

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

    public Boolean visitMemberSelect(MemberSelectTree node, Void v) {
        if (node.getIdentifier().contentEquals("class")) {
            return reportError("no.class.literals", node);
        } else {
            return super.visitMemberSelect(node, v);
        }
    }

    public Boolean visitSynchronized(SynchronizedTree node, Void v) {
        return reportError("no.synchronized.blocks", node);        
    }

    public Boolean visitThrow(ThrowTree node, Void v) {
        return reportError("no.throw", node);        
    }

    public Boolean visitTry(TryTree node, Void v) {
        return reportError("no.try", node);
    } 

    public Boolean visitWhileLoop(WhileLoopTree node, Void v)  {
        return reportError("no.while.loop", node);
    }
    
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
}