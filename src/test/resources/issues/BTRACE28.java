/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package resources.issues;

/**
 *
 * @author Jaroslav Bachorik <jaroslav.bachorik@sun.com>
 */
public class BTRACE28 {
    private void serveResource(String param1, String param2) {
        String resourceType = "resourceType";
        String contentType = "contentType";
        int indice, tempIndice;
        byte tempArr[];
        byte mainArr[] = new byte[0];
        byte byteArr[] = new byte[65535];

        StringBuilder sb = new StringBuilder();

        try {
            sb.append("hooo");
            System.err.println("i am here");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
