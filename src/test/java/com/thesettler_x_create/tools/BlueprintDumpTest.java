package com.thesettler_x_create.tools;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintDumpTest {
    @Test
    void runReturnsUsageCodeWhenMissingArgs() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = BlueprintDump.run(new String[0], new PrintStream(out), new PrintStream(err));
        assertEquals(2, code);
    }

    @Test
    void runReturnsMissingFileCode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = BlueprintDump.run(new String[] {"Z:\\does_not_exist.blueprint"}, new PrintStream(out), new PrintStream(err));
        assertEquals(3, code);
    }
}
