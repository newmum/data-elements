import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;

/** Parse only. This is NOT a dependency-resolved compilation or a Maven test substitute. */
public class SyntaxCheck {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);List<Path> sources;
        try(var paths=Files.walk(root.resolve("src"))) {sources=paths.filter(p->p.toString().endsWith(".java")).toList();}
        var compiler=ToolProvider.getSystemJavaCompiler();var diagnostics=new DiagnosticCollector<JavaFileObject>();
        try(var manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8)) {
            var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none","--release","21"),null,manager.getJavaFileObjectsFromPaths(sources));
            for(var unit:task.parse()) { /* Force complete parsing of every compilation unit. */ }
            long errors=diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).peek(System.err::println).count();
            if(errors>0) throw new AssertionError("Java syntax errors: "+errors);
            System.out.println("JAVA SYNTAX PARSE PASSED: "+sources.size()+" source files (no type/dependency checking)");
        }
    }
}
