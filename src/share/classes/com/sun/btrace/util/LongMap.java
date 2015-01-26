/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.btrace.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AVL tree based map implementation for <b>long</b> values as keys
 * @author Jaroslav Bachorik
 * @param <T> The value type
 */
final public class LongMap<T> {
    private final static class Node<T> {
        private final long id;
        private final T load;
        private Node<T> left, right, parent;
        private int balance = 0;

        public Node(long id, T load) {
            this.id = id;
            this.load = load;
        }

        private boolean isLeaningLeft() {
            return balance >= 1;
        }

        private boolean isLeaningRight() {
            return balance <= -1;
        }

        @Override
        public String toString() {
            return "Node{" + "id=" + id + ", load=" + load + ", balance=" + balance + '}';
        }
    }

    public int length = 0;
    private Node<T> root = null;

    public void put(long id, T load) {
        if (root == null) {
            root = new Node<T>(id, load);
            length = 1;
            return;
        }

        Node<T> p = findParent(root, id);

        if (p != null) {
            if (id <= p.id) {
                addLeft(p, new Node<T>(id, load));
            } else {
                addRight(p, new Node<T>(id, load));
            }
            length++;
        }
    }

    public T get(long id) {
        Node<T> n = findNode(root, id);
        return n != null ? n.load : null;
    }

    public T remove(long id) {
        Node<T> n = findNode(root, id);

        if (n != null) {
            removeNode(n);
        }
        if (n != null) length--;
        return n != null ? n.load : null;
    }

    public Collection<T> values() {
        Collection<T> coll = new ArrayList<T>();

        collect(root, coll);
        return coll;
    }

    private void collect(Node<T> root, Collection<T> coll) {
        if (root == null) return;

        collect(root.left, coll);
        coll.add(root.load);
        collect(root.right, coll);
    }

    private Node<T> findNode(Node<T> n, long id) {
        if (n == null) return null;

        if (id == n.id) return n;
        if (id <= n.id) {
            return findNode(n.left, id);
        } else {
            return findNode(n.right, id);
        }

    }

    private Node<T> findParent(Node<T> root, long id) {
        if (id <= root.id) {
            return root.left == null ? root : findParent(root.left, id);
        } else {
            return root.right == null ? root : findParent(root.right, id);
        }
    }

    private void addLeft(Node<T> p, Node<T> n) {
        p.left = n;
        n.parent = p;

        balanceLeft(p, n);
    }

    private void balance(Node<T> n) {
        if (n == null) return;

        Node<T> p = n.parent;

        if (p != null) {
            if (p.left == n) {
                balanceLeft(p, n);
            } else {
                balanceRight(p, n);
            }
        }
    }

    private void balanceLeft(Node<T> p, Node<T> n) {
        if (p.isLeaningLeft()) {
            if (n.isLeaningRight()) {
                rotateLeft(n);
            }
            rotateRight(p);
            return;
        }
        if (p.isLeaningRight()) {
            p.balance = 0;
            return;
        }
        p.balance = 1;
        balance(p);
    }

    private void addRight(Node<T> p, Node<T> n) {
        p.right = n;
        n.parent = p;

        balanceRight(p, n);
    }

    private void balanceRight(Node<T> p, Node<T> n) {
        if (p.isLeaningRight()) {
            if (n.isLeaningLeft()) {
                rotateRight(n);
            }
            rotateLeft(p);
            return;
        }
        if (p.isLeaningLeft()) {
            p.balance = 0;
            return;
        }
        p.balance = -1;
        balance(p);
    }

    private void rotateRight(Node<T> n) {
        Node<T> tmp = n.left;
        n.left = tmp.right;
        if (tmp.right != null) {
            tmp.right.parent = n;
        }

        tmp.right = n;
        tmp.parent = n.parent;
        if (n.parent != null) {
            if (n.parent.left == n) {
                n.parent.left = tmp;
            } else {
                n.parent.right = tmp;
            }
        } else {
            root = tmp;
        }
        n.parent = tmp;

        tmp.balance = 0;
        n.balance = 0;
    }

