package me.hash.mediaroulette.content.provider.impl.images;

import me.hash.mediaroulette.model.content.MediaSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BooruBoardTest {

    @Test
    void enumValues_ExistWithCorrectProperties() {
        // Test RULE34
        BooruBoard rule34 = BooruBoard.RULE34;
        assertEquals("rule34", rule34.getId());
        assertEquals("Rule34", rule34.getDisplayName());
        assertEquals("https://rule34.xxx/index.php?page=post&s=random", rule34.getRandomUrl());
        assertEquals(MediaSource.RULE34, rule34.getMediaSource());

        // Test GELBOORU
        BooruBoard gelbooru = BooruBoard.GELBOORU;
        assertEquals("gelbooru", gelbooru.getId());
        assertEquals("Gelbooru", gelbooru.getDisplayName());
        assertTrue(gelbooru.getRandomUrl().contains("gelbooru.com"));
        assertNotNull(gelbooru.getMediaSource());
    }

    @Test
    void values_ContainsAllBoards() {
        BooruBoard[] boards = BooruBoard.values();
        assertEquals(2, boards.length);
        assertEquals(BooruBoard.RULE34, boards[0]);
        assertEquals(BooruBoard.GELBOORU, boards[1]);
    }

    @RepeatedTest(10)
    void random_ReturnsValidBoard() {
        BooruBoard board = BooruBoard.random();
        assertNotNull(board);
        assertTrue(board == BooruBoard.RULE34 || board == BooruBoard.GELBOORU);
    }

    @Test
    void random_HasReasonableDistribution() {
        // Run many times to verify both boards are returned
        Set<BooruBoard> seenBoards = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seenBoards.add(BooruBoard.random());
            if (seenBoards.size() == 2) break;
        }
        assertEquals(2, seenBoards.size(), "Both boards should be returned randomly");
    }

    @Test
    void byId_FindsById_CaseInsensitive() {
        // Exact match
        Optional<BooruBoard> result = BooruBoard.byId("rule34");
        assertTrue(result.isPresent());
        assertEquals(BooruBoard.RULE34, result.get());

        // Upper case
        result = BooruBoard.byId("RULE34");
        assertTrue(result.isPresent());
        assertEquals(BooruBoard.RULE34, result.get());

        // Mixed case
        result = BooruBoard.byId("GeLbOoRu");
        assertTrue(result.isPresent());
        assertEquals(BooruBoard.GELBOORU, result.get());
    }

    @Test
    void byId_FindsByDisplayName() {
        Optional<BooruBoard> result = BooruBoard.byId("Rule34");
        assertTrue(result.isPresent());
        assertEquals(BooruBoard.RULE34, result.get());

        result = BooruBoard.byId("Gelbooru");
        assertTrue(result.isPresent());
        assertEquals(BooruBoard.GELBOORU, result.get());
    }

    @Test
    void byId_ReturnsEmpty_ForUnknown() {
        Optional<BooruBoard> result = BooruBoard.byId("unknown");
        assertFalse(result.isPresent());

        result = BooruBoard.byId("danbooru");
        assertFalse(result.isPresent());
    }

    @Test
    void byId_ReturnsEmpty_ForNullOrBlank() {
        Optional<BooruBoard> result = BooruBoard.byId(null);
        assertFalse(result.isPresent());

        result = BooruBoard.byId("");
        assertFalse(result.isPresent());

        result = BooruBoard.byId("   ");
        assertFalse(result.isPresent());
    }

    @Test
    void getAll_ReturnsAllBoards() {
        List<BooruBoard> all = BooruBoard.getAll();
        assertEquals(2, all.size());
        assertTrue(all.contains(BooruBoard.RULE34));
        assertTrue(all.contains(BooruBoard.GELBOORU));
    }
}
