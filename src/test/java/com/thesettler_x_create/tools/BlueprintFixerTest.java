package com.thesettler_x_create.tools;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintFixerTest {
    @Test
    void runReturnsUsageCodeWhenMissingArgs() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = BlueprintFixer.run(new String[0], new PrintStream(out), new PrintStream(err));
        assertEquals(2, code);
    }

    @Test
    void runReturnsInvalidLevelCode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = BlueprintFixer.run(new String[] {"Z:\\missing.blueprint", "createshop", "NaN"}, new PrintStream(out), new PrintStream(err));
        assertEquals(3, code);
    }
}
