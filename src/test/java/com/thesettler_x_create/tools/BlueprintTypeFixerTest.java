package com.thesettler_x_create.tools;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintTypeFixerTest {
    @Test
    void runReturnsUsageCodeWhenMissingArgs() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = BlueprintTypeFixer.run(new String[0], new PrintStream(out), new PrintStream(err));
        assertEquals(2, code);
    }

    @Test
    void runReturnsInvalidInputCode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = BlueprintTypeFixer.run(new String[] {"Z:\\missing.blueprint", "", "type", "file"}, new PrintStream(out), new PrintStream(err));
        assertEquals(4, code);
    }
}
