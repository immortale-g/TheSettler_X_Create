package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minecolonies.api.colony.requestsystem.data.IRequestSystemDeliveryManJobDataStore;
import com.minecolonies.api.util.constant.NbtTagConstants;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Pins the two things in MineColonies that {@link CourierOngoingDeliveries} rests on, and that a
 * MineColonies release could take away without breaking a single compile:
 *
 * <ol>
 *   <li>the courier's job writes the token of its request-system data store into its own NBT, under
 *       the API constant, which is how we reach the store without touching a private field
 *   <li>the courier AI records the delivery it is fetching in that store before it reaches into the
 *       source inventory, which is the only thing that tells us which delivery an extraction
 *       belongs to
 * </ol>
 *
 * <p>Both are read out of the compiled MineColonies classes, so this runs in the normal test task
 * and therefore in the nightly compat run against each new release. Without it, a change upstream
 * would not fail a build; pickups would simply stop being attributed and reservations would be
 * consumed on arrival again, quietly.
 */
class CourierOngoingDeliveriesCompatTest {
  private static final String JOB = "com/minecolonies/core/colony/jobs/JobDeliveryman";
  private static final String COURIER_AI =
      "com/minecolonies/core/entity/ai/workers/service/EntityAIWorkDeliveryman";

  @Test
  void theCourierJobStillKnowsItsDataStoreTagByThatName() throws Exception {
    Set<String> constants = stringConstantsOf(JOB);

    assertTrue(
        constants.contains(NbtTagConstants.TAG_RS_DMANJOB_DATASTORE),
        JOB
            + " no longer mentions the NBT tag "
            + NbtTagConstants.TAG_RS_DMANJOB_DATASTORE
            + ". CourierOngoingDeliveries reads the data store token from the job's NBT under that"
            + " tag; without it, pickups are booked on arrival instead of when the courier takes"
            + " them.");
  }

  @Test
  void theCourierStillRecordsWhatItIsFetching() throws Exception {
    Set<String> calls = methodCallsOf(COURIER_AI);

    assertTrue(
        calls.contains("addConcurrentDelivery"),
        COURIER_AI
            + " no longer records the delivery it is fetching on the job. That set is what tells"
            + " CourierOngoingDeliveries which delivery an extraction from the shop belongs to.");
  }

  @Test
  void theDataStoreStillExposesTheOngoingDeliveries() throws Exception {
    assertNotNull(
        IRequestSystemDeliveryManJobDataStore.class.getMethod("getOngoingDeliveries"),
        "the courier job data store no longer exposes getOngoingDeliveries()");
  }

  private static Set<String> stringConstantsOf(String internalName) throws Exception {
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

  private static Set<String> methodCallsOf(String internalName) throws Exception {
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

  private static void visit(String internalName, ClassVisitor visitor) throws Exception {
    try (InputStream in =
        CourierOngoingDeliveriesCompatTest.class.getResourceAsStream(
            "/" + internalName + ".class")) {
      assertNotNull(in, internalName + " is not on the test classpath");
      new ClassReader(in).accept(visitor, ClassReader.SKIP_FRAMES);
    }
  }
}
