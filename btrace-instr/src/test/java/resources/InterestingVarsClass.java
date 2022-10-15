package resources;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

public class InterestingVarsClass {
  public static class Token<T> {
    public boolean getKind() {
      return true;
    }
  }

  public static void initAndStartApp(String a, String b, String c) {
    Collection<Token<String>> tokens = tokens();
    for (Token<?> token : tokens) {
      System.out.println(token);
    }

    StringBuilder nextVar = new StringBuilder(a);
    nextVar.append(b);

    Iterator<Token<String>> iter = tokens.iterator();
    while (iter.hasNext()) {
      Token<?> token = iter.next();
      if (token.getKind()) {
        iter.remove();
      }
    }
  }

  private static Collection<Token<String>> tokens() {
    return Arrays.asList(new Token<String>());
  }
}
