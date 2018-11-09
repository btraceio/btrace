/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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

package com.sun.btrace;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Library for constructing a dot file format file containing the state of select
 * objects in the current running application.
 *
 * Introduction
 * ============
 *
 * Sometimes when debugging an complex problem, a picture is worth more than a
 * thousand words.  Looking at an object set graphically can simplify understanding
 * of some of the problems.
 *
 * DOTWriter is designed to analyze a set of Java objects and generate a
 * dot format file.  The file can be passed to any number of tools for further
 * analysis.  The multi-platform tools are described at http://graphviz.org/.  The
 * most commonly used are the 'dot' command and the 'Graphviz' application.
 *
 * The 'dot' command can be used to convert a dot format file into any number of
 * visual format files; jpg, png, pdf et cetera.
 *
 * Ex.
 *     dot -Tpdf -osample.pdf sample.dot
 *
 * There are also other tools to convert dot format files to other representations
 * such gxl (using dot2gxl.)
 *
 * Using DOTWriter is straight forward.  Simply construct an DOTWriter instance,
 * add objects to observe, then close the instance.
 *
 * Ex.
 *
 *     import com.sun.btrace.DOTWriter;
 *
 *     var dot = new DOTWriter("sample.dot");
 *     dot.addNodes(a, b, c);
 *     dot.close();
 *
 * There is also a quick and dirty form to do the same.  Note: to get dependency
 * edges you need to build the class file with -XDannobindees
 *
 * Ex.
 *     DOTWriter.graph("sample.dot", a, b, c);
 *
 * The other calls on the public interface allows more detailed control of the
 * graph. Details below.
 *
 * You also have the ability to control dot format properties.
 *
 * Ex.
 *     DOTWriter.graph("sample.dot", "fillcolor=lightblue", a, "fillcolor=lightpink", b, c);
 *
 * Will display 'a' in blue and 'b'/'c' in pink.  Properties are specified in
 * "k=v, ..., k=v" form.
 *
 *
 * Public Interface
 * ================
 *
 * Graphing
 * --------
 *
 * public DOTWriter(String fileName);
 *
 * The constructor creates a file stream for the dot output.  The filename string
 * is the name of the file, and should have the .dot extension.
 *
 *
 * public void close();
 *
 * This method writes the graph to the file stream and closes out the graph.  Any
 * further operations will be ignored.
 *
 *
 * public static void graph(String fileName, Object... objects);
 *
 * All-in-one graph objects.  Specify which objects you want graphed. If an object
 * argument is a String then it is used as the default properties for all object
 * arguments following.
 *
 *
 * public void addNodes(Object... objects);
 *
 * Add a set of objects to the graph.  If an object argument is a string then the
 * string is used as a property string for the remaining object arguments.
 *
 *
 * public void addNode(String propertyString, Object object);
 *
 * Add an individual object to the graph.  The property string defines the dot
 * Properties for the string.  If the object is of primitive data type or null then
 * it will be ignored.
 *
 *
 * public void addEdge(Object head, Object tail);
 * public void addEdge(Object head, Object tail, String propertyString);
 * public void addEdge(Object head, int headFieldId, Object tail, int tailFieldId);
 * public void addEdge(Object head, int headFieldId, Object tail, int tailFieldId, String propertyString);
 *
 * Add Edges to the node.  Field ids indicate which slot to start/end from, -1
 * indicates the whole object.
 *
 *
 *
 * Control Display
 * ----------------
 *
 * public void objectLimit(int objectLimit);
 *
 * The maximum number of objects to display in detail in the graph (default=256.)
 * More objects may be displayed but they will be truncated.  Having a lower limit
 * speeds up the processing of the graph.
 *
 *
 * public void fieldLimit(int fieldLimit);
 *
 * The maximum number of fields to display for an object (default=64.) Having a
 * lower limit speeds up the processing of the graph.
 *
 *
 * public void arrayLimit(int arrayLimit);
 *
 * The maximum number of slots to display for an array (default=32.) Having a
 * lower limit speeds up the processing of the graph.
 *
 *
 * public void stringLimit(int stringLimit);
 *
 * The maximum length of a string to display (default=32.) Having a
 * lower limit speeds up the processing of the graph.
 *
 *
 * public void expandCollections(boolean expandCollections);
 *
 * Controls the display of collections.  By default, collections are displayed
 * as simple arrays. expandCollections == true, displays collections as
 * individual java objects.
 *
 * public void displayStatics(boolean displayStatics)
 *
 * Controls whether static fields are displayed.  By default displayStatics == false.
 *
 *
 * public void displayLinks(boolean displayLinks);
 *
 * Controls whether data links are displayed.  By default displayLinks == true.
 * You may want to set displayLinks = false, if all you want to look at is
 * dependencies.
 *
 * Filtering
 * ---------
 *
 * public void includeObjects(Object... objects);
 *
 * Only objects specified are included in the graph.
 *
 *
 * public void excludeObjects(Object... objects)
 *
 * Objects specified are excluded from the graph.  This may truncated data links
 * as well.
 *
 *
 * public void includeClasses(Class... clazzes);
 *
 * Only objects that are instances of the specified classes are included in the
 * graph.
 *
 * public void excludeClasses(Class... clazzes);
 *
 * Classes to be excluded from the graph.
 *
 * @author Jim Laskey
 *
 */
