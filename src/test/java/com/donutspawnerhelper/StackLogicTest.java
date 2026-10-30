package com.donutspawnerhelper;

import static org.junit.jupiter.api.Assertions.*;

import com.donutspawnerhelper.StackTracker.Outcome;
import org.junit.jupiter.api.Test;

class StackLogicTest {

    @Test
    void parsesScreenshotMessages() {
        assertEquals(2, StackMessageParser.parseStackAdded(
                "You just added another spawner! There are now 2 spawners in the stack.").getAsInt());
        assertEquals(8, StackMessageParser.parseStackAdded(
                "You just added another spawner! There are now 8 spawners in the stack.").getAsInt());
        assertEquals(1234, StackMessageParser.parseStackAdded(
                "[!] You just added another spawner! There are now 1,234 spawners in the stack.").getAsInt());
    }

    @Test
    void ignoresFailuresAndOtherChat() {
        assertTrue(StackMessageParser.parseStackAdded("You can't add that spawner here.").isEmpty());
        assertTrue(StackMessageParser.parseStackAdded("Steve: you just added another spawner? lol").isEmpty());
        assertTrue(StackMessageParser.parseStackAdded(null).isEmpty());
        assertTrue(StackMessageParser.parseStackAdded("There are now 99999999999 spawners in the stack").isEmpty());
    }

    @Test
    void parsesTitle() {
        var t = StackMessageParser.parseTitle("8 Skeleton Spawners").orElseThrow();
        assertEquals(8, t.count());
        assertEquals("Skeleton", t.mob());
        assertEquals("Cave Spider", StackMessageParser.parseTitle("1 Cave Spider Spawner").orElseThrow().mob());
        assertTrue(StackMessageParser.parseTitle("Inventory").isEmpty());
    }

    @Test
    void duplicatesAndRateLimit() {
        StackTracker t = new StackTracker();
        String m2 = "You just added another spawner! There are now 2 spawners in the stack.";
        String m3 = "You just added another spawner! There are now 3 spawners in the stack.";
        assertEquals(Outcome.IGNORED, t.onServerMessage("hello", 0));
        assertEquals(Outcome.ACCEPTED, t.onServerMessage(m2, 1000));
        assertEquals(Outcome.DUPLICATE, t.onServerMessage(m2, 1100));      // same count, too soon
        assertEquals(Outcome.ACCEPTED_SILENT, t.onServerMessage(m3, 1150)); // real, but rate limited
        assertEquals(3, t.lastCount());
        assertEquals(Outcome.ACCEPTED, t.onServerMessage(m3, 2000));        // same count, later: legit again
    }

    @Test
    void bindsCountToSpawnerPosition() {
        StackTracker t = new StackTracker();
        t.bind(42L, 1000);
        t.onServerMessage("You just added another spawner! There are now 5 spawners in the stack.", 1500);
        assertEquals(5, t.confirmedCountAt(42L).getAsInt());
        assertTrue(t.confirmedCountAt(43L).isEmpty());

        StackTracker late = new StackTracker();
        late.bind(42L, 1000);
        late.onServerMessage("You just added another spawner! There are now 5 spawners in the stack.", 9000);
        assertTrue(late.confirmedCountAt(42L).isEmpty()); // too late to attribute
    }
}
