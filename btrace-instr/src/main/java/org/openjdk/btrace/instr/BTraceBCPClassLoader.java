package org.openjdk.btrace.instr;

import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.SharedSettings;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

final class BTraceBCPClassLoader extends URLClassLoader {
    BTraceBCPClassLoader(SharedSettings settings) {
        super(getBCPUrls(settings), null);
    }

    private static URL[] getBCPUrls(SharedSettings settings) {
        DebugSupport debug = new DebugSupport(settings);
        String bcp = settings.getBootClassPath();
        if (bcp != null && !bcp.isEmpty()) {
            List<URL> urls = new ArrayList<>();
            for (String cpElement : bcp.split(File.pathSeparator)) {
                try {
                    urls.add(new File(cpElement).toURI().toURL());
                } catch (MalformedURLException e) {
                    debug.debug(e);
                }
            }
            return urls.toArray(new URL[0]);
        }
        return new URL[0];
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // delegate class loading to parent directly
        return getParent().loadClass(name);
    }

    @Override
    public URL getResource(String name) {
        // follow the standard process to load resources
        return super.getResource(name);
    }
}
