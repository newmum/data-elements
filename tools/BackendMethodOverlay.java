import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Build a reviewed single-method overlay when a shared source class contains other pending work.
 * Compile with the existing ASM/ASM-tree dependencies into logs/. Inputs are hash-pinned class files;
 * the output is subsequently packaged and baseline-checked by package-backend-overlay.py.
 * This tool does not modify source files, runtime JARs or running processes.
 */
public final class BackendMethodOverlay {
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 7) throw new IllegalArgumentException(
                "base.class donor.class output.class method descriptor baseSHA256 donorSHA256");
        Path basePath = Path.of(args[0]).toAbsolutePath().normalize();
        Path donorPath = Path.of(args[1]).toAbsolutePath().normalize();
        Path output = Path.of(args[2]).toAbsolutePath().normalize();
        if (output.equals(basePath) || output.equals(donorPath) || Files.exists(output)) {
            throw new IllegalArgumentException("Stage a new output; never overwrite either input");
        }
        byte[] baseBytes = Files.readAllBytes(basePath), donorBytes = Files.readAllBytes(donorPath);
        if (!sha(baseBytes).equals(args[5]) || !sha(donorBytes).equals(args[6])) {
            throw new IllegalArgumentException("Reviewed input hash changed");
        }
        ClassReader baseReader = new ClassReader(baseBytes);
        ClassNode base = new ClassNode(), donor = new ClassNode();
        baseReader.accept(base, 0);
        new ClassReader(donorBytes).accept(donor, 0);
        if (!base.name.equals(donor.name)) throw new IllegalArgumentException("Class identities differ");
        String method = args[3], descriptor = args[4];
        if (method.startsWith("<")) throw new IllegalArgumentException("Constructors are not supported");
        MethodNode original = selected(base, method, descriptor), replacement = selected(donor, method, descriptor);
        if (original.access != replacement.access || !java.util.Objects.equals(original.signature, replacement.signature)
                || !original.exceptions.equals(replacement.exceptions)
                || (replacement.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            throw new IllegalArgumentException("Selected method contract changed");
        }
        // A copied method must not depend on a second, unpublished method or field in this class.
        Set<String> methods = new HashSet<>(), fields = new HashSet<>();
        base.methods.forEach(m -> methods.add(m.name + m.desc));
        base.fields.forEach(f -> fields.add(f.name + ":" + f.desc));
        for (var instruction : replacement.instructions) {
            if (instruction instanceof MethodInsnNode m && m.owner.equals(base.name)
                    && !methods.contains(m.name + m.desc)) throw new IllegalArgumentException("Unpublished method dependency");
            if (instruction instanceof FieldInsnNode f && f.owner.equals(base.name)
                    && !fields.contains(f.name + ":" + f.desc)) throw new IllegalArgumentException("Unpublished field dependency");
            if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                checkHandle(dynamic.bsm, base.name, methods, fields);
                for (Object argument : dynamic.bsmArgs) if (argument instanceof Handle handle) {
                    checkHandle(handle, base.name, methods, fields);
                }
            }
        }
        // Nonselected methods are copied directly from the parent's MethodWriter, including their
        // original code and metadata. Only the explicit donor method is re-emitted with remapped constants.
        ClassWriter writer = new ClassWriter(baseReader, 0);
        baseReader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return name.equals(method) && desc.equals(descriptor) ? null
                        : super.visitMethod(access, name, desc, signature, exceptions);
            }
            @Override public void visitEnd() {
                replacement.accept(writer);
                super.visitEnd();
            }
        }, 0);
        byte[] result = writer.toByteArray();
        Files.createDirectories(output.getParent());
        Files.write(output, result, java.nio.file.StandardOpenOption.CREATE_NEW);
        System.out.println("class=" + base.name + " method=" + method + descriptor + " sha256=" + sha(result));
    }

    private static MethodNode selected(ClassNode node, String name, String descriptor) {
        var matches = node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("Expected exactly one selected method");
        return matches.getFirst();
    }

    private static void checkHandle(Handle handle, String owner, Set<String> methods, Set<String> fields) {
        if (!handle.getOwner().equals(owner)) return;
        boolean field = handle.getTag() >= Opcodes.H_GETFIELD && handle.getTag() <= Opcodes.H_PUTSTATIC;
        if (!(field ? fields.contains(handle.getName() + ":" + handle.getDesc())
                    : methods.contains(handle.getName() + handle.getDesc()))) {
            throw new IllegalArgumentException("Unpublished dynamic dependency");
        }
    }
}
