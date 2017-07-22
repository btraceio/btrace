/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.btrace.shared;

import com.sun.btrace.runtime.Level;
import com.sun.btrace.runtime.OnMethod;
import com.sun.btrace.shared.InstrumentationRecipe.CutPoint;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jbachorik
 */
public class InstrumentationRecipeTest {
    private InstrumentationRecipe instance;

    public InstrumentationRecipeTest() {
    }

    @Before
    public void setUp() {
        CutPoint[] cps = new CutPoint[2];
        OnMethod om1 = new OnMethod();
        om1.setClazz("Class1");
        om1.setMethod("/.*method/");
        om1.setTargetName("handler1");
        om1.setTargetDescriptor("(Ljava/lang/String;)V");
        om1.setLevel(Level.fromString(">=0"));

        OnMethod om2 = new OnMethod();
        om2.setClazz("/.*aclass/");
        om2.setMethod("/.*method/");
        om2.setType("void (java.lang.String)");
        om2.setTargetName("handler2");
        om2.setTargetDescriptor("(II)V");
        om2.setLevel(Level.fromString(">=0"));

        cps[0] = new CutPoint(om1);
        cps[1] = new CutPoint(om2);

        instance = new InstrumentationRecipe(cps, new byte[4096]);
    }

    @Test
    public void testFrom_InputStream() throws Exception {
        System.out.println("from InputStream");
//        InputStream is = null;
//        InstrumentationRecipe expResult = null;
//        InstrumentationRecipe result = InstrumentationRecipe.from(is);
//        assertEquals(expResult, result);
//        fail("The test case is a prototype.");
    }

    @Test
    public void testFrom_byteArr() throws Exception {
        System.out.println("from byte array");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        instance.to(os);
        byte[] data = os.toByteArray();

        long t1 = System.nanoTime();
        InstrumentationRecipe ni = null;
        for (int i = 0; i < 10000; i++) {
            ni = InstrumentationRecipe.from(data);
            assertNotNull(ni);
        }
        System.out.println("*** read-in: " + ((System.nanoTime() - t1) / 10000) + "ns");
    }

    @Test
    public void testTo() throws Exception {
        System.out.println("to");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        instance.to(os);
        byte[] data = os.toByteArray();
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

}