public class DOTWriter {
    // Property settings for fonts.
    final static String FONTDEFAULTS = "fontname=Helvetica, fontcolor=black, fontsize=10";

    // Default property settings for entire graph.
    final static String GRAPHDEFAULTS = FONTDEFAULTS + ", rankdir=LR";

    // Default property settings for nodes.
    final static String NODEDEFAULTS = FONTDEFAULTS + ", label=\"\\N\", shape=record, style=filled, fillcolor=lightgrey, color=black";

    // Default property settings for edges.
    final static String EDGEDEFAULTS = FONTDEFAULTS + ", arrowhead=open";

    // Style of starting node.
    final static String STARTNODESTYLE = "fillcolor=pink";

    // Style of intra dependency edge.
    final static String INTRAEDGESTYLE = "style=dashed, color=grey";

    // Style of inter dependency edge.
    final static String INTEREDGESTYLE = "style=dashed, color=darkGrey";
    private final DotWriterFormatter dotWriterFormatter = new DotWriterFormatter();

    // Maximum number of objects displayed.
    private int objectLimit = 256;

    // Maximum number of field entries displayed.
    private int fieldLimit = 64;

    // Maximum number of array entries displayed.
    private int arrayLimit = 32;

    // Map of visited objects.
    private Map<Object, Node> visited = new IdentityHashMap<Object, Node>();

    // Cache of field information.
    Map<Class, Field[]> fieldCache = new HashMap<Class, Field[]>();

    // Graph properties.
    private Properties graphProperties = new Properties();

    // Default node properties.
    private Properties nodeProperties = new Properties();

    // Default edge properties.
    private Properties edgeProperties = new Properties();

    // List of nodes.
    private List<Node> nodes = new ArrayList<Node>();

    // List of edges.
    private List<Edge> edges = new ArrayList<Edge>();

    // Output stream.
    private PrintStream dotStream;

    // True if filters are active.
    private boolean filtering = false;

    // Include set of instances.
    private Set<Object> includeObjects = new HashSet<Object>();

    // Exclude set of instances.
    private Set<Object> excludeObjects = new HashSet<Object>();

    // Include List of classes.
    private List<Class> includeClasses = new ArrayList<Class>();

    // Include classes which matching name pattern
    private Pattern includeClassNames;

    // Exclude List of classes.
    private List<Class> excludeClasses = new ArrayList<Class>();

    // Excluse classes with matching name pattern
    private Pattern excludeClassNames;

    // True if collections should be expanded.
    private boolean expandCollections = false;

    // True if static fields should be displayed.
    private boolean displayStatics = false;

    // True if object links should be shown.
    private boolean displayLinks = true;

    // This class maintains properties.
    static class Properties {
        // Property map.
        private Map<String, String> properties = new HashMap<String, String>();

        // Adds a new property to the map.
        void addProperty(String key, String value) {
            properties.put(key, value);
        }

        // Adds new properties from a "key=value, key=value" string.
        void addProperties(String propertyString) {
            // Split on the commas.
            String[] kvs = propertyString.split("\\s*,\\s*");

            // For each key=value pair.
            for (String kv : kvs) {
                // Split on the equals.
                String[] pair = kv.split("\\s*=\\s*");

                // If no equals.
                if (pair.length == 1) {
                    properties.put(pair[0], "true");
                } else {
                    properties.put(pair[0], pair[1]);
                }
            }
        }

        // Adds new information to a property.
        void extendProperty(String key, String extension) {
            // Get existing property
            String value = getProperty(key);
            // If no property exists then start one.
            if (value == null) value = "";
            // Add the extension to the value.
            value += extension;
            // Update the property.
            addProperty(key, value);
        }

        // Return the value of the property, null if not found.
        String getProperty(String key) {
            return properties.get(key);
        }

        // Write properties to a stream in the form "[key=value, ..., key=value]".
        void writeProperties(PrintStream dotStream) {
            // Only if there are properties.
            if (!properties.isEmpty()) {
                dotStream.print("[");

                String comma = "";
                for (String key : properties.keySet()) {
                    String value = properties.get(key);

                    // true properties don't require a value.
                    if (value.equals("true")) {
                        dotStream.print(comma + key);
                    } else {
                        dotStream.print(comma + key + "=" + escapeString(value));
                    }

                    comma = ", ";
                }

                dotStream.print("]");
            }
        }
    }


    // This class maintains information about a graph element.
    static class Element extends Properties {
        // Identifying number.
        int id;

        Element(int id) {
            this.id = id;
        }
    }


    // This class maintains information about a node.
    static class Node extends Element {
         // Subject of the node.
        Object object;

         Node(int id, Object object) {
            super(id);
            this.object = object;
        }
    }


    // This class maintains information about an edge.
    static class Edge extends Element {
         // Beginning and end of edge.
         Node head;
         int headFieldId;
         Node tail;
         int tailFieldId;

         Edge(int id, Node head, int headFieldId, Node tail, int tailFieldId) {
            super(id);
            this.head = head;
            this.headFieldId = headFieldId;
            this.tail = tail;
            this.tailFieldId = tailFieldId;
        }
    }


    // This class makes simplifies the display of values.
    class Format {
        // String representation of value.
        String string = "";

        // True if the value is simple and can't be referenced externally.
        boolean isSimple = true;

        Format(Object value) {
            if (value == null) {
                // nulls as is, but may be referenced .
                string += value;
                isSimple = false;
            } else if (value instanceof String) {
                // Strings in double quotes.
                string = dotWriterFormatter.formatString((String) value, "\"");
            } else if (value instanceof char[]) {
                // char arrays as single quote strings.
                string = dotWriterFormatter.formatString(new String((char[]) value), "\'");
            } else if (value instanceof byte[] && allASCII((byte[])value)) {
                // byte arrays of ascii characters as single quote strings.
                string = dotWriterFormatter.formatString(new String((byte[]) value, StandardCharsets.UTF_8), "\'");
            } else if (value instanceof Character) {
                // Quote characters.
                Character character = (Character)value;
                string = "\'" + character + "\'";
            } else if (!expandCollections && value instanceof Map.Entry) {
                // Map entries as key->value.
                Map.Entry entry = (Map.Entry)value;
                Format keyFormat = new Format(entry.getKey());
                Format valueFormat = new Format(entry.getValue());
                if (!keyFormat.isSimple) {
                    isSimple = false;
                } else {
                    string = keyFormat.string + "-\\>";

                    if (valueFormat.isSimple) {
                        string += valueFormat.string;
                    } else {
                         isSimple = false;
                    }
                }
            } else if (isPrimitive(value)) {
                // Primitive types as is.
                string += value;
            } else {
                // Reference to another object.
                isSimple = false;
            }
        }
    }


    public DOTWriter(String fileName) {
        try {
            dotStream = new PrintStream(fileName);
        } catch (Throwable ex) {
        }

        // Set up default properties.
        graphProperties.addProperties(GRAPHDEFAULTS);
        nodeProperties.addProperties(NODEDEFAULTS);
        edgeProperties.addProperties(EDGEDEFAULTS);
    }

    public static final String DOTWRITER_PREFIX = "dotwriter.";
    public void customize(java.util.Properties props) {
        String prop = props.getProperty(DOTWRITER_PREFIX + "objectlimit");
        if (prop != null) {
            this.objectLimit(Integer.parseInt(prop));
        }
        prop = props.getProperty(DOTWRITER_PREFIX + "fieldLimit");
        if (prop != null) {
            this.fieldLimit(Integer.parseInt(prop));
        }
        prop = props.getProperty(DOTWRITER_PREFIX + "stringLimit");
        if (prop != null) {
            dotWriterFormatter.stringLimit(Integer.parseInt(prop));
        }
        prop = props.getProperty(DOTWRITER_PREFIX + "arrayLimit");
        if (prop != null) {
            this.arrayLimit(Integer.parseInt(prop));
        }
        prop = props.getProperty(DOTWRITER_PREFIX + "expandCollections");
        if (prop != null) {
            this.expandCollections(Boolean.parseBoolean(prop));
        }
        prop = props.getProperty(DOTWRITER_PREFIX + "diaplayLinks");
        if (prop != null) {
            this.displayLinks(Boolean.parseBoolean(prop));
        }
        prop = props.getProperty(DOTWRITER_PREFIX + "displayStatics");
        if (prop != null) {
            this.displayStatics(Boolean.parseBoolean(prop));
        }
        prop = props.getProperty(DOTWRITER_PREFIX + "excludeClassNames");
        if (prop != null) {
            this.excludeClassNames(Pattern.compile(prop));
        }
        prop = props.getProperty(DOTWRITER_PREFIX + "includeClassNames");
        if (prop != null) {
            this.includeClassNames(Pattern.compile(prop));
        }
    }

    // All-in-one graph objects.  Specify which objects you want graphed.
    // If an object is a String then it is used as the default properties
    // for all objects following.
    public static void graph(String fileName, Object... objects) {
        // Open the dot file.
        DOTWriter writer = new DOTWriter(fileName);
        // Hilight starting nodes in pink.
        String propertyString = STARTNODESTYLE;

        // For each argument.
        for (Object object : objects) {
            // If string the swap default properties.
            if (object instanceof String) {
                propertyString = (String)object;
            } else {
                writer.addNode(propertyString, object);
            }
        }

        // Dump and close out the dot file.
        writer.close();
    }

    // Add a set of nodes to the graph.  If the object is a string then the string
    // is used as a property string for the remaining objects.
    public void addNodes(Object... objects) {
        String propertyString = null;

        for (Object object : objects) {
            if (object instanceof String) {
                propertyString = (String)object;
            } else {
                addNode(propertyString, object);
            }
        }
    }

    // Add an object to the graph.
    public void addNode(String propertyString, Object object) {
        // No nulls in the graph.
        if (object == null) return;
        // No primitive types in the graph.
        if (isPrimitive(object)) return;

        // Capture where to continue graphing.
        int index = nodes.size();
        // Add the new node or get the existing one.
        Node newNode = getNode(object);

        // While the node list is not exhausted.
        for ( ; index < nodes.size(); index++) {
            // get the next node.
            Node node = nodes.get(index);
            // Get the node object.
            object = node.object;
            // Get the object class.
            Class clazz = object.getClass();
            // Add object header to label.
            addHeader(object, clazz, node);

            // If the object is an array.
            if (clazz.isArray()) {
                // Display detail if under object limit and not excluded.
                if (index < arrayLimit && shouldDetail(object)) {
                    addArrayDetail(object, node);
                } else {
                    // Indicate there is more than displayed.
                    addContinuation(node);
                }
            } else {
                // Display detail if under object limit and not excluded.
                if (index < objectLimit && shouldDetail(object)) {
                    if (!expandCollections && object instanceof Collection) {
                        // Display collection as an array of enties.
                        addCollectionDetail(object, clazz, node);
                    } else if (!expandCollections && object instanceof Map) {
                        // Display map as an array of key->value.
                        addMapDetail(object, clazz, node);
                    } else if (displayStatics && object instanceof Class) {
                        // Display class static fields.
                        addClassDetail(object, clazz, node);
                    } else {
                        // Display as a detailed java object.
                        addObjectDetail(object, clazz, node);
                    }
                } else {
                    // Indicate there is more than displayed.
                    addContinuation(node);
                }
            }
        }

        // Add properties to the node if present.
        if (propertyString != null) {
            newNode.addProperties(propertyString);
        }
    }

    // Set maximum number of objects displayed in detail.
    public void objectLimit(int objectLimit) {
        this.objectLimit = objectLimit;
    }

    // Set maximum number of field entries displayed.
    public void fieldLimit(int fieldLimit) {
        this.fieldLimit = fieldLimit;
    }

