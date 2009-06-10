/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.btrace;

import java.io.IOException;
import java.io.Writer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.UnsupportedCharsetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.IdentityHashMap;

/**
 * This class serializes an object to XML. This class 
 * handles circular references by generating ID and IDREF 
 * attributes.
 * 
 * @author A. Sundararajan
 */
final class XMLSerializer {
    // do not create me!
    private XMLSerializer() {}

    private static final String ID = "id";
    private static final String IDREF = "idref";
    private static final String CLASS = "class";

    /**
     * Write an object as an XML document to the out. 
     */      
    public static void write(Object obj, Writer out) throws IOException {
        if (obj == null || out == null) {
            throw new NullPointerException();
        }
        Serializer s = new Serializer(out);
        s.write(obj);
        out.flush();
    }

    /**
     * Return XML document string for the given object. 
     */            
    public static String toXML(Object obj) throws IOException {
        if (obj == null) {
            throw new NullPointerException();
        }
        StringWriter sw = new StringWriter();
        write(obj, sw);
        return sw.toString();
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            System.out.println(toXML(args));
        } else {
            System.out.println(toXML(XMLSerializer.class));
        }
    }

    // class that handles XML serialization
    private static class Serializer {  
        private static CharsetEncoder encoder = Charset.forName("8859_1").newEncoder();

        // out on which we will write XML
        private PrintWriter out;
        // map to maintain objects serialized already    
        private Map<Object, String> objToId;
        // next object id (unique id for objects)
        private long nextId;

        Serializer(Writer writer) {
            if (writer instanceof PrintWriter) {
                this.out = (PrintWriter)writer;
            } else {
                this.out = new PrintWriter(writer);
            }            
            this.objToId = 
                    new IdentityHashMap<Object, String>();
            writeln("<?xml version='1.0' encoding='ISO-8859-1'?>");
        }

        void write(Object obj) {
            if (obj instanceof Class) {
                write("class", obj);
            } else {
                write("object", obj);
            }
        }

        void write(String name, Object obj) {
            name = encodeTagName(name);
            // check null
            if (obj == null) {
                out.print('<');
                out.print(name);
                out.print(">null</");
                out.print(name);
                writeln('>');
                return;
            }

            // check for circularity
            if (hasSeenAlready(name, obj)) {
                return;
            }

            Class clazz = obj.getClass();      
            if (clazz.isArray()) {
                writeArray(name, obj);
            } else {
                writeObject(name, obj);
            }
        }

        private String nextObjectId(Object obj) {
            return Long.toString(nextId++);
        }

        private void writeln(String str) {
            out.print(str);
            out.print("\r\n");
        }

        private void writeln(char ch) {
            out.print(ch);
            out.print("\r\n");
        }

        private void writeln() {
            out.print("\r\n");
        }

        private void writeAttribute(String name, String value) {
            out.print(name);
            out.print("=\"");
            out.print(value);
            out.print("\"");
        }

        private void writeIdProperty(Object obj) {
            String id = nextObjectId(obj);
            writeAttribute(ID, id);
            objToId.put(obj, id);
        }

        private boolean hasSeenAlready(String name, Object obj) {
            String id = objToId.get(obj);
            if (id != null) {
                out.print('<');
                out.print(name);
                out.print(' ');
                writeAttribute(IDREF, id);
                writeln("/>");
                return true;
            } else {
                return false;
            }
        }

        private void arrayStart(String name, Object obj) {
            objectStart(name, obj);
        }

        private void arrayEnd(String name, Object obj) {
            objectEnd(name, obj);
        }

        private void writeArray(String name, Object array) {
            arrayStart(name, array);
            final int len = Array.getLength(array);
            if (len == 0) {
                arrayEnd(name, array);
                return;
            }
            Class clazz = array.getClass().getComponentType();
            writeln("<elements>");
            if (clazz.isPrimitive()) {
                if (clazz == Character.TYPE) {
                    out.print(encodeText((char[])array));
                } else {
                    for (int index = 0; index < len; index++) {
                        out.print(Array.get(array, index));
                        if (index != len - 1) {
                            out.print(", ");
                        }
                    }
                }
                writeln();
            } else {
                for (int index = 0; index < len; index++) {
                    write("li", Array.get(array, index));
                }
            }
            writeln("</elements>");
            arrayEnd(name, array);
        }

        private void objectStart(String name, Object obj) {
            out.print('<');
            out.print(name);
            out.print(' ');
            writeIdProperty(obj);
            writeln('>');
            write(CLASS, obj.getClass());
        }

        private void objectEnd(String name, Object obj) {
            out.print("</");
            out.print(name);
            writeln('>');
        }

        private void writeObject(String name, Object obj) {
            objectStart(name, obj);
            if (obj instanceof Class) {
                Class clazz = (Class)obj;
                writeStaticFields(clazz);
                write("name", clazz.getName());
                Object loader = clazz.getClassLoader();
                if (loader != null) {
                    write("loader", loader);
                }
                Object protDomain = clazz.getProtectionDomain();
                if (protDomain != null) {
                    write("protectionDomain", protDomain);
                }
                Object[] signers = clazz.getSigners();
                if (signers != null && signers.length > 0) {
                    write("signers", signers);
                }
                Class sc = clazz.getSuperclass();
                if (sc != null) {
                    writeln("<extends>");
                    write("class", sc);
                    writeln("</extends>");
                }
                Class[] interfaces = clazz.getInterfaces();
                if (interfaces != null && interfaces.length > 0) {
                    writeln("<implements>");                
                    for (Class cl : interfaces) {
                        write("class", cl);
                    }
                    writeln("</implements>");
                }                
           } else {
                Class clazz = obj.getClass();
                while (clazz != null) {
                    writeFields(obj, clazz);
                    clazz = clazz.getSuperclass();
                }
            }
            objectEnd(name, obj);
        }
  
        private void writeStaticFields(Class clazz) {
            Field[] fields = getAllFields(clazz); 
            if (fields.length == 0) {
                return;
            }
            writeln("<fields>");           
            for (Field f : fields) {     
                int modifiers = f.getModifiers();
                if (! Modifier.isStatic(modifiers)) {
                    continue;
                }
                writeField(f, null);
            }
            writeln("</fields>");
        }

        private void writeFields(Object obj, Class clazz) {          
            Field[] fields = getAllFields(clazz);            
            for (Field f : fields) {     
                int modifiers = f.getModifiers();
                if (Modifier.isStatic(modifiers)) {
                    continue;
                }
                writeField(f, obj);
            }
        }

        private void writeField(Field f, Object obj) {
            Class type = f.getType();
            try {
                if (type.isPrimitive()) {
                    String name = encodeTagName(f.getName());
                    String value;
                    if (type == Character.TYPE) {
                        value = encodeText(f.getChar(obj));
                    } else {
                        value = f.get(obj).toString();
                    }
                    out.print("<");
                    out.print(name);
                    out.print(">");
                    out.print(value); 
                    out.print("</");
                    out.print(name);
                    writeln('>');
                } else {
                    write(f.getName(), f.get(obj));
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception exp) {
                throw new RuntimeException(exp);
            }
        }
        
        private static Field[] getAllFields(final Class clazz) {
            return AccessController.doPrivileged(new PrivilegedAction<Field[]>() {
                public Field[] run() {
                    try {
                        Field[] fields = clazz.getDeclaredFields();
                        for (Field f : fields) {
                            f.setAccessible(true);
                        }
                        return fields;
                    } catch (RuntimeException re) {
                        throw re;
                    } catch (Exception exp) {
                        throw new RuntimeException(exp);
                    }
                }
            });
        }  

        private static String encodeTagName(String str) {
            return str.replace("$", "d-");
        }

        private static String quote(int code) {
            switch(code) {
                case '&':  return "&amp;";
                case '<':  return "&lt;";
                case '>':  return "&gt;";
                case '"':  return "&quot;";
                case '\'': return "&apos;";
                // case '\r': return "&#13;";
                default:   return null;
            }
        }

        private static boolean isValidCharCode(int code) {
            return (0x0020 <= code && code <= 0xD7FF)
                || (0x000A == code)
                || (0x0009 == code)
                || (0x000D == code)
                || (0xE000 <= code && code <= 0xFFFD)
                || (0x10000 <= code && code <= 0x10ffff);
        }

        private static String encodeCode(int code) {
            return "<char cp=\"#" + Integer.toString(code, 16) + "\"/>";
        }

        private static String encodeText(char ch) {
            return encodeText(new char[] { ch });
        }

        private static String encodeText(char[] array) {
            return encodeText(new String(array));
        }

        private static String encodeText(String string) {            
            StringBuilder sb = new StringBuilder();            
            int index = 0;
            final int len = string.length();
            while (index < len) {
                int point = string.codePointAt(index);
                int count = Character.charCount(point);
                if (isValidCharCode(point)) { 
                    if (encoder.canEncode(string.substring(index, index + count))) {
                        String value = quote(point);
                        if (value != null) {
                            sb.append(value);
                        } else {
                            sb.appendCodePoint(point);
                        }
                    } else {
                        sb.append("&#x");
                        sb.append(Integer.toString(point, 16));
                        sb.append(';');
                    }
                    index += count;
                } else {
                    sb.append(encodeCode(string.charAt(index)));
                    index++;
                }
            }            
            return sb.toString();
        }
   }    
}