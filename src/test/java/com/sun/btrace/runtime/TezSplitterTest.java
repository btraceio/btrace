package com.sun.btrace.runtime;

import org.junit.Test;

public class TezSplitterTest extends InstrumentorTestBase {
    @Test
    public void annotatedClass() throws Exception {
        originalBC = loadTargetClass("classdata/TezSplitter");
        transform("issues/TezSplitter");
        checkTransformation(
            "ICONST_4\n" +
            "ANEWARRAY java/lang/Object\n" +
            "DUP\n" +
            "ICONST_0\n" +
            "ALOAD 1\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_1\n" +
            "ALOAD 2\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_2\n" +
            "ILOAD 3\n" +
            "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_3\n" +
            "ALOAD 4\n" +
            "AASTORE\n" +
            "INVOKESTATIC org/apache/hadoop/mapred/split/TezMapredSplitsGrouper.$btrace$traces$issues$TezSplitter$getGroupedSplitsHook ([Ljava/lang/Object;)V\n" +
            "ICONST_5\n" +
            "ANEWARRAY java/lang/Object\n" +
            "DUP\n" +
            "ICONST_0\n" +
            "ALOAD 1\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_1\n" +
            "ALOAD 2\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_2\n" +
            "ILOAD 3\n" +
            "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_3\n" +
            "ALOAD 4\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_4\n" +
            "ALOAD 5\n" +
            "AASTORE\n" +
            "INVOKESTATIC org/apache/hadoop/mapred/split/TezMapredSplitsGrouper.$btrace$traces$issues$TezSplitter$getGroupedSplitsHook ([Ljava/lang/Object;)V\n" +
            "L2\n" +
            "L3\n" +
            "LINENUMBER 114 L3\n" +
            "L4\n" +
            "LINENUMBER 115 L4\n" +
            "IFLE L5\n" +
            "L6\n" +
            "LINENUMBER 117 L6\n" +
            "L7\n" +
            "LINENUMBER 118 L7\n" +
            "L5\n" +
            "LINENUMBER 121 L5\n" +
            "IFNONNULL L8\n" +
            "L9\n" +
            "LINENUMBER 122 L9\n" +
            "L8\n" +
            "LINENUMBER 125 L8\n" +
            "L10\n" +
            "LINENUMBER 126 L10\n" +
            "L11\n" +
            "LINENUMBER 127 L11\n" +
            "L12\n" +
            "LINENUMBER 129 L12\n" +
            "L13\n" +
            "LINENUMBER 131 L13\n" +
            "L14\n" +
            "LINENUMBER 132 L14\n" +
            "L15\n" +
            "LINENUMBER 134 L15\n" +
            "L16\n" +
            "IF_ICMPGE L17\n" +
            "L18\n" +
            "LINENUMBER 135 L18\n" +
            "L19\n" +
            "LINENUMBER 136 L19\n" +
            "L20\n" +
            "LINENUMBER 137 L20\n" +
            "L21\n" +
            "LINENUMBER 138 L21\n" +
            "L22\n" +
            "LINENUMBER 139 L22\n" +
            "IFNULL L23\n" +
            "IFNE L24\n" +
            "L23\n" +
            "LINENUMBER 140 L23\n" +
            "L25\n" +
            "LINENUMBER 141 L25\n" +
            "L24\n" +
            "LINENUMBER 143 L24\n" +
            "L26\n" +
            "IF_ICMPGE L27\n" +
            "L28\n" +
            "LINENUMBER 144 L28\n" +
            "IFNONNULL L29\n" +
            "L30\n" +
            "LINENUMBER 145 L30\n" +
            "L31\n" +
            "LINENUMBER 146 L31\n" +
            "L29\n" +
            "LINENUMBER 148 L29\n" +
            "IFNE L32\n" +
            "L33\n" +
            "LINENUMBER 149 L33\n" +
            "L32\n" +
            "LINENUMBER 151 L32\n" +
            "L34\n" +
            "LINENUMBER 143 L34\n" +
            "GOTO L26\n" +
            "L27\n" +
            "LINENUMBER 134 L27\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map [Lorg/apache/hadoop/mapred/InputSplit; I I T T T T T T] []\n" +
            "GOTO L16\n" +
            "L17\n" +
            "LINENUMBER 155 L17\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map T T T T T T T T T] []\n" +
            "IFGT L35\n" +
            "IFNULL L35\n" +
            "IFEQ L35\n" +
            "L36\n" +
            "LINENUMBER 163 L36\n" +
            "IFLE L37\n" +
            "GOTO L38\n" +
            "L37\n" +
            "L38\n" +
            "L39\n" +
            "LINENUMBER 164 L39\n" +
            "L40\n" +
            "LINENUMBER 165 L40\n" +
            "L41\n" +
            "LINENUMBER 166 L41\n" +
            "L42\n" +
            "LINENUMBER 168 L42\n" +
            "LSTORE 23\n" +
            "L43\n" +
            "LINENUMBER 170 L43\n" +
            "LSTORE 25\n" +
            "L44\n" +
            "LINENUMBER 173 L44\n" +
            "LSTORE 27\n" +
            "L45\n" +
            "LINENUMBER 176 L45\n" +
            "LLOAD 25\n" +
            "LLOAD 27\n" +
            "IFLT L46\n" +
            "LLOAD 27\n" +
            "IFGT L47\n" +
            "L46\n" +
            "LINENUMBER 178 L46\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map I T T J J J] []\n" +
            "LLOAD 25\n" +
            "LLOAD 27\n" +
            "L47\n" +
            "LINENUMBER 182 L47\n" +
            "LLOAD 23\n" +
            "LLOAD 25\n" +
            "IFLE L48\n" +
            "L49\n" +
            "LINENUMBER 184 L49\n" +
            "LLOAD 25\n" +
            "L50\n" +
            "LINENUMBER 185 L50\n" +
            "LLOAD 23\n" +
            "LLOAD 25\n" +
            "L51\n" +
            "LINENUMBER 192 L51\n" +
            "L52\n" +
            "LINENUMBER 193 L52\n" +
            "GOTO L35\n" +
            "L48\n" +
            "LLOAD 23\n" +
            "LLOAD 27\n" +
            "IFGE L35\n" +
            "L53\n" +
            "LINENUMBER 195 L53\n" +
            "LLOAD 27\n" +
            "L54\n" +
            "LINENUMBER 200 L54\n" +
            "IFNE L55\n" +
            "L56\n" +
            "LINENUMBER 201 L56\n" +
            "L55\n" +
            "LINENUMBER 204 L55\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map I I T J J J] []\n" +
            "LLOAD 23\n" +
            "LLOAD 27\n" +
            "L35\n" +
            "LINENUMBER 215 L35\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map T T T T T T T T T] []\n" +
            "IFNONNULL L57\n" +
            "L58\n" +
            "LINENUMBER 216 L58\n" +
            "L59\n" +
            "LINENUMBER 217 L59\n" +
            "L57\n" +
            "LINENUMBER 220 L57\n" +
            "IFEQ L60\n" +
            "IFEQ L60\n" +
            "IF_ICMPLT L61\n" +
            "L60\n" +
            "LINENUMBER 224 L60\n" +
            "L62\n" +
            "LINENUMBER 226 L62\n" +
            "L63\n" +
            "LINENUMBER 227 L63\n" +
            "ISTORE 23\n" +
            "L64\n" +
            "LINENUMBER 228 L64\n" +
            "ASTORE 24\n" +
            "ALOAD 24\n" +
            "ISTORE 25\n" +
            "ISTORE 26\n" +
            "L65\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map [Lorg/apache/hadoop/mapred/InputSplit; T T I [Lorg/apache/hadoop/mapred/InputSplit; I I T T] []\n" +
            "ILOAD 26\n" +
            "ILOAD 25\n" +
            "IF_ICMPGE L66\n" +
            "ALOAD 24\n" +
            "ILOAD 26\n" +
            "ASTORE 27\n" +
            "L67\n" +
            "LINENUMBER 229 L67\n" +
            "ALOAD 27\n" +
            "L68\n" +
            "LINENUMBER 230 L68\n" +
            "ASTORE 28\n" +
            "L69\n" +
            "LINENUMBER 231 L69\n" +
            "ALOAD 28\n" +
            "ALOAD 27\n" +
            "L70\n" +
            "LINENUMBER 232 L70\n" +
            "ILOAD 23\n" +
            "IINC 23 1\n" +
            "ALOAD 28\n" +
            "L71\n" +
            "LINENUMBER 228 L71\n" +
            "IINC 26 1\n" +
            "GOTO L65\n" +
            "L66\n" +
            "LINENUMBER 234 L66\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map [Lorg/apache/hadoop/mapred/InputSplit; T T I T T T T T] []\n" +
            "L61\n" +
            "LINENUMBER 237 L61\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map T T T T T T T T T] []\n" +
            "L72\n" +
            "LINENUMBER 239 L72\n" +
            "LSTORE 23\n" +
            "L73\n" +
            "LINENUMBER 240 L73\n" +
            "ISTORE 25\n" +
            "L74\n" +
            "LINENUMBER 241 L74\n" +
            "ILOAD 25\n" +
            "ISTORE 26\n" +
            "L75\n" +
            "LINENUMBER 242 L75\n" +
            "ISTORE 27\n" +
            "L76\n" +
            "LINENUMBER 245 L76\n" +
            "ASTORE 28\n" +
            "L77\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List T T J I I I java/util/Iterator] []\n" +
            "ALOAD 28\n" +
            "IFEQ L78\n" +
            "ALOAD 28\n" +
            "L79\n" +
            "LINENUMBER 246 L79\n" +
            "ILOAD 26\n" +
            "L80\n" +
            "LINENUMBER 247 L80\n" +
            "GOTO L77\n" +
            "L78\n" +
            "LINENUMBER 249 L78\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List T T J I I I T] []\n" +
            "ASTORE 28\n" +
            "L81\n" +
            "LINENUMBER 250 L81\n" +
            "ISTORE 29\n" +
            "L82\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List [Lorg/apache/hadoop/mapred/InputSplit; I J I I I java/util/Set I] []\n" +
            "ILOAD 29\n" +
            "IF_ICMPGE L83\n" +
            "ILOAD 29\n" +
            "ASTORE 30\n" +
            "L84\n" +
            "LINENUMBER 251 L84\n" +
            "ALOAD 28\n" +
            "L85\n" +
            "LINENUMBER 252 L85\n" +
            "ALOAD 30\n" +
            "ASTORE 31\n" +
            "L86\n" +
            "LINENUMBER 253 L86\n" +
            "ALOAD 30\n" +
            "ASTORE 32\n" +
            "L87\n" +
            "LINENUMBER 254 L87\n" +
            "ALOAD 32\n" +
            "IFNULL L88\n" +
            "ALOAD 32\n" +
            "IFNE L89\n" +
            "L88\n" +
            "LINENUMBER 255 L88\n" +
            "ASTORE 32\n" +
            "L89\n" +
            "LINENUMBER 257 L89\n" +
            "ALOAD 32\n" +
            "ASTORE 33\n" +
            "ALOAD 33\n" +
            "ISTORE 34\n" +
            "ISTORE 35\n" +
            "L90\n" +
            "ILOAD 35\n" +
            "ILOAD 34\n" +
            "IF_ICMPGE L91\n" +
            "ALOAD 33\n" +
            "ILOAD 35\n" +
            "ASTORE 36\n" +
            "L92\n" +
            "LINENUMBER 258 L92\n" +
            "ALOAD 36\n" +
            "IFNONNULL L93\n" +
            "L94\n" +
            "LINENUMBER 259 L94\n" +
            "ALOAD 7\n" +
            "ASTORE 36\n" +
            "LINENUMBER 261 L93\n" +
            "ALOAD 28\n" +
            "ALOAD 36\n" +
            "L95\n" +
            "LINENUMBER 257 L95\n" +
            "IINC 35 1\n" +
            "GOTO L90\n" +
            "L91\n" +
            "LINENUMBER 263 L91\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List [Lorg/apache/hadoop/mapred/InputSplit; I J I I I java/util/Set I org/apache/hadoop/mapred/InputSplit org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder [Ljava/lang/String; T T T T] []\n" +
            "ALOAD 28\n" +
            "ASTORE 33\n" +
            "L96\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List [Lorg/apache/hadoop/mapred/InputSplit; I J I I I java/util/Set I org/apache/hadoop/mapred/InputSplit org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder [Ljava/lang/String; java/util/Iterator T T T] []\n" +
            "ALOAD 33\n" +
            "IFEQ L97\n" +
            "ALOAD 33\n" +
            "ASTORE 34\n" +
            "L98\n" +
            "LINENUMBER 264 L98\n" +
            "ALOAD 34\n" +
            "ASTORE 35\n" +
            "L99\n" +
            "LINENUMBER 265 L99\n" +
            "ALOAD 35\n" +
            "ALOAD 31\n" +
            "L100\n" +
            "LINENUMBER 266 L100\n" +
            "GOTO L96\n" +
            "L97\n" +
            "LINENUMBER 250 L97\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List [Lorg/apache/hadoop/mapred/InputSplit; I J I I I java/util/Set I T T T T T T T] []\n" +
            "IINC 29 1\n" +
            "GOTO L82\n" +
            "L83\n" +
            "LINENUMBER 269 L83\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List T T J I I I java/util/Set T T T T T T T T] []\n" +
            "L101\n" +
            "LINENUMBER 272 L101\n" +
            "L102\n" +
            "LINENUMBER 275 L102\n" +
            "IFNE L103\n" +
            "IFNE L103\n" +
            "L104\n" +
            "LINENUMBER 276 L104\n" +
            "L103\n" +
            "LINENUMBER 281 L103\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set T T T T T T T T] []\n" +
            "LLOAD 23\n" +
            "ILOAD 25\n" +
            "ILOAD 26\n" +
            "ILOAD 27\n" +
            "L105\n" +
            "LINENUMBER 291 L105\n" +
            "ISTORE 29\n" +
            "L106\n" +
            "LINENUMBER 292 L106\n" +
            "ILOAD 27\n" +
            "ASTORE 30\n" +
            "L107\n" +
            "LINENUMBER 293 L107\n" +
            "ASTORE 31\n" +
            "LINENUMBER 294 L108\n" +
            "ISTORE 32\n" +
            "LINENUMBER 295 L109\n" +
            "ISTORE 33\n" +
            "LINENUMBER 296 L110\n" +
            "ICONST_0\n" +
            "ISTORE 34\n" +
            "L111\n" +
            "LINENUMBER 297 L111\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I T T] []\n" +
            "ILOAD 29\n" +
            "IF_ICMPGE L112\n" +
            "LINENUMBER 298 L113\n" +
            "IINC 34 1\n" +
            "L114\n" +
            "LINENUMBER 299 L114\n" +
            "ISTORE 35\n" +
            "L115\n" +
            "LINENUMBER 300 L115\n" +
            "ASTORE 36\n" +
            "L116\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I java/util/Iterator] []\n" +
            "ALOAD 36\n" +
            "IFEQ L117\n" +
            "ALOAD 36\n" +
            "ASTORE 37\n" +
            "L118\n" +
            "LINENUMBER 301 L118\n" +
            "ALOAD 30\n" +
            "LINENUMBER 302 L119\n" +
            "INVOKEINTERFACE java/util/Set.clear ()V\n" +
            "L120\n" +
            "LINENUMBER 303 L120\n" +
            "ALOAD 37\n" +
            "ASTORE 38\n" +
            "L121\n" +
            "LINENUMBER 304 L121\n" +
            "ALOAD 37\n" +
            "ASTORE 39\n" +
            "L122\n" +
            "LINENUMBER 305 L122\n" +
            "ALOAD 39\n" +
            "ASTORE 40\n" +
            "L123\n" +
            "LINENUMBER 306 L123\n" +
            "ALOAD 40\n" +
            "IFNONNULL L124\n" +
            "L125\n" +
            "LINENUMBER 308 L125\n" +
            "GOTO L116\n" +
            "LINENUMBER 310 L124\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I java/util/Iterator java/util/Map$Entry java/lang/String org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder] []\n" +
            "ALOAD 39\n" +
            "ISTORE 41\n" +
            "L126\n" +
            "LINENUMBER 311 L126\n" +
            "LSTORE 42\n" +
            "L127\n" +
            "LINENUMBER 312 L127\n" +
            "ISTORE 44\n" +
            "L128\n" +
            "LINENUMBER 314 L128\n" +
            "ALOAD 30\n" +
            "ALOAD 40\n" +
            "L129\n" +
            "LINENUMBER 315 L129\n" +
            "LLOAD 42\n" +
            "ALOAD 40\n" +
            "LSTORE 42\n" +
            "LINENUMBER 316 L130\n" +
            "IINC 44 1\n" +
            "L131\n" +
            "LINENUMBER 317 L131\n" +
            "ALOAD 39\n" +
            "L132\n" +
            "LINENUMBER 318 L132\n" +
            "ALOAD 39\n" +
            "ASTORE 40\n" +
            "L133\n" +
            "LINENUMBER 319 L133\n" +
            "ALOAD 40\n" +
            "IFNULL L134\n" +
            "IFEQ L135\n" +
            "LLOAD 42\n" +
            "ALOAD 40\n" +
            "L136\n" +
            "LINENUMBER 321 L136\n" +
            "LLOAD 23\n" +
            "IFGT L134\n" +
            "L135\n" +
            "IFEQ L128\n" +
            "ILOAD 44\n" +
            "ILOAD 27\n" +
            "IF_ICMPLE L128\n" +
            "L134\n" +
            "LINENUMBER 325 L134\n" +
            "ALOAD 39\n" +
            "IFEQ L137\n" +
            "ILOAD 32\n" +
            "IFNE L137\n" +
            "IFEQ L138\n" +
            "LLOAD 42\n" +
            "LLOAD 23\n" +
            "IFGE L137\n" +
            "L138\n" +
            "IFEQ L139\n" +
            "ILOAD 44\n" +
            "ILOAD 27\n" +
            "IF_ICMPGE L137\n" +
            "L139\n" +
            "LINENUMBER 330 L139\n" +
            "ALOAD 39\n" +
            "ILOAD 41\n" +
            "L140\n" +
            "LINENUMBER 331 L140\n" +
            "GOTO L116\n" +
            "L137\n" +
            "LINENUMBER 334 L137\n" +
            "IINC 35 1\n" +
            "L141\n" +
            "LINENUMBER 337 L141\n" +
            "ALOAD 38\n" +
            "ASTORE 45\n" +
            "L142\n" +
            "LINENUMBER 338 L142\n" +
            "ALOAD 38\n" +
            "IF_ACMPNE L143\n" +
            "L144\n" +
            "LINENUMBER 339 L144\n" +
            "ACONST_NULL\n" +
            "ASTORE 45\n" +
            "GOTO L145\n" +
            "LINENUMBER 340 L143\n" +
            "ILOAD 33\n" +
            "IFEQ L145\n" +
            "L146\n" +
            "LINENUMBER 341 L146\n" +
            "ALOAD 30\n" +
            "ASTORE 46\n" +
            "L147\n" +
            "ALOAD 46\n" +
            "IFEQ L148\n" +
            "ALOAD 46\n" +
            "ASTORE 47\n" +
            "L149\n" +
            "LINENUMBER 342 L149\n" +
            "ALOAD 47\n" +
            "ASTORE 48\n" +
            "L150\n" +
            "LINENUMBER 343 L150\n" +
            "ALOAD 48\n" +
            "IFNULL L151\n" +
            "L152\n" +
            "LINENUMBER 344 L152\n" +
            "ALOAD 48\n" +
            "ASTORE 49\n" +
            "ALOAD 49\n" +
            "ISTORE 50\n" +
            "ISTORE 51\n" +
            "L153\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I java/util/Iterator java/util/Map$Entry java/lang/String org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder I J I [Ljava/lang/String; java/util/Iterator org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder [Ljava/lang/String; [Ljava/lang/String; I I] []\n" +
            "ILOAD 51\n" +
            "ILOAD 50\n" +
            "IF_ICMPGE L151\n" +
            "ALOAD 49\n" +
            "ILOAD 51\n" +
            "ASTORE 52\n" +
            "L154\n" +
            "LINENUMBER 345 L154\n" +
            "ALOAD 52\n" +
            "IFNULL L155\n" +
            "L156\n" +
            "LINENUMBER 346 L156\n" +
            "ALOAD 31\n" +
            "ALOAD 52\n" +
            "L155\n" +
            "LINENUMBER 344 L155\n" +
            "FRAME APPEND [T]\n" +
            "IINC 51 1\n" +
            "GOTO L153\n" +
            "L151\n" +
            "LINENUMBER 350 L151\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I java/util/Iterator java/util/Map$Entry java/lang/String org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder I J I [Ljava/lang/String; java/util/Iterator T T T T T T] []\n" +
            "GOTO L147\n" +
            "L148\n" +
            "LINENUMBER 351 L148\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I java/util/Iterator java/util/Map$Entry java/lang/String org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder I J I [Ljava/lang/String; T T T T T T T] []\n" +
            "ALOAD 31\n" +
            "ALOAD 45\n" +
            "ASTORE 45\n" +
            "L145\n" +
            "LINENUMBER 353 L145\n" +
            "ALOAD 30\n" +
            "L157\n" +
            "LINENUMBER 354 L157\n" +
            "ALOAD 45\n" +
            "ILOAD 33\n" +
            "IFEQ L158\n" +
            "ALOAD 38\n" +
            "IF_ACMPEQ L158\n" +
            "ALOAD 38\n" +
            "GOTO L159\n" +
            "L158\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I java/util/Iterator java/util/Map$Entry java/lang/String org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder I J I [Ljava/lang/String; T T T T T T T] [L145 L145 I java/lang/String [Ljava/lang/String;]\n" +
            "L159\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I java/util/Iterator java/util/Map$Entry java/lang/String org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder I J I [Ljava/lang/String; T T T T T T T] [L145 L145 I java/lang/String [Ljava/lang/String; java/lang/String]\n" +
            "ASTORE 46\n" +
            "L160\n" +
            "LINENUMBER 358 L160\n" +
            "ALOAD 30\n" +
            "ASTORE 47\n" +
            "L161\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I java/util/Iterator java/util/Map$Entry java/lang/String org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder I J I [Ljava/lang/String; org/apache/hadoop/mapred/split/TezGroupedSplit java/util/Iterator T T T T T] []\n" +
            "ALOAD 47\n" +
            "IFEQ L162\n" +
            "ALOAD 47\n" +
            "ASTORE 48\n" +
            "L163\n" +
            "LINENUMBER 359 L163\n" +
            "ALOAD 46\n" +
            "ALOAD 48\n" +
            "L164\n" +
            "LINENUMBER 360 L164\n" +
            "ALOAD 48\n" +
            "IFNE L165\n" +
            "GOTO L166\n" +
            "L165\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I java/util/Iterator java/util/Map$Entry java/lang/String org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder I J I [Ljava/lang/String; org/apache/hadoop/mapred/split/TezGroupedSplit java/util/Iterator org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder T T T T] []\n" +
            "L166\n" +
            "ALOAD 38\n" +
            "L167\n" +
            "LINENUMBER 362 L167\n" +
            "ALOAD 48\n" +
            "LINENUMBER 363 L168\n" +
            "IINC 29 1\n" +
            "L169\n" +
            "LINENUMBER 364 L169\n" +
            "GOTO L161\n" +
            "L162\n" +
            "LINENUMBER 365 L162\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I java/util/Iterator java/util/Map$Entry java/lang/String org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder I J I [Ljava/lang/String; org/apache/hadoop/mapred/split/TezGroupedSplit T T T T T T] []\n" +
            "IFEQ L170\n" +
            "L171\n" +
            "LINENUMBER 366 L171\n" +
            "ALOAD 30\n" +
            "ALOAD 46\n" +
            "L172\n" +
            "LINENUMBER 367 L172\n" +
            "ALOAD 38\n" +
            "L173\n" +
            "LINENUMBER 366 L173\n" +
            "L170\n" +
            "LINENUMBER 370 L170\n" +
            "ALOAD 46\n" +
            "L174\n" +
            "LINENUMBER 371 L174\n" +
            "GOTO L116\n" +
            "L117\n" +
            "LINENUMBER 373 L117\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I T T T T T T T T T T T T T T T T T] []\n" +
            "ILOAD 33\n" +
            "IFNE L175\n" +
            "ILOAD 35\n" +
            "IF_ICMPGE L175\n" +
            "L176\n" +
            "LINENUMBER 375 L176\n" +
            "ISTORE 33\n" +
            "L177\n" +
            "LINENUMBER 377 L177\n" +
            "ILOAD 29\n" +
            "ISTORE 36\n" +
            "L178\n" +
            "LINENUMBER 378 L178\n" +
            "ILOAD 36\n" +
            "ASTORE 37\n" +
            "L179\n" +
            "LINENUMBER 380 L179\n" +
            "ASTORE 38\n" +
            "L180\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Iterator T T T T T T T T T T T T T T] []\n" +
            "ALOAD 38\n" +
            "IFEQ L181\n" +
            "ALOAD 38\n" +
            "ASTORE 39\n" +
            "L182\n" +
            "LINENUMBER 381 L182\n" +
            "ALOAD 39\n" +
            "ASTORE 40\n" +
            "L183\n" +
            "LINENUMBER 382 L183\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Iterator java/util/Map$Entry org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder T T T T T T T T T T T T] []\n" +
            "ALOAD 40\n" +
            "IFNE L184\n" +
            "L185\n" +
            "LINENUMBER 383 L185\n" +
            "ALOAD 40\n" +
            "ASTORE 41\n" +
            "L186\n" +
            "LINENUMBER 384 L186\n" +
            "ALOAD 41\n" +
            "IFNULL L187\n" +
            "L188\n" +
            "LINENUMBER 385 L188\n" +
            "ALOAD 37\n" +
            "ALOAD 41\n" +
            "L189\n" +
            "LINENUMBER 386 L189\n" +
            "ALOAD 40\n" +
            "L187\n" +
            "LINENUMBER 388 L187\n" +
            "GOTO L183\n" +
            "L184\n" +
            "LINENUMBER 389 L184\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Iterator T T T T T T T T T T T T T T] []\n" +
            "GOTO L180\n" +
            "L181\n" +
            "LINENUMBER 390 L181\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set T T T T T T T T T T T T T T T] []\n" +
            "ALOAD 37\n" +
            "ILOAD 36\n" +
            "IF_ICMPEQ L190\n" +
            "L191\n" +
            "LINENUMBER 391 L191\n" +
            "ILOAD 36\n" +
            "ALOAD 37\n" +
            "L192\n" +
            "LINENUMBER 392 L192\n" +
            "L190\n" +
            "LINENUMBER 397 L190\n" +
            "L193\n" +
            "LINENUMBER 398 L193\n" +
            "ASTORE 38\n" +
            "L194\n" +
            "LINENUMBER 399 L194\n" +
            "ASTORE 39\n" +
            "L195\n" +
            "LINENUMBER 400 L195\n" +
            "ASTORE 40\n" +
            "L196\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/Iterator T T T T T T T T T T T T] []\n" +
            "ALOAD 40\n" +
            "IFEQ L197\n" +
            "ALOAD 40\n" +
            "ASTORE 41\n" +
            "L198\n" +
            "LINENUMBER 401 L198\n" +
            "ASTORE 42\n" +
            "L199\n" +
            "LINENUMBER 402 L199\n" +
            "ALOAD 41\n" +
            "IF_ACMPEQ L200\n" +
            "L201\n" +
            "LINENUMBER 403 L201\n" +
            "ALOAD 41\n" +
            "ASTORE 42\n" +
            "L200\n" +
            "LINENUMBER 405 L200\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/Iterator java/lang/String java/lang/String T T T T T T T T T T] []\n" +
            "ALOAD 38\n" +
            "ALOAD 41\n" +
            "ALOAD 42\n" +
            "L202\n" +
            "LINENUMBER 406 L202\n" +
            "ALOAD 39\n" +
            "ALOAD 42\n" +
            "IFNONNULL L203\n" +
            "L204\n" +
            "LINENUMBER 408 L204\n" +
            "ALOAD 39\n" +
            "ALOAD 42\n" +
            "ILOAD 36\n" +
            "L203\n" +
            "LINENUMBER 410 L203\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/Iterator T T T T T T T T T T T T] []\n" +
            "GOTO L196\n" +
            "L197\n" +
            "LINENUMBER 411 L197\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map T T T T T T T T T T T T T] []\n" +
            "L205\n" +
            "LINENUMBER 412 L205\n" +
            "ALOAD 39\n" +
            "ASTORE 40\n" +
            "L206\n" +
            "LINENUMBER 413 L206\n" +
            "ALOAD 37\n" +
            "ISTORE 41\n" +
            "L207\n" +
            "LINENUMBER 414 L207\n" +
            "ASTORE 42\n" +
            "ALOAD 42\n" +
            "ISTORE 43\n" +
            "ISTORE 44\n" +
            "L208\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/HashSet I [Lorg/apache/hadoop/mapred/InputSplit; I I T T T T T T T T] []\n" +
            "ILOAD 44\n" +
            "ILOAD 43\n" +
            "IF_ICMPGE L209\n" +
            "ALOAD 42\n" +
            "ILOAD 44\n" +
            "ASTORE 45\n" +
            "L210\n" +
            "LINENUMBER 415 L210\n" +
            "ILOAD 41\n" +
            "IFNE L211\n" +
            "L212\n" +
            "LINENUMBER 416 L212\n" +
            "GOTO L209\n" +
            "LINENUMBER 421 L211\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/HashSet I [Lorg/apache/hadoop/mapred/InputSplit; I I org/apache/hadoop/mapred/InputSplit T T T T T T T] []\n" +
            "ALOAD 37\n" +
            "ALOAD 45\n" +
            "IFNE L213\n" +
            "L214\n" +
            "LINENUMBER 422 L214\n" +
            "GOTO L215\n" +
            "LINENUMBER 424 L213\n" +
            "IINC 41 -1\n" +
            "L216\n" +
            "LINENUMBER 425 L216\n" +
            "ALOAD 40\n" +
            "L217\n" +
            "LINENUMBER 426 L217\n" +
            "ALOAD 45\n" +
            "ASTORE 46\n" +
            "L218\n" +
            "LINENUMBER 427 L218\n" +
            "ALOAD 45\n" +
            "ASTORE 47\n" +
            "L219\n" +
            "LINENUMBER 428 L219\n" +
            "ALOAD 47\n" +
            "IFNULL L220\n" +
            "ALOAD 47\n" +
            "IFNE L221\n" +
            "L220\n" +
            "LINENUMBER 429 L220\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/HashSet I [Lorg/apache/hadoop/mapred/InputSplit; I I org/apache/hadoop/mapred/InputSplit org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder [Ljava/lang/String; T T T T T] []\n" +
            "ASTORE 47\n" +
            "L221\n" +
            "LINENUMBER 431 L221\n" +
            "ALOAD 47\n" +
            "ASTORE 48\n" +
            "ALOAD 48\n" +
            "ISTORE 49\n" +
            "ISTORE 50\n" +
            "L222\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/HashSet I [Lorg/apache/hadoop/mapred/InputSplit; I I org/apache/hadoop/mapred/InputSplit org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder [Ljava/lang/String; [Ljava/lang/String; I I T T] []\n" +
            "ILOAD 50\n" +
            "ILOAD 49\n" +
            "IF_ICMPGE L223\n" +
            "ALOAD 48\n" +
            "ILOAD 50\n" +
            "ASTORE 51\n" +
            "L224\n" +
            "LINENUMBER 432 L224\n" +
            "ALOAD 51\n" +
            "IFNONNULL L225\n" +
            "L226\n" +
            "LINENUMBER 433 L226\n" +
            "ALOAD 7\n" +
            "ASTORE 51\n" +
            "LINENUMBER 435 L225\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/HashSet I [Lorg/apache/hadoop/mapred/InputSplit; I I org/apache/hadoop/mapred/InputSplit org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder [Ljava/lang/String; [Ljava/lang/String; I I java/lang/String T] []\n" +
            "ALOAD 40\n" +
            "ALOAD 38\n" +
            "ALOAD 51\n" +
            "L227\n" +
            "LINENUMBER 431 L227\n" +
            "IINC 50 1\n" +
            "GOTO L222\n" +
            "L223\n" +
            "LINENUMBER 437 L223\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/HashSet I [Lorg/apache/hadoop/mapred/InputSplit; I I org/apache/hadoop/mapred/InputSplit org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder [Ljava/lang/String; T T T T T] []\n" +
            "ALOAD 40\n" +
            "ASTORE 48\n" +
            "L228\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/HashSet I [Lorg/apache/hadoop/mapred/InputSplit; I I org/apache/hadoop/mapred/InputSplit org/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder [Ljava/lang/String; java/util/Iterator T T T T] []\n" +
            "ALOAD 48\n" +
            "IFEQ L215\n" +
            "ALOAD 48\n" +
            "ASTORE 49\n" +
            "L229\n" +
            "LINENUMBER 438 L229\n" +
            "ALOAD 39\n" +
            "ALOAD 49\n" +
            "ALOAD 46\n" +
            "L230\n" +
            "LINENUMBER 439 L230\n" +
            "GOTO L228\n" +
            "L215\n" +
            "LINENUMBER 414 L215\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/HashSet I [Lorg/apache/hadoop/mapred/InputSplit; I I T T T T T T T T] []\n" +
            "IINC 44 1\n" +
            "GOTO L208\n" +
            "L209\n" +
            "LINENUMBER 441 L209\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/HashSet I T T T T T T T T T T T] []\n" +
            "ALOAD 37\n" +
            "L231\n" +
            "LINENUMBER 442 L231\n" +
            "ALOAD 39\n" +
            "L232\n" +
            "LINENUMBER 444 L232\n" +
            "FSTORE 42\n" +
            "L233\n" +
            "LINENUMBER 447 L233\n" +
            "FLOAD 42\n" +
            "IFLE L234\n" +
            "L235\n" +
            "LINENUMBER 448 L235\n" +
            "LLOAD 23\n" +
            "FLOAD 42\n" +
            "LSTORE 43\n" +
            "L236\n" +
            "LINENUMBER 449 L236\n" +
            "ILOAD 27\n" +
            "FLOAD 42\n" +
            "ISTORE 45\n" +
            "L237\n" +
            "LINENUMBER 450 L237\n" +
            "LLOAD 43\n" +
            "IFLE L238\n" +
            "L239\n" +
            "LINENUMBER 451 L239\n" +
            "LLOAD 43\n" +
            "LSTORE 23\n" +
            "LINENUMBER 453 L238\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/HashSet I F J I T T T T T T T] []\n" +
            "ILOAD 45\n" +
            "IFLE L234\n" +
            "L240\n" +
            "LINENUMBER 454 L240\n" +
            "ILOAD 45\n" +
            "ISTORE 27\n" +
            "L234\n" +
            "LINENUMBER 458 L234\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I I java/util/Set java/util/Map java/util/Map java/util/HashSet I F T T T T T T T T T T] []\n" +
            "ILOAD 34\n" +
            "ILOAD 29\n" +
            "ILOAD 35\n" +
            "L241\n" +
            "LINENUMBER 461 L241\n" +
            "LLOAD 23\n" +
            "ILOAD 27\n" +
            "L242\n" +
            "LINENUMBER 458 L242\n" +
            "L243\n" +
            "LINENUMBER 466 L243\n" +
            "GOTO L111\n" +
            "L175\n" +
            "LINENUMBER 469 L175\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I I T T T T T T T T T T T T T T T T T] []\n" +
            "ILOAD 32\n" +
            "IFNE L244\n" +
            "ILOAD 35\n" +
            "ILOAD 25\n" +
            "IF_ICMPGT L244\n" +
            "L245\n" +
            "LINENUMBER 472 L245\n" +
            "ISTORE 32\n" +
            "L246\n" +
            "LINENUMBER 473 L246\n" +
            "ILOAD 34\n" +
            "ILOAD 29\n" +
            "ILOAD 35\n" +
            "L247\n" +
            "LINENUMBER 476 L247\n" +
            "L248\n" +
            "LINENUMBER 473 L248\n" +
            "L244\n" +
            "LINENUMBER 479 L244\n" +
            "IFEQ L249\n" +
            "L250\n" +
            "LINENUMBER 480 L250\n" +
            "ILOAD 34\n" +
            "ILOAD 29\n" +
            "ILOAD 35\n" +
            "L251\n" +
            "LINENUMBER 483 L251\n" +
            "L252\n" +
            "LINENUMBER 480 L252\n" +
            "L249\n" +
            "LINENUMBER 485 L249\n" +
            "FRAME FULL [org/apache/hadoop/mapred/split/TezMapredSplitsGrouper org/apache/hadoop/conf/Configuration [Lorg/apache/hadoop/mapred/InputSplit; I java/lang/String org/apache/hadoop/mapred/split/SplitSizeEstimator I java/lang/String java/lang/String [Ljava/lang/String; I J java/util/Map java/util/List I I J I I I java/util/Set I java/util/List java/util/Set I I I T T T T T T T T T T T T T T T T T T] []\n" +
            "GOTO L111\n" +
            "L112\n" +
            "LINENUMBER 486 L112\n" +
            "ASTORE 35\n" +
            "L253\n" +
            "LINENUMBER 487 L253\n" +
            "ALOAD 35\n" +
            "L254\n" +
            "LINENUMBER 488 L254\n" +
            "L255\n" +
            "LINENUMBER 489 L255\n" +
            "ILOAD 29\n" +
            "L256\n" +
            "LINENUMBER 488 L256\n" +
            "L257\n" +
            "LINENUMBER 491 L257\n" +
            "ALOAD 35\n" +
            "L258\n" +
            "LOCALVARIABLE location Ljava/lang/String; L28 L34 22\n" +
            "LOCALVARIABLE locations [Ljava/lang/String; L22 L27 26\n" +
            "LOCALVARIABLE split Lorg/apache/hadoop/mapred/InputSplit; L18 L27 25\n" +
            "LOCALVARIABLE newDesiredNumSplits I L50 L52 21\n" +
            "LOCALVARIABLE newDesiredNumSplits I L54 L35 21\n" +
            "LOCALVARIABLE splitCount I L39 L35 14\n" +
            "LOCALVARIABLE lengthPerGroup J L43 L35 23\n" +
            "LOCALVARIABLE maxLengthPerGroup J L44 L35 25\n" +
            "LOCALVARIABLE minLengthPerGroup J L45 L35 27\n" +
            "LOCALVARIABLE newSplit Lorg/apache/hadoop/mapred/split/TezGroupedSplit; L69 L71 28\n" +
            "LOCALVARIABLE split Lorg/apache/hadoop/mapred/InputSplit; L67 L71 27\n" +
            "LOCALVARIABLE groupedSplits [Lorg/apache/hadoop/mapred/InputSplit; L63 L61 14\n" +
            "LOCALVARIABLE i I L64 L61 23\n" +
            "LOCALVARIABLE location Ljava/lang/String; L79 L80 21\n" +
            "LOCALVARIABLE location Ljava/lang/String; L92 L95 36\n" +
            "LOCALVARIABLE holder Lorg/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder; L99 L100 35\n" +
            "LOCALVARIABLE location Ljava/lang/String; L98 L100 34\n" +
            "LOCALVARIABLE splitHolder Lorg/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder; L86 L97 31\n" +
            "LOCALVARIABLE locations [Ljava/lang/String; L87 L97 32\n" +
            "LOCALVARIABLE split Lorg/apache/hadoop/mapred/InputSplit; L84 L97 30\n" +
            "LOCALVARIABLE loc Ljava/lang/String; L154 L155 52\n" +
            "LOCALVARIABLE locations [Ljava/lang/String; L150 L151 48\n" +
            "LOCALVARIABLE splitH Lorg/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder; L149 L151 47\n" +
            "LOCALVARIABLE groupedSplitHolder Lorg/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder; L163 L169 48\n" +
            "LOCALVARIABLE location Ljava/lang/String; L121 L174 38\n" +
            "LOCALVARIABLE holder Lorg/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder; L122 L174 39\n" +
            "LOCALVARIABLE splitHolder Lorg/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder; L123 L174 40\n" +
            "LOCALVARIABLE oldHeadIndex I L126 L174 41\n" +
            "LOCALVARIABLE groupLength J L127 L174 42\n" +
            "LOCALVARIABLE groupNumSplits I L128 L174 44\n" +
            "LOCALVARIABLE groupLocation [Ljava/lang/String; L142 L174 45\n" +
            "LOCALVARIABLE groupedSplit Lorg/apache/hadoop/mapred/split/TezGroupedSplit; L160 L174 46\n" +
            "LOCALVARIABLE entry Ljava/util/Map$Entry; L118 L174 37\n" +
            "LOCALVARIABLE splitHolder Lorg/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder; L186 L187 41\n" +
            "LOCALVARIABLE locHolder Lorg/apache/hadoop/mapred/split/TezMapredSplitsGrouper$LocationHolder; L183 L184 40\n" +
            "LOCALVARIABLE entry Ljava/util/Map$Entry; L182 L184 39\n" +
            "LOCALVARIABLE rack Ljava/lang/String; L199 L203 42\n" +
            "LOCALVARIABLE location Ljava/lang/String; L198 L203 41\n" +
            "LOCALVARIABLE location Ljava/lang/String; L224 L227 51\n" +
            "LOCALVARIABLE rack Ljava/lang/String; L229 L230 49\n" +
            "LOCALVARIABLE splitHolder Lorg/apache/hadoop/mapred/split/TezMapredSplitsGrouper$SplitHolder; L218 L215 46\n" +
            "LOCALVARIABLE locations [Ljava/lang/String; L219 L215 47\n" +
            "LOCALVARIABLE split Lorg/apache/hadoop/mapred/InputSplit; L210 L215 45\n" +
            "LOCALVARIABLE newLengthPerGroup J L236 L234 43\n" +
            "LOCALVARIABLE newNumSplitsInGroup I L237 L234 45\n" +
            "LOCALVARIABLE numRemainingSplits I L178 L175 36\n" +
            "LOCALVARIABLE remainingSplits Ljava/util/Set; L179 L175 37\n" +
            "LOCALVARIABLE locToRackMap Ljava/util/Map; L194 L175 38\n" +
            "LOCALVARIABLE rackLocations Ljava/util/Map; L195 L175 39\n" +
            "LOCALVARIABLE rackSet Ljava/util/HashSet; L206 L175 40\n" +
            "LOCALVARIABLE numRackSplitsToGroup I L207 L175 41\n" +
            "LOCALVARIABLE rackSplitReduction F L233 L175 42\n" +
            "LOCALVARIABLE numFullGroupsCreated I L115 L249 35\n" +
            "LOCALVARIABLE this Lorg/apache/hadoop/mapred/split/TezMapredSplitsGrouper; L0 L258 0\n" +
            "LOCALVARIABLE conf Lorg/apache/hadoop/conf/Configuration; L0 L258 1\n" +
            "LOCALVARIABLE originalSplits [Lorg/apache/hadoop/mapred/InputSplit; L0 L258 2\n" +
            "LOCALVARIABLE desiredNumSplits I L0 L258 3\n" +
            "LOCALVARIABLE wrappedInputFormatName Ljava/lang/String; L0 L258 4\n" +
            "LOCALVARIABLE estimator Lorg/apache/hadoop/mapred/split/SplitSizeEstimator; L0 L258 5\n" +
            "LOCALVARIABLE configNumSplits I L4 L258 6\n" +
            "LOCALVARIABLE emptyLocation Ljava/lang/String; L10 L258 7\n" +
            "LOCALVARIABLE localhost Ljava/lang/String; L11 L258 8\n" +
            "LOCALVARIABLE emptyLocations [Ljava/lang/String; L12 L258 9\n" +
            "LOCALVARIABLE allSplitsHaveLocalhost Z L13 L258 10\n" +
            "LOCALVARIABLE totalLength J L14 L258 11\n" +
            "LOCALVARIABLE distinctLocations Ljava/util/Map; L15 L258 13\n" +
            "LOCALVARIABLE groupedSplitsList Ljava/util/List; L72 L258 14\n" +
            "LOCALVARIABLE lengthPerGroup J L73 L258 23\n" +
            "LOCALVARIABLE numNodeLocations I L74 L258 25\n" +
            "LOCALVARIABLE numSplitsPerLocation I L75 L258 26\n" +
            "LOCALVARIABLE numSplitsInGroup I L76 L258 27\n" +
            "LOCALVARIABLE locSet Ljava/util/Set; L81 L258 28\n" +
            "LOCALVARIABLE groupByLength Z L101 L258 21\n" +
            "LOCALVARIABLE groupByCount Z L102 L258 22\n" +
            "LOCALVARIABLE splitsProcessed I L106 L258 29\n" +
            "LOCALVARIABLE group Ljava/util/List; L107 L258 30\n" +
            "LOCALVARIABLE groupLocationSet Ljava/util/Set; L108 L258 31\n" +
            "LOCALVARIABLE allowSmallGroups Z L109 L258 32\n" +
            "LOCALVARIABLE doingRackLocal Z L110 L258 33\n" +
            "LOCALVARIABLE iterations I L111 L258 34\n" +
            "LOCALVARIABLE groupedSplits [Lorg/apache/hadoop/mapred/InputSplit; L253 L258 35\n" +
            "MAXLOCALS = 53", false
        );
    }
}
