package com.thesettler_x_create;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Reads a compiled class off the test classpath and says what is inside it.
 *
 * <p>Several of the mod's couplings to MineColonies and Create are strings - an NBT tag name, a
 * method the courier AI has to call - that no compiler ever looks at. A test can only ask the
 * upstream class itself whether it still carries them, which is what this does, so the nightly run
 * against the newest release answers the question for us.
 */
public final class CompiledClassFacts {
  private CompiledClassFacts() {}

  /** Every string literal in the class, from every method body. */
  public static Set<String> stringConstantsOf(String internalName) throws Exception {
    Set<String> constants = new HashSet<>();
    visit(
        internalName,
        new ClassVisitor(Opcodes.ASM9) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
              @Override
              public void visitLdcInsn(Object value) {
                if (value instanceof String text) {
                  constants.add(text);
                }
              }
            };
          }
        });
    return constants;
  }

  /** The names of every method this class calls. */
  public static Set<String> methodCallsOf(String internalName) throws Exception {
    Set<String> calls = new HashSet<>();
    visit(
        internalName,
        new ClassVisitor(Opcodes.ASM9) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String called, String descriptor, boolean isInterface) {
                calls.add(called);
              }
            };
          }
        });
    return calls;
  }

  /** Hands a class off the test classpath to a visitor of your own. */
  public static void visit(String internalName, ClassVisitor visitor) throws Exception {
    try (InputStream in =
        CompiledClassFacts.class.getResourceAsStream("/" + internalName + ".class")) {
      assertNotNull(in, internalName + " is not on the test classpath");
      new ClassReader(in).accept(visitor, ClassReader.SKIP_FRAMES);
    }
  }
}
