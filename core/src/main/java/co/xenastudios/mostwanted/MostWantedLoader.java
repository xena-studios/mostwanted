package co.xenastudios.mostwanted;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Has Paper download the database libraries (jOOQ, Flyway, HikariCP, the PostgreSQL
 * driver) on first start, instead of shading them into the jar. The list comes from
 * the build (paper-libraries.txt), so it matches what common was compiled against.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MostWantedLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpath) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        for (String coordinates : libraries()) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinates), null));
        }
        classpath.addLibrary(resolver);
    }

    private static Iterable<String> libraries() {
        try (InputStream in = MostWantedLoader.class.getResourceAsStream("/paper-libraries.txt")) {
            if (in == null) {
                throw new IllegalStateException("paper-libraries.txt is missing from the jar");
            }
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