    // Set maximum number of array entries displayed.
    public void arrayLimit(int arrayLimit) {
        this.arrayLimit = arrayLimit;
    }

    // Set maximum number of string characters displayed
    public void stringLimit(int stringLimit) {
        dotWriterFormatter.stringLimit(stringLimit);
    }

    // Control the switching of collections from detail to expanded.
    public void expandCollections(boolean expandCollections) {
        this.expandCollections = expandCollections;
    }

    // Control whether static fields should be shown.
    public void displayStatics(boolean displayStatics) {
        this.displayStatics = displayStatics;
    }

    // Control whether object links should be shown.
    public void displayLinks(boolean displayLinks) {
        this.displayLinks = displayLinks;
    }

    // Add objects to the include list.
    public void includeObjects(Object... objects) {
        includeObjects.addAll(Arrays.asList(objects));
        filtering = true;
    }

    // Add objects to the exclude list.
    public void excludeObjects(Object... objects) {
        excludeObjects.addAll(Arrays.asList(objects));
        filtering = true;
    }

    // Add classes to the include list.
    public void includeClasses(Class... clazzes) {
        includeClasses.addAll(Arrays.asList(clazzes));
        filtering = true;
    }

    public void includeClassNames(Pattern pattern) {
        includeClassNames = pattern;
        filtering = true;
    }

    // Adds classes to the exclude list.
    public void excludeClasses(Class... clazzes) {
        excludeClasses.addAll(Arrays.asList(clazzes));
        filtering = true;
    }

    public void excludeClassNames(Pattern pattern) {
        excludeClassNames = pattern;
        filtering = true;
    }

    // This method adds a new edge to the graph.
    public void addEdge(Object head, Object tail) {
        addEdge(head, -1, tail, -1, null);
    }
    public void addEdge(Object head, Object tail, String propertyString) {
        addEdge(head, -1, tail, -1, propertyString);
    }
    public void addEdge(Object head, int headFieldId, Object tail, int tailFieldId) {
        addEdge(head, headFieldId, tail, tailFieldId, null);
    }
    public void addEdge(Object head, int headFieldId, Object tail, int tailFieldId, String propertyString) {
        addEdge(getNode(head), headFieldId, getNode(tail), tailFieldId, propertyString);
    }
    private void addEdge(Node head, int headFieldId, Node tail, int tailFieldId) {
        addEdge(head, headFieldId, tail, tailFieldId, null);
    }

    // Write the graph and close the dot file.
    public void close() {
        if (dotStream != null) {
            writeGraph();
            dotStream.close();
            dotStream = null;
        }
    }

    //
    // Private interface.
    //

    // Return true if the object is a primitive type.
    private boolean isPrimitive(Object object) {
        return object.getClass().isPrimitive() || object instanceof Number || object instanceof Boolean || object instanceof String;
    }

    // Write the graph to the stream.
    private void writeGraph() {
        dotStream.println("digraph g {");

        dotStream.print(" graph ");
        graphProperties.writeProperties(dotStream);
        dotStream.println(";");

        dotStream.print(" node ");
        nodeProperties.writeProperties(dotStream);
        dotStream.println(";");

        dotStream.print(" edge ");
        edgeProperties.writeProperties(dotStream);
        dotStream.println(";");

        for (Node node : nodes) {
            dotStream.print(" node" + node.id + " ");
            node.writeProperties(dotStream);
            dotStream.println(";");
        }

        for (Edge edge : edges) {
            dotStream.print(" node" + edge.head.id + (edge.headFieldId < 0 ? ":f" : ":f" + edge.headFieldId));
            dotStream.print(" -> ");
            dotStream.print(" node" + edge.tail.id + (edge.tailFieldId < 0 ? ":f" : ":f" + edge.tailFieldId));
            dotStream.print(" ");
            edge.writeProperties(dotStream);
            dotStream.println(";");
        }

        dotStream.println("}");
    }

    // Add an object to the node work list.
    private Node getNode(Object object) {
        // Don't add nulls to graph.
        if (object == null) return null;

        // Some object types fail mapping.
        Node node = null;
        try {
            node = visited.get(object);
        } catch (Throwable ex) {
            return null;
        }

        // If the node is not found add one.
        if (node == null) {
            node = new Node(nodes.size(), object);
            nodes.add(node);
            visited.put(object, node);
        }

        return node;
    }

