package com.example.kadai7

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JankenTest {
    @Test
    fun judge_allCombinations() {
        assertEquals(Result.WIN, judge(Hand.ROCK, Hand.SCISSORS))
        assertEquals(Result.WIN, judge(Hand.SCISSORS, Hand.PAPER))
        assertEquals(Result.WIN, judge(Hand.PAPER, Hand.ROCK))
        assertEquals(Result.LOSE, judge(Hand.SCISSORS, Hand.ROCK))
        assertEquals(Result.LOSE, judge(Hand.PAPER, Hand.SCISSORS))
        assertEquals(Result.LOSE, judge(Hand.ROCK, Hand.PAPER))
        for (h in Hand.entries) assertEquals(Result.DRAW, judge(h, h))
    }

    @Test
    fun counter_beatsHand() {
        for (h in Hand.entries) assertTrue(h.counter().beats(h))
    }

    @Test
    fun mindReader_learnsRepeatedHand() {
        val ai = MindReader(Random(1))
        repeat(10) { ai.learn(Hand.ROCK, Result.DRAW) }
        val p = ai.predict()
        assertEquals(Hand.ROCK, p.hand)
        assertTrue(p.confidence > 0.7)
    }

    @Test
    fun mindReader_learnsCycle() {
        // グー → チョキ → パー → グー … と順番に出す人
        val ai = MindReader(Random(2))
        val cycle = listOf(Hand.ROCK, Hand.SCISSORS, Hand.PAPER)
        repeat(30) { ai.learn(cycle[it % 3], Result.DRAW) }
        // 最後に出したのはパー（29 % 3 == 2）なので、次はグーと予想するはず
        assertEquals(Hand.ROCK, ai.predict().hand)
    }

    @Test
    fun mindReader_serializeRestore() {
        val a = MindReader(Random(3))
        repeat(5) { a.learn(Hand.PAPER, Result.WIN) }
        val b = MindReader(Random(3))
        b.restore(a.serialize())
        assertEquals(a.serialize(), b.serialize())
        assertEquals(Hand.PAPER, b.predict().hand)
    }

    @Test
    fun mindReader_ignoresCorruptedData() {
        val ai = MindReader(Random(4))
        val good = ai.serialize()
        // 最後の値（前回の状況の番号）が範囲外、負の回数、数字でない値、長さ違い
        val parts = good.split(",").toMutableList()
        for (bad in listOf(
            (parts.dropLast(1) + "99").joinToString(","),
            (listOf("-5") + parts.drop(1)).joinToString(","),
            (listOf("x") + parts.drop(1)).joinToString(","),
            "1,2,3",
        )) {
            ai.restore(bad)
            ai.predict()                       // 落ちない
            ai.learn(Hand.ROCK, Result.WIN)    // 落ちない
            ai.clear()
        }
    }

    @Test
    fun habits_singleTop() {
        // グー3・チョキ6・パー1 → キツネ（チョキ）がいちばん多い
        val s = HabitSummary.of(intArrayOf(3, 6, 1))
        assertEquals(10, s.total)
        assertEquals(listOf(Hand.SCISSORS), s.top)
        assertEquals(6, s.topCount)
        assertEquals(60, percentOf(6, 10))
        assertTrue(s.enough)
    }

    @Test
    fun habits_tieIsReported() {
        // カメ3・キツネ3・ワシ1 → カメとキツネが同じ
        val s = HabitSummary.of(intArrayOf(3, 3, 1))
        assertEquals(listOf(Hand.ROCK, Hand.SCISSORS), s.top)
        assertEquals(3, s.topCount)
        assertEquals(3, HabitSummary.of(intArrayOf(2, 2, 2)).top.size)
    }

    @Test
    fun habits_fewRoundsNotEnough() {
        val s = HabitSummary.of(intArrayOf(2, 1, 1))
        assertEquals(4, s.total)
        assertTrue(!s.enough)
        assertEquals(0, percentOf(0, 0))
    }

    @Test
    fun stats_streakAndWinRate() {
        val s = Stats()
        s.record(Hand.ROCK, Result.WIN)
        s.record(Hand.ROCK, Result.WIN)
        s.record(Hand.PAPER, Result.LOSE)
        s.record(Hand.PAPER, Result.DRAW)
        assertEquals(0, s.streak)
        assertEquals(2, s.bestStreak)
        assertEquals(67, s.winRate())
        assertEquals(4, s.rounds)
    }
}
