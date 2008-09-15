/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package net.java.btrace.visualvm.views;

import java.awt.BorderLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 *
 * @author Jaroslav Bachorik
 */
public class OutputPane extends JPanel {

    private JTextArea textArea;
    private JScrollPane scroller;
    final private PrintWriter writer;
    final private ExecutorService printService = Executors.newCachedThreadPool();

    public OutputPane(Reader outputSource) {
        this();
        startReaderTask(outputSource);
    }
    
    public OutputPane() {
        setLayout(new BorderLayout());

        initComponents();
        try {
            PipedReader sink = new PipedReader();
            PipedWriter src = new PipedWriter(sink);
            writer = new PrintWriter(src, true);
            startReaderTask(sink);
            
        } catch (IOException e) {
            throw new ExceptionInInitializerError();
        }
    }

    private void startReaderTask(Reader reader) {
        final BufferedReader bReader = new BufferedReader(reader);
        
        printService.submit(new Runnable() {

                public void run() {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            String line = bReader.readLine();
                            if (line == null) break;
                            textArea.append(line + "\n");
                            textArea.setCaretPosition(textArea.getText().length());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
    }
    
    private void initComponents() {
        textArea = new JTextArea();
        textArea.setBorder(BorderFactory.createEmptyBorder());
        textArea.setOpaque(false);
        scroller = new JScrollPane(textArea);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.setOpaque(false);
        scroller.setViewportBorder(BorderFactory.createEmptyBorder());
        add(scroller, BorderLayout.CENTER);
    }

    public PrintWriter getWriter() {
        return writer;
    }

    public void clear() {
        textArea.setText("");
    }
}
