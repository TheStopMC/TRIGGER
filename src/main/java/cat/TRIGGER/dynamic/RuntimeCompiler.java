package cat.TRIGGER.dynamic;

import cat.TRIGGER.TriggeredCallback;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class RuntimeCompiler {

    public static Consumer<TriggeredCallback> compileConsumer(String className, String imports, String functionBody) throws Exception {
        String fullClassName = "cat.TRIGGER.dynamic.generated." + className;

        // Full source code string
        String sourceCode = """
                package cat.TRIGGER.dynamic.generated;

                import cat.TRIGGER.dynamic.DynamicFunction;
                %s

                public class %s implements DynamicFunction {
                    @Override
                    public void accept(Object o) {
                        %s
                    }
                }
                """.formatted(imports, className, functionBody);

        // Compile in-memory
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileObject sourceFile = new JavaSourceFromString(fullClassName, sourceCode);

        JavaFileManager fileManager = new ClassFileManager(compiler.getStandardFileManager(null, null, null));
        Boolean result = compiler.getTask(null, fileManager, null, null, null, List.of(sourceFile)).call();

        if (!result) throw new RuntimeException("Compilation failed!");

        // Load class
        ClassLoader classLoader = fileManager.getClassLoader(null);
        Class<?> clazz = classLoader.loadClass(fullClassName);
        return (Consumer<TriggeredCallback>) clazz.getDeclaredConstructor().newInstance();
    }

    // In-memory source file
    static class JavaSourceFromString extends SimpleJavaFileObject {
        final String code;

        JavaSourceFromString(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    // In-memory compiled class loader
    static class ClassFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        private final Map<String, ByteArrayJavaClass> classFiles = new HashMap<>();

        protected ClassFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                   JavaFileObject.Kind kind, FileObject sibling) {
            ByteArrayJavaClass file = new ByteArrayJavaClass(className);
            classFiles.put(className, file);
            return file;
        }

        public ClassLoader getClassLoader(Location location) {
            return new ClassLoader() {
                protected Class<?> findClass(String name) {
                    ByteArrayJavaClass file = classFiles.get(name);
                    if (file == null) throw new RuntimeException("Class not found: " + name);
                    byte[] bytes = file.getBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                }
            };
        }
    }

    // Byte array class object
    static class ByteArrayJavaClass extends SimpleJavaFileObject {
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        ByteArrayJavaClass(String name) {
            super(URI.create("byte:///" + name.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        public OutputStream openOutputStream() {
            return bos;
        }

        public byte[] getBytes() {
            return bos.toByteArray();
        }
    }
}
