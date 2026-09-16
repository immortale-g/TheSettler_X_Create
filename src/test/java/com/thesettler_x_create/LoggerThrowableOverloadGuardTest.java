package com.thesettler_x_create;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * MineColonies declares {@code IRequest#getId()} as {@code <T extends IToken<?>> T getId()}. Passed
 * as the only argument after the message, Java infers T as a Throwable and binds the call to {@code
 * Logger.info(String, Throwable)}, which throws a ClassCastException at runtime as soon as debug
 * logging is on. The compiled form is a checkcast to Throwable right before the logger call; no
 * real exception argument needs that cast.
 */
class LoggerThrowableOverloadGuardTest {
  private static final Path CLASSES = Path.of("build/classes/java/main");

  @Test
  void noLoggerCallCastsItsArgumentToThrowable() throws Exception {
    assertTrue(Files.isDirectory(CLASSES), "compiled classes missing at " + CLASSES);
    List<String> hits = new ArrayList<>();
    try (Stream<Path> files = Files.walk(CLASSES)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
        try (InputStream in = Files.newInputStream(file)) {
          new ClassReader(in).accept(new Scanner(hits), ClassReader.SKIP_FRAMES);
        }
      }
    }
    assertTrue(hits.isEmpty(), "logger calls bound to the Throwable overload: " + hits);
  }

  private static final class Scanner extends ClassVisitor {
    private final List<String> hits;
    private String className;

    Scanner(List<String> hits) {
      super(Opcodes.ASM9);
      this.hits = hits;
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      className = name;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      return new MethodVisitor(Opcodes.ASM9) {
        private boolean castToThrowable;
        private int line;

        @Override
        public void visitLineNumber(int lineNumber, Label start) {
          line = lineNumber;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
          castToThrowable = opcode == Opcodes.CHECKCAST && type.equals("java/lang/Throwable");
        }

        @Override
        public void visitMethodInsn(
            int opcode, String owner, String method, String desc, boolean isInterface) {
          if (castToThrowable
              && (owner.equals("org/slf4j/Logger")
                  || owner.equals("org/apache/logging/log4j/Logger"))
              && desc.endsWith("Ljava/lang/Throwable;)V")) {
            hits.add(className + ":" + line);
          }
          castToThrowable = false;
        }

        @Override
        public void visitInsn(int opcode) {
          castToThrowable = false;
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
          castToThrowable = false;
        }
      };
    }
  }
}
