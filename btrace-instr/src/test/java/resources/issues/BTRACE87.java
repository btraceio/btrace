/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package resources.issues;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jaroslav Bachorik <jaroslav.bachorik@oracle.com>
 */
public class BTRACE87 {
  private void containerMethod() {
    List a = new ArrayList();
    a.clear();
    a.add("sample");
  }
}