    private void rotateLeft(Node<T> n) {
        Node<T> tmp = n.right;
        n.right = tmp.left;
        if (tmp.left != null) {
            tmp.left.parent = n;
        }

        tmp.left = n;
        tmp.parent = n.parent;

        if (n.parent != null) {
            if (n.parent.left == n) {
                n.parent.left = tmp;
            } else {
                n.parent.right = tmp;
            }
        } else {
            root = tmp;
        }
        n.parent = tmp;

        tmp.balance = 0;
        n.balance = 0;
    }

    private void removeNode(Node<T> n) {
        Node<T> z = null;

        if ((n.left == null && n.right == null) ||
            (n.left != null && n.right == null) ||
            (n.left == null && n.right != null)) {
            z = n;
        } else {
            Node<T> y = findMax(n.left);
            if (y == n.left) {
                y = findMin(n.right);
            }

            Node<T> yr = y.right;
            Node<T> yl = y.left;
            Node<T> yp = y.parent;

            y.right = n.right;
            y.right.parent = y;
            y.left = n.left;
            y.left.parent = y;
            y.parent = n.parent;

            if (n.parent != null) {
                if (n.parent.left == n) {
                    y.parent.left = y;
                } else {
                    y.parent.right = y;
                }
            } else {
                root = y;
            }
            n.left = yl;
            if (yl != null) {
                n.left.parent = n;
            }
            n.right = yr;
            if (yr != null) {
                n.right.parent = n;
            }
            n.parent = yp;
            if (yp.left == y) {
                yp.left = n;
            } else {
                yp.right = n;
            }
            z = n;
        }
        Node<T> p = z.parent;
        if (z.left == null && z.right == null) {
            if (p == null) {
                root = null;
            } else {
                if (p.left == z) {
                    p.left = null;
                } else {
                    p.right = null;
                }
            }
        } else {
            if (z.left != null) {
                if (p == null) {
                    root = z.left;
                } else {
                    if (p.left == z) {
                        p.left = z.left;
                        z.left.parent = p;
                    } else {
                        p.right = z.left;
                        z.left.parent = p;
                    }
                }
            }
            if (z.right != null) {
                if (p == null) {
                    root = z.right;
                } else {
                    if (p.left == z) {
                        p.left = z.right;
                        z.right.parent = p;
                    } else {
                        p.right = z.right;
                        z.right.parent = p;
                    }
                }
            }
        }
        if (p != null) {
            rebalanceRemoval(p);
        }
    }

    private Node<T> findMax(Node<T> root) {
        return root.right == null ? root : findMax(root.right);
    }

    private Node<T> findMin(Node<T> root) {
        return root.left == null ? root : findMin(root.left);
    }

    private void rebalanceRemoval(Node<T> n) {
        Node<T> p = n.parent;
        if (p == null) {
            return;
        }

        if (p.right == n) {
            if (p.isLeaningLeft()) {
                Node<T> s = p.left;
                int b = s.balance;
                if (s.isLeaningRight()) {
                    rotateLeft(s);
                }
                rotateRight(p);
                if (b == 0) return;
            }
            if (p.balance == 0) {
                p.balance = 1;
                return;
            }
            p.balance = 0;
        } else {
            if (p.isLeaningRight()) {
                Node<T> s = p.right;
                int b = s.balance;
                if (s.isLeaningLeft()) {
                    rotateRight(s);
                }
                rotateLeft(p);
                if (b == 0) return;
            }
            if (p.balance == 0) {
                p.balance = -1;
                return;
            }
            p.balance = 0;
        }
        rebalanceRemoval(p);
    }

    long[] dump() {
        if (root == null) return new long[0];

        long[] arr = new long[length];
        AtomicInteger pos = new AtomicInteger(0);

        dump(root, arr, pos);

        return arr;
    }

    private void dump(Node<T> n, long[] arr, AtomicInteger pos) {
        if (n.left != null) {
            dump(n.left, arr, pos);
        }
        arr[pos.getAndIncrement()] = n.id;
        if (n.right != null) {
            dump(n.right, arr, pos);
        }
    }
}
