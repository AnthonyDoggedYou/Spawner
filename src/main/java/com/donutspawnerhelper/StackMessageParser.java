package com.donutspawnerhelper;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure text parsing (no Minecraft classes) so it can be unit-tested.
 *
 * Reference strings (from the screenshots):
 *  - chat:  "You just added another spawner! There are now 2 spawners in the stack."
 *  - title: "8 Skeleton Spawners"
 */
public final class StackMessageParser {

    private static final Pattern ADDED = Pattern.compile(
            "you\\s+just\\s+added\\s+another\\s+spawner!?\\s*there\\s+(?:is|are)\\s+now\\s+"
                    + "([0-9][0-9,]*)\\s+spawners?\\s+in\\s+the\\s+stack",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TITLE = Pattern.compile(
            "^\\s*([0-9][0-9,]*)\\s+(.+?)\\s+spawners?\\s*$", Pattern.CASE_INSENSITIVE);

    public record TitleInfo(int count, String mob) {}

    private StackMessageParser() {}

    /** @return the new stack size if {@code text} is the server's "added another spawner" confirmation. */
    public static OptionalInt parseStackAdded(String text) {
        if (text == null) return OptionalInt.empty();
        Matcher m = ADDED.matcher(text);
        if (!m.find()) return OptionalInt.empty();
        return parseCount(m.group(1));
    }

    /** Parses a container title such as "8 Skeleton Spawners". */
    public static Optional<TitleInfo> parseTitle(String title) {
        if (title == null) return Optional.empty();
        Matcher m = TITLE.matcher(title);
        if (!m.matches()) return Optional.empty();
        OptionalInt count = parseCount(m.group(1));
        if (count.isEmpty()) return Optional.empty();
        return Optional.of(new TitleInfo(count.getAsInt(), m.group(2).trim()));
    }

    private static OptionalInt parseCount(String raw) {
        try {
            return OptionalInt.of(Integer.parseInt(raw.replace(",", "")));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
