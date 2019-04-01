package de.kosit.validationtool.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;

import org.apache.commons.lang3.StringUtils;

/**
 * Helferlein rund um Pfade, hauptsächlich um {@link ContentRepository Repository-Quellen} in JAR-Dateien zu
 * unterstützen. Hintergrund: Um innerhalb der Anwendung möglichst homogen auf Artefakte zugreifen zu können, wird ein
 * JAR mit den Artefakten mittels {@link FileSystem} geladen und intern Pfade verteilt.
 *
 * Das diese {@link FileSystem}-Pfade auch mit den XML-Loading-Mechanismen zusammenarbeiten müssen, gibt es hier einige
 * Hilfsfunktionen.
 * 
 * @author Andreas Penski
 */
public class FilesystemHelper {

    /**
     * Erzeugt aus einer URL einen {@link Path}. Berücksichtigt dabei, dass die URL sich ggf. auf eine Resource in einem JAR
     * zeigt-
     * 
     * @param url die URL
     * @return der {@link Path}
     */
    public static Path createPath(final URL url) {
        try {
            return createPath(url.toURI());
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException(String.format("Not a valid URL %s", url), e);
        }
    }

    /**
     * Erzeugt aus einer {@link URI} einen {@link Path}. Berücksichtigt dabei, dass die {@link URI} sich ggf. auf eine
     * Resource in einem JAR zeigt-
     * 
     * @param uri die {@link URI}
     * @return der {@link Path}
     */
    public static Path createPath(final URI uri) {
        if ("jar".equals(uri.getScheme())) {
            final String[] array = uri.toString().split("!");
            final FileSystem fs = openFilesystem(array[0]);
            if (fs == null) {
                throw new IllegalArgumentException(String.format("Jar not found: %s", array[0]));
            }
            return fs.getPath(array[1]);
        } else {
            return Paths.get(uri);
        }

    }

    /**
     * Öffnet ein JAR um darin {@link Path Pfade} zu addressieren.
     * 
     * @param jarPath Pfad zum JAR (URL)
     * @return das geöffnete {@link FileSystem}
     */
    private static FileSystem openFilesystem(final String jarPath) {
        FileSystem fs = null;
        for (final FileSystemProvider provider : FileSystemProvider.installedProviders()) {
            if (provider.getScheme().equalsIgnoreCase("jar")) {
                try {

                    fs = provider.getFileSystem(URI.create(jarPath));
                } catch (final FileSystemNotFoundException e) {
                    // in this case we need to initialize it first:
                    try {
                        fs = provider.newFileSystem(URI.create(jarPath), Collections.emptyMap());
                    } catch (final IOException e1) {
                        throw new IllegalArgumentException(String.format("Can not access path %s", jarPath), e);
                    }
                }
            }
        }
        return fs;
    }

    /**
     * Zeigt an, ob dieser Pfad in ein JAR zeigt.
     * 
     * @param path der Pfad
     * @return true wenn innerhalb eines JARs
     */
    public static boolean isJarResource(final Path path) {
        return isJarResource(path.toUri());
    }

    /**
     * Zeigt an, ob diese URI in ein JAR zeigt.
     * 
     * @param uri der URI
     * @return true wenn innerhalb eines JARs
     */
    public static boolean isJarResource(final URI uri) {
        return isJarResource(uri.toString());
    }

    /**
     * Zeigt an, ob dieser Pfad in ein JAR zeigt.
     * 
     * @param path der Pfad (URI-Format)
     * @return true wenn innerhalb eines JARs
     */
    public static boolean isJarResource(final String path) {
        return StringUtils.startsWithIgnoreCase(path, "jar:") && path.split("!").length == 2;
    }

    /**
     * Ermittelt den Pfad zum JAR dieser URI
     * 
     * @param uri der Pfad
     * @return Pfad zum JAR
     */
    public static String getJar(final URI uri) {
        if (!isJarResource(uri)) {
            throw new IllegalArgumentException("Is not a valid jar uri " + uri);
        }
        return uri.toString().split("!")[0];

    }

    /**
     * Ermittelt den Pfad im JAR dieser URI, also der lokale Pfad ohne das JAR
     * 
     * @param uri der Pfad
     * @return Pfad innerhalb des JARs
     */
    public static String getResoucePath(final String uri) {
        if (!isJarResource(uri)) {
            throw new IllegalArgumentException("Is not a valid jar uri " + uri);
        }
        return uri.split("!")[1];
    }
}
