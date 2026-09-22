package com.thesettler_x_create.create.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thesettler_x_create.CompiledClassFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Asks Structurize who it lets go in front of our placement handlers.
 *
 * <p>{@link CreatePlacementHandlers} registers through {@code PlacementHandlers.add(handler)},
 * which does not append: it inserts the handler at a fixed position in a list that is scanned front
 * to back, first match wins. Structurize moves that position when it puts new handlers of its own
 * at the front - 1.0.807 inserted at index 1, 1.0.832 inserts at index 3 - and the compiler sees
 * nothing of it, because the signature never changes.
 *
 * <p>That is harmless as long as the handlers ahead of us only claim a single block each, which is
 * true of the three below. It stops being harmless the day a handler that matches broadly, over a
 * block tag or an instance check, is placed in front of us: it would swallow the Create blocks
 * before our handlers are ever asked, and the only symptom would be a builder placing a belt wrong
 * without a line in the log. So the names are pinned here rather than the index.
 */
class PlacementHandlerPrecedenceCompatTest {
  private static final String PLACEMENT_HANDLERS =
      "com/ldtteam/structurize/placement/handlers/placement/PlacementHandlers";

  /**
   * Handlers that may be asked before ours. Each of these compares the block state against exactly
   * one block, so none of them can ever match a Create block. Anything else in front of us has to
   * be read before it is added here.
   */
  private static final Set<String> ALLOWED_AHEAD_OF_US =
      Set.of(
          PLACEMENT_HANDLERS + "$AirPlacementHandler",
          PLACEMENT_HANDLERS + "$SolidSubstitutionPlacementHandler",
          PLACEMENT_HANDLERS + "$SubstitutionPlacementHandler");

  @Test
  void onlyNarrowHandlersAreAskedBeforeOurs() throws Exception {
    Registration registration = readRegistration();

    assertTrue(
        registration.insertionIndex >= 0,
        "PlacementHandlers.add(handler) no longer inserts at a fixed index. If it appends instead,"
            + " our handlers end up behind every Structurize handler and will never be asked for a"
            + " Create block.");
    assertTrue(
        registration.insertionIndex <= registration.handlersInOrder.size(),
        "PlacementHandlers.add(handler) inserts at index "
            + registration.insertionIndex
            + ", past the "
            + registration.handlersInOrder.size()
            + " handlers Structurize registers itself; this test can no longer tell who goes"
            + " first.");

    List<String> ahead = registration.handlersInOrder.subList(0, registration.insertionIndex);
    assertFalse(
        registration.handlersInOrder.isEmpty(),
        "no handler was found in the static initializer of PlacementHandlers; Structurize now"
            + " builds its handler list somewhere else and this test reads the wrong place");
    for (String handler : ahead) {
      assertTrue(
          ALLOWED_AHEAD_OF_US.contains(handler),
          handler
              + " is now asked before our Create handlers. Check what its canHandle matches: if it"
              + " claims more than one fixed block, it can take the Create blocks away from"
              + " CreatePlacementHandlers, and the composite casings and belts are placed wrong"
              + " with nothing in the log. Once it is read and found harmless, add it to"
              + " ALLOWED_AHEAD_OF_US.");
    }
  }

  /**
   * What the compiled PlacementHandlers class says about its own list and where addons land in it.
   */
  private record Registration(List<String> handlersInOrder, int insertionIndex) {}

  private static Registration readRegistration() throws Exception {
    List<String> handlersInOrder = new ArrayList<>();
    int[] insertionIndex = {-1};

    CompiledClassFacts.visit(
        PLACEMENT_HANDLERS,
        new ClassVisitor(Opcodes.ASM9) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            boolean isStaticInit = "<clinit>".equals(name);
            boolean isSingleArgumentAdd =
                "add".equals(name)
                    && descriptor.equals(
                        "(Lcom/ldtteam/structurize/placement/handlers/placement/IPlacementHandler;)V");
            if (!isStaticInit && !isSingleArgumentAdd) {
              return null;
            }
            return new MethodVisitor(Opcodes.ASM9) {
              private String lastConstructed;
              private int lastPushedInt = -1;

              @Override
              public void visitTypeInsn(int opcode, String type) {
                if (opcode == Opcodes.NEW) {
                  lastConstructed = type;
                }
              }

              @Override
              public void visitInsn(int opcode) {
                if (opcode >= Opcodes.ICONST_0 && opcode <= Opcodes.ICONST_5) {
                  lastPushedInt = opcode - Opcodes.ICONST_0;
                }
              }

              @Override
              public void visitIntInsn(int opcode, int operand) {
                if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                  lastPushedInt = operand;
                }
              }

              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String called, String descriptor, boolean isInterface) {
                if (!"java/util/List".equals(owner) || !"add".equals(called)) {
                  return;
                }
                if (isStaticInit
                    && "(Ljava/lang/Object;)Z".equals(descriptor)
                    && lastConstructed != null) {
                  handlersInOrder.add(lastConstructed);
                  lastConstructed = null;
                } else if (isSingleArgumentAdd && "(ILjava/lang/Object;)V".equals(descriptor)) {
                  insertionIndex[0] = lastPushedInt;
                }
              }
            };
          }
        });

    return new Registration(handlersInOrder, insertionIndex[0]);
  }
}
