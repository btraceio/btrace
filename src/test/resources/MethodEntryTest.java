/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package resources;

/**
 *
 * @author Jaroslav Bachorik
 */
public class MethodEntryTest {
    public MethodEntryTest() {}
    private MethodEntryTest(String a) {}

    public void noargs() {};
    static public void noargs$static() {};
    public void args(String a, int b, String[] c, int[] d) {};
    static public void args$static(String a, int b, String[] c, int[] d) {};
}