    // Determine if a node should be detailed based on object and class filters.
    private boolean shouldDetail(Object object) {
        if (object == this) return false;
        if (!filtering) return true;

        if (!includeObjects.isEmpty()) {
            return includeObjects.contains(object);
        }

        if (!includeClasses.isEmpty()) {
            for (Class clazz : includeClasses) {
                if (clazz.isInstance(object)) return true;
            }

            return false;
        }

        if (includeClassNames != null) {
            return includeClassNames.matcher(object.getClass().getName()).matches();
        }

        if (!excludeObjects.isEmpty()) {
            if (excludeObjects.contains(object)) return false;
        }

        if (!excludeClasses.isEmpty()) {
            for (Class clazz : excludeClasses) {
                if (clazz.isInstance(object)) return false;
            }
        }

        if (excludeClassNames != null) {
            return !excludeClassNames.matcher(object.getClass().getName()).matches();
        }
        return true;
    }

    // Add a new edge to the graph.
    private void addEdge(Node head, int headFieldId, Node tail, int tailFieldId, String propertyString) {
        // Exclude null nodes.
        if (head == null || tail == null) return;
        // Exclude edges to nodes that disn't get displayed.
        if (headFieldId > getLimit(head) && head.id >= objectLimit) headFieldId = -1;
        if (tailFieldId > getLimit(tail) && tail.id >= objectLimit) tailFieldId = -1;

        // Create edge.
        Edge edge = new Edge(edges.size(), head, headFieldId, tail, tailFieldId);
        // Add properties if present.
        if (propertyString != null) edge.addProperties(propertyString);
        // Add to edge list.
        edges.add(edge);
    }

    // Return the field/slot limit for a node's object.
    private int getLimit(Node node) {
        return node.object.getClass().isArray() ? arrayLimit : fieldLimit;
    }

    // Return a displayable name for the specified class.
    private String getClassName(Class clazz) {
        String name = clazz.getCanonicalName();
        if (name == null) name = clazz.getName();
        if (name == null) name = clazz.getSimpleName();
        return name;
    }

    // Add the header information about the object.
    private void addHeader(Object object, Class clazz, Node node) {
        // Use object if displaying statics.
        if (displayStatics && object instanceof Class) clazz = (Class)object;
        // Use name as title.
        String className = getClassName(clazz);

        if (object instanceof Class) {
            // Flag Class objects.
            className += "(Class)";
        }

        // Start label for node.
        node.addProperty("label", "<f> " + className);
    }

    // Indicate that the object has more fields.
    private void addContinuation(Node node) {
        node.extendProperty("label", " | ...");
    }

    // Return the value of a field.
    private Object getValue(Field field, Object object) {
        Object value = null;

        try {
            value = field.get(object);
        } catch(Throwable ex) {
        }

        return value;
    }

    // Return an array of all the fields in a given class.
    private Field[] getFields(Class clazz) {
        if (clazz == null) return new Field[0];
        if (displayStatics && clazz == Class.class) return new Field[0];

        Field[] fields = fieldCache.get(clazz);
        if (fields != null) return fields;

        Field[] superFields = getFields(clazz.getSuperclass());
        Field[] classFields = clazz.getDeclaredFields();

        fields = new Field[superFields.length + classFields.length];
        System.arraycopy(superFields, 0, fields, 0, superFields.length);
        System.arraycopy(classFields, 0, fields, superFields.length, classFields.length);

        fieldCache.put(clazz, fields);

        return fields;
    }

    // Add the detail information about the object.
    private void addObjectDetail(Object object, Class clazz, Node node) {
        Field[] fields = getFields(clazz);

        if (displayStatics && fields.length != 0) {
            addEdge(node, -1, getNode(clazz), -1);
        }

        for (int index = 0; index < fields.length && index < fieldLimit; index++) {
            Field field = fields[index];
            // Ignore static fields.
            if ((field.getModifiers() & Modifier.STATIC) != 0) continue;

            // Add record row for field.
            field.setAccessible(true);
            String name = field.getName();
            Object value = getValue(field, object);
            Format format = new Format(value);
            addNodeField(node, name, index, format.string);

            // If linking to another object then add edge.
            if (displayLinks && !format.isSimple) {
                addEdge(node, index, getNode(value), -1);
            }
        }

        // Indicate if object is too big to display.
        if (fields.length >= fieldLimit) {
            addContinuation(node);
        }
    }

