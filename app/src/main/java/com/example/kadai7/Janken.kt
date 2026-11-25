package com.example.kadai7

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlin.random.Random

/**
 * じゃんけんの手
 *
 * 手はそれぞれ動物で表し、勝負の瞬間に手の形へ「変身」する。
 * - グー：カメ → 甲羅にこもって丸くなる
 * - チョキ：キツネ → 2本の耳がチョキの形
 * - パー：ワシ → 翼を大きく広げる
 */
enum class Hand(
    @param:StringRes val nameRes: Int,
    @param:StringRes val animalRes: Int,
    val emoji: String,
    @param:DrawableRes val idleRes: Int,   // ふだんの姿
    @param:DrawableRes val poseRes: Int,   // 変身後（手の形）
    @param:ColorRes val colorRes: Int,
) {
    ROCK(R.string.hand_rock, R.string.animal_turtle, "🐢",
        R.drawable.animal_turtle_idle, R.drawable.animal_turtle_pose, R.color.rock),
    SCISSORS(R.string.hand_scissors, R.string.animal_fox, "🦊",
        R.drawable.animal_fox_idle, R.drawable.animal_fox_pose, R.color.scissors),
    PAPER(R.string.hand_paper, R.string.animal_eagle, "🦅",
        R.drawable.animal_eagle_idle, R.drawable.animal_eagle_pose, R.color.paper);

    /** この手が other に勝つかどうか */
    fun beats(other: Hand): Boolean = when (this) {
        ROCK -> other == SCISSORS
        SCISSORS -> other == PAPER
        PAPER -> other == ROCK
    }

    /** この手に勝てる手 */
    fun counter(): Hand = entries.first { it.beats(this) }
}

/** 勝敗（プレイヤーから見た結果） */
enum class Result { WIN, LOSE, DRAW }

/** プレイヤーの手とコンピュータの手から勝敗を判定する */
fun judge(player: Hand, cpu: Hand): Result = when {
    player == cpu -> Result.DRAW
    player.beats(cpu) -> Result.WIN
    else -> Result.LOSE
}

/** AIの予想（どの手を出しそうか・どれくらい自信があるか） */
data class Prediction(val hand: Hand, val confidence: Double)

/**
 * 読心AI
 *
 * 「前回プレイヤーが出した手」と「前回の勝敗」の組み合わせ（9通り）ごとに、
 * 次にプレイヤーが出した手を数えておく。人は「勝ったら同じ手」「負けたら変える」
 * などのクセを持つので、同じ状況になったときに一番多かった手を予想する。
 * データが少ないうちは、全体でよく出す手から予想する。
 */
class MindReader(private val random: Random = Random.Default) {

    private val contextCounts = Array(CONTEXTS) { IntArray(3) }
    private val totalCounts = IntArray(3)
    private var lastContext = -1

    /** 次にプレイヤーが出しそうな手を予想する */
    fun predict(): Prediction {
        val ctx = lastContext
        val counts = if (ctx in 0 until CONTEXTS && contextCounts[ctx].sum() >= 2) contextCounts[ctx] else totalCounts
        val total = counts.sum()
        if (total == 0) return Prediction(Hand.entries.random(random), 1.0 / 3)
        // ラプラス平滑化（データが少ないときに極端な確率にならないようにする）
        val best = counts.indices.maxByOrNull { counts[it] * 3 + random.nextInt(3) }!!
        val confidence = (counts[best] + 1.0) / (total + 3.0)
        return Prediction(Hand.entries[best], confidence)
    }

    /** AIが出す手を決める（自信があれば予想した手に勝つ手、なければランダム） */
    fun choose(prediction: Prediction = predict()): Hand =
        if (prediction.confidence >= CONFIDENCE_THRESHOLD && random.nextDouble() > EXPLORE_RATE) {
            prediction.hand.counter()
        } else {
            Hand.entries.random(random)
        }

    /** 実際にプレイヤーが出した手と勝敗を記録して学習する */
    fun learn(player: Hand, result: Result) {
        if (lastContext in 0 until CONTEXTS) contextCounts[lastContext][player.ordinal]++
        totalCounts[player.ordinal]++
        lastContext = player.ordinal * 3 + result.ordinal
    }

    /** 保存用の文字列にする */
    fun serialize(): String =
        (contextCounts.flatMap { it.toList() } + totalCounts.toList() + lastContext).joinToString(",")

    fun restore(data: String?) {
        val values = data?.split(",")?.map { it.toIntOrNull() ?: return } ?: return
        // 保存データが壊れていたら使わない（範囲外の番号や負の回数で落ちないように）
        if (values.size != CONTEXTS * 3 + 3 + 1) return
        if (values.dropLast(1).any { it < 0 } || values.last() !in -1 until CONTEXTS) return
        for (c in 0 until CONTEXTS) for (h in 0 until 3) contextCounts[c][h] = values[c * 3 + h]
        for (h in 0 until 3) totalCounts[h] = values[CONTEXTS * 3 + h]
        lastContext = values.last()
    }

    fun clear() {
        contextCounts.forEach { it.fill(0) }
        totalCounts.fill(0)
        lastContext = -1
    }

    companion object {
        private const val CONTEXTS = 9
        private const val CONFIDENCE_THRESHOLD = 0.4
        private const val EXPLORE_RATE = 0.15   // たまにわざとランダムにして、勝てる余地を残す
    }
}

/**
 * 「あなたのクセ」のまとめ
 * @property total 全ての回数（あいこも含む）
 * @property top いちばん多く選んだ手（同じ回数なら複数）
 */
data class HabitSummary(val total: Int, val top: List<Hand>, val topCount: Int) {
    /** 傾向を言えるだけの回数があるか */
    val enough get() = total >= MIN_ROUNDS

    companion object {
        const val MIN_ROUNDS = 5

        fun of(counts: IntArray): HabitSummary {
            val max = counts.max()
            return HabitSummary(counts.sum(), Hand.entries.filter { counts[it.ordinal] == max }, max)
        }
    }
}

fun percentOf(count: Int, total: Int): Int = if (total == 0) 0 else Math.round(count * 100f / total)

/** 通算成績 */
data class Stats(
    var wins: Int = 0,
    var losses: Int = 0,
    var draws: Int = 0,
    var streak: Int = 0,
    var bestStreak: Int = 0,
    val handCounts: IntArray = IntArray(3),
) {
    val rounds get() = wins + losses + draws

    fun record(player: Hand, result: Result) {
        handCounts[player.ordinal]++
        when (result) {
            Result.WIN -> {
                wins++
                streak++
                if (streak > bestStreak) bestStreak = streak
            }
            Result.LOSE -> {
                losses++
                streak = 0
            }
            Result.DRAW -> draws++
        }
    }

    /** 勝率（あいこを除く） */
    fun winRate(): Int {
        val decided = wins + losses
        return if (decided == 0) 0 else Math.round(wins * 100f / decided)
    }
}
