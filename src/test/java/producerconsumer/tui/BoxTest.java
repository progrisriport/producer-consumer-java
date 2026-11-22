package producerconsumer.tui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class BoxTest {

    @Test
    void headerShowsNameInBorderAndTagline() {
        List<String> lines = Box.header();

        assertEquals(3, lines.size());
        Ansi.assertFullWidth(lines);
        assertTrue(Ansi.strip(lines.get(0)).startsWith("┌─ " + Theme.APP_NAME + " ─"));
        assertTrue(Ansi.strip(lines.get(1)).contains(Theme.TAGLINE));
        assertTrue(lines.get(0).contains(Theme.accentBold(Theme.APP_NAME)));
    }

    @Test
    void boxHasTitleInTopBorderAndPaddedBody() {
        List<String> lines = Box.box("Title", List.of(Theme.text("one"), "two"));

        assertEquals(4, lines.size());
        Ansi.assertFullWidth(lines);
        assertTrue(Ansi.strip(lines.get(0)).startsWith("┌─ Title ─"));
        assertTrue(Ansi.strip(lines.get(0)).endsWith("┐"));
        assertEquals("│ one", Ansi.strip(lines.get(1)).substring(0, 5));
        assertTrue(Ansi.strip(lines.get(1)).endsWith(" │"));
        assertTrue(Ansi.strip(lines.get(3)).startsWith("└"));
    }

    @Test
    void boxAcceptsLineOfExactlyInnerWidth() {
        List<String> lines = Box.box("T", List.of(Box.repeat("x", Box.INNER)));

        Ansi.assertFullWidth(lines);
    }

    @Test
    void boxRejectsLineTooLong() {
        try {
            Box.box("T", List.of(Box.repeat("x", Box.INNER + 1)));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void boxTruncatesLongTitle() {
        List<String> lines = Box.box(Box.repeat("t", 100), List.of());

        Ansi.assertFullWidth(lines);
        assertTrue(Ansi.strip(lines.get(0)).contains("..."));
    }

    @Test
    void footerHasSeparatorAndShortcuts() {
        List<String> lines = Box.footer("q quit");

        assertEquals(2, lines.size());
        assertEquals(Box.repeat("─", Theme.WIDTH), Ansi.strip(lines.get(0)));
        assertEquals("q quit", Ansi.strip(lines.get(1)));
    }

    @Test
    void visibleLengthIgnoresAnsiSequences() {
        assertEquals(5, Box.visibleLength(Theme.error("hello")));
        assertEquals(10, Box.visibleLength(Theme.text("hello") + Theme.accentBold("world")));
        assertEquals(0, Box.visibleLength(""));
    }

    @Test
    void truncateAddsEllipsisOnlyWhenNeeded() {
        assertEquals("short", Box.truncate("short", 10));
        assertEquals("abcdefg...", Box.truncate("abcdefghijklmnop", 10));
    }

    @Test
    void wrapBreaksBetweenWords() {
        List<String> lines = Box.wrap("one two three four", 9);

        assertEquals(List.of("one two", "three", "four"), lines);
    }

    @Test
    void wrapTruncatesWordLongerThanWidth() {
        List<String> lines = Box.wrap("abcdefghijkl", 8);

        assertEquals(List.of("abcde..."), lines);
    }

    @Test
    void secondsHasOneDecimal() {
        assertEquals("4.1 s", Box.seconds(4_072));
        assertEquals("0.0 s", Box.seconds(0));
    }
}
