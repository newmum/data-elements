import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.ArrayList;

/** Class/constructor/URL verification only: never opens a database connection. */
public class DriverProbe {
    public static void main(String[] args) throws Exception {
        int failed = 0;
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String[] parts = line.split("\\|", -1);
            var urls = new ArrayList<java.net.URL>();
            for (String file : parts[2].split(",")) urls.add(Path.of(file).toUri().toURL());
            var previous = Thread.currentThread().getContextClassLoader();
            // Parent contains JDK plus SLF4J API, as provided by the NiFi framework.
            try (var loader = new URLClassLoader(urls.toArray(java.net.URL[]::new),
                    DriverProbe.class.getClassLoader())) {
                Thread.currentThread().setContextClassLoader(loader);
                Driver driver = (Driver) Class.forName(parts[1], true, loader)
                        .getDeclaredConstructor().newInstance();
                if (!driver.acceptsURL(parts[3])) throw new IllegalStateException("URL not accepted");
                if (parts[0].endsWith("_COMPAT")) {
                    driver.getPropertyInfo(parts[3], new java.util.Properties());
                }
                System.out.println("PASS " + parts[0]);
            } catch (Throwable error) {
                failed++;
                System.out.println("FAIL " + parts[0] + " " + error);
                error.printStackTrace(System.out);
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
        }
        if (failed != 0) throw new IllegalStateException(failed + " driver profiles failed");
    }
}