    // Add the detail information about a class.
    private void addClassDetail(Object object, Class clazz, Node node) {
        Field[] fields = getFields((Class)object);

        for (int index = 0; index < fields.length && index < fieldLimit; index++) {
            Field field = fields[index];
            // Only look at static fields.
            if ((field.getModifiers() & Modifier.STATIC) == 0) continue;

            // Add record row for field.
            field.setAccessible(true);
            String name = field.getName();
            Object value = getValue(field, object);
            Format format = new Format(value);
            addNodeField(node, name, index, format.string);

            // If linking to another object then add edge.
            if (displayLinks && !format.isSimple) {
                addEdge(node, index, getNode(value), -1);
            }
        }

        // Indicate if object is too big to display.
        if (fields.length >= fieldLimit) {
            addContinuation(node);
        }
    }

    // Add the detail information about a collection.
    private void addCollectionDetail(Object object, Class clazz, Node node) {
        // Convert colection to array and display as array.
        Object[] array = ((Collection)object).toArray();
        addArrayDetail(array, node);
    }

    // Add the detail information about a map.
    private void addMapDetail(Object object, Class clazz, Node node) {
        // Convert map to array and display as array.
        Object[] array = ((Map)object).entrySet().toArray();
        addArrayDetail(array, node);
    }

    // Add the detail information about the array.
    private void addArrayDetail(Object object, Node node) {
        int length = Array.getLength(object);

        if (object instanceof char[] || (object instanceof byte[] && allASCII((byte[])object))) {
            // If char or displayable byte array then display as string.
            Format format = new Format(object);
            String string = " | " + format.string;
            node.extendProperty("label", string);
            length = 0;
        } else {
            // One line of record per slot in array.
            for (int index = 0; index < length && index < arrayLimit; index++) {
                // Add record row for slot.
                Object value = Array.get(object, index);
                Format format = new Format(value);
                addNodeField(node, null, index, format.string);

                // If linking to another object then add edge.
                if (displayLinks && !format.isSimple) {
                    addEdge(node, index, getNode(value), -1);
                }
            }
        }

        // Indicate if object is too big to display.
        if (length >= arrayLimit) {
            addContinuation(node);
        }
    }

    // Add a field to a node.
    private void addNodeField(Node node, String fieldName, int fieldId, String valueString) {
        // Start record row.
        String labelString = " | ";
        // Add report port for edges.
        labelString += "<f" + fieldId + "> ";
        // Add field name.
        if (fieldName != null) labelString += fieldName + ": ";
        // Add value.
        labelString += valueString;
        // Add to record description.
        node.extendProperty("label", labelString);
    }

    // Escape a quoted value string for output.
    private static String escapeString(String value) {
        if (needsEscape(value)) {
            final StringBuilder result = new StringBuilder();
            result.append('\"');
            final StringCharacterIterator iterator = new StringCharacterIterator(value);

            for (char ch = iterator.current(); ch != CharacterIterator.DONE; ch = iterator.next()) {
                if (ch == '\"' ||
                    ch == '\'' ||
                    ch == '{' ||
                    ch == '}' ||
                    ch == '[' ||
                    ch == ']') {
                    result.append('\\');
                    result.append(ch);
                } else if (ch < ' ' || ch > '~') {
                    result.append("\\\\u");
                    String hex = "0000" + Integer.toHexString((int)ch);
                    hex = hex.substring(hex.length() - 4);
                    result.append(hex);
                } else {
                    result.append(ch);
                }

            }

            result.append('\"');
            value = result.toString();
        }

        return value;
    }

    // Return true if the string needs to be quoted.
    private static boolean needsEscape(String value) {
        if (value.length() > 0 && value.charAt(0) == '\"') return false;

        final StringCharacterIterator iterator = new StringCharacterIterator(value);

        for (char ch = iterator.current(); ch != CharacterIterator.DONE; ch = iterator.next()) {
            if (!Character.isJavaIdentifierPart(ch) && ch != '-') {
                return true;
            }
        }

        return false;
    }

    // Return true if all the characters in the array are ASCII characters.
    private boolean allASCII(byte[] array) {
        for (byte b : array) {
            char ch = (char)b;
            if (ch < ' ' && '~' < ch) return false;
        }

        return false;
    }
}
