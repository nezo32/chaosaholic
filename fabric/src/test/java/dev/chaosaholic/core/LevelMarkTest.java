package dev.chaosaholic.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Anti-farming: only levels above the highest level reached since the last death start events. */
class LevelMarkTest {
	/** Replays level changes of one player from {@code start} with a fresh mark; returns the total triggers. */
	private static int replay(int start, int... levels) {
		int mark = LevelMark.initial(start);
		int last = start;
		int total = 0;
		for (int level : levels) {
			LevelMark.Result r = LevelMark.observe(last, level, mark);
			total += r.triggers();
			mark = r.mark();
			last = level;
		}
		return total;
	}

	@Test
	void oneLevelOneTrigger() {
		assertEquals(new LevelMark.Result(1, 1), LevelMark.observe(0, 1, 0));
		assertEquals(new LevelMark.Result(1, 6), LevelMark.observe(5, 6, 5));
	}

	@Test
	void severalLevelsAtOnce() {
		assertEquals(new LevelMark.Result(3, 3), LevelMark.observe(0, 3, 0));
		assertEquals(new LevelMark.Result(30, 30), LevelMark.observe(0, 30, 0));
	}

	@Test
	void levelLossNeverTriggersAndKeepsMark() {
		assertEquals(new LevelMark.Result(0, 30), LevelMark.observe(30, 27, 30));
		assertEquals(new LevelMark.Result(0, 30), LevelMark.observe(30, 0, 30));
	}

	@Test
	void enchantingTableRefundScenario() {
		// level 30, enchant for 3 levels, earn them back: nothing; the 31st level counts once
		assertEquals(0, replay(30, 27, 28, 29, 30));
		assertEquals(1, replay(30, 27, 28, 29, 30, 31));
		assertEquals(0, LevelMark.observe(27, 30, 30).triggers());
		assertEquals(new LevelMark.Result(1, 31), LevelMark.observe(29, 31, 30));
	}

	@Test
	void anvilRepeatedSpendAndRegain() {
		// 20 -> spend 5 -> regain -> spend 5 -> regain, many times: never a trigger
		int mark = 20;
		int level = 20;
		for (int i = 0; i < 50; i++) {
			LevelMark.Result spend = LevelMark.observe(level, level - 5, mark);
			assertEquals(0, spend.triggers());
			LevelMark.Result regain = LevelMark.observe(level - 5, level, spend.mark());
			assertEquals(0, regain.triggers());
			mark = regain.mark();
		}
		assertEquals(20, mark);
	}

	@Test
	void jumpPastTheMarkCountsOnlyTheNewPart() {
		// mark 30, at 25 after spending, one big orb takes the player to 33: 3 triggers (31, 32, 33)
		assertEquals(new LevelMark.Result(3, 33), LevelMark.observe(25, 33, 30));
	}

	@Test
	void deathResetsTheMark() {
		// the respawned player has no mark: initialized to the respawn level (0 without keepInventory)
		int mark = LevelMark.initial(0);
		assertEquals(0, mark);
		assertEquals(new LevelMark.Result(1, 1), LevelMark.observe(0, 1, mark));
		// keepInventory: level 30 kept, mark re-initialized to 30, so no flood after respawn
		assertEquals(30, LevelMark.initial(30));
		assertEquals(0, LevelMark.observe(30, 30, 30).triggers());
	}

	@Test
	void firstObservationDoesNotFlood() {
		// installing the mod on a world where the player already has level 50
		assertEquals(50, LevelMark.initial(50));
		assertEquals(0, replay(50, 49, 50));
		assertEquals(1, replay(50, 51));
		assertEquals(0, LevelMark.initial(-3));
	}

	@Test
	void extremes() {
		assertEquals(Integer.MAX_VALUE, LevelMark.observe(0, Integer.MAX_VALUE, 0).triggers());
		assertEquals(0, LevelMark.observe(Integer.MAX_VALUE, 0, Integer.MAX_VALUE).triggers());
		assertEquals(0, LevelMark.observe(5, 5, 5).triggers());
	}
}
