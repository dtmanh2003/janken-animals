package com.example.kadai7

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.doOnLayout
import java.text.NumberFormat
import kotlin.math.abs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * じゃんけんアプリ「じゃんけんマインド」（AIと対戦）
 *
 * - 手は動物：グー＝カメ、チョキ＝キツネ、パー＝ワシ。勝負の瞬間に手の形へ変身する
 * - AIはあなたのクセを学習して次の手を読んでくる
 * - あなたのクセ：全ての回で、どの動物を何回選んだか
 * - AIの的中率：直近最大20回で、AIがあなたの手を当てた回数
 * - 成績・連勝記録・AIの記憶はアプリを閉じても保存される
 */
class MainActivity : AppCompatActivity() {

    /** 1回分の勝負 */
    private data class Round(val player: Hand, val cpu: Hand, val predicted: Hand) {
        val result get() = judge(player, cpu)
    }

    private lateinit var prefs: SharedPreferences
    private val ai = MindReader()
    private var stats = Stats()
    private val recentHits = ArrayDeque<Boolean>()   // AIの予想が当たったかどうか（直近の分）
    private var lastRound: Round? = null
    private var playing = false
    private val running = mutableListOf<Animator>()

    private lateinit var arena: View
    private lateinit var boxPlayer: View
    private lateinit var boxCpu: View
    private lateinit var imgPlayerIdle: ImageView
    private lateinit var imgPlayerPose: ImageView
    private lateinit var imgCpuIdle: ImageView
    private lateinit var imgCpuPose: ImageView
    private lateinit var txtPlayerName: TextView
    private lateinit var txtCpuName: TextView
    private lateinit var txtResult: TextView
    private lateinit var txtStreak: TextView
    private lateinit var meter: LinearProgressIndicator
    private lateinit var txtMeter: TextView
    private lateinit var txtAiScope: TextView
    private lateinit var txtAiNote: TextView
    private lateinit var txtWins: TextView
    private lateinit var txtLosses: TextView
    private lateinit var txtDraws: TextView
    private lateinit var txtWinRate: TextView
    private lateinit var txtHabitScope: TextView
    private lateinit var txtHabit: TextView
    private lateinit var habitRows: Map<Hand, View>
    private lateinit var btnReset: View
    private lateinit var btnLanguage: TextView
    private lateinit var handButtons: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arena = findViewById(R.id.arena)
        boxPlayer = findViewById(R.id.boxPlayer)
        boxCpu = findViewById(R.id.boxCpu)
        imgPlayerIdle = findViewById(R.id.imgPlayerIdle)
        imgPlayerPose = findViewById(R.id.imgPlayerPose)
        imgCpuIdle = findViewById(R.id.imgCpuIdle)
        imgCpuPose = findViewById(R.id.imgCpuPose)
        txtPlayerName = findViewById(R.id.txtPlayerName)
        txtCpuName = findViewById(R.id.txtCpuName)
        txtResult = findViewById(R.id.txtResult)
        txtStreak = findViewById(R.id.txtStreak)
        meter = findViewById(R.id.meter)
        txtMeter = findViewById(R.id.txtMeter)
        txtAiScope = findViewById(R.id.txtAiScope)
        txtAiNote = findViewById(R.id.txtAiNote)
        txtWins = findViewById(R.id.txtWins)
        txtLosses = findViewById(R.id.txtLosses)
        txtDraws = findViewById(R.id.txtDraws)
        txtWinRate = findViewById(R.id.txtWinRate)
        findViewById<TextView>(R.id.txtWinRateLabel).setText(R.string.win_rate)
        txtHabitScope = findViewById(R.id.txtHabitScope)
        txtHabit = findViewById(R.id.txtHabit)
        habitRows = mapOf(
            Hand.SCISSORS to findViewById(R.id.rowScissors),
            Hand.ROCK to findViewById(R.id.rowRock),
            Hand.PAPER to findViewById(R.id.rowPaper),
        )

        val rock = findViewById<View>(R.id.btnRock)
        val scissors = findViewById<View>(R.id.btnScissors)
        val paper = findViewById<View>(R.id.btnPaper)
        handButtons = listOf(rock, scissors, paper)
        rock.setOnClickListener { play(Hand.ROCK) }
        scissors.setOnClickListener { play(Hand.SCISSORS) }
        paper.setOnClickListener { play(Hand.PAPER) }
        // 押したときに少し沈む動きは res/animator/press_scale.xml（スタイル HandButton で指定）

        btnReset = findViewById(R.id.btnReset)
        btnReset.setOnClickListener { confirmReset() }

        // 言語の切り替え（ボタンには 🌐 と、選んでいる言語のコードを表示）
        btnLanguage = findViewById(R.id.btnLanguage)
        btnLanguage.text = Languages.currentTag()?.let { "🌐 ${it.uppercase()}" } ?: "🌐"
        btnLanguage.setOnClickListener { if (!playing) Languages.showPicker(this) }

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        load()

        // 画面が作り直された（回転など）ときは、直前の勝負の結果をそのまま表示する。
        // 勝負は始めた時点で記録済みなので、二重に数えたり消えたりしない
        lastRound = savedInstanceState?.let { restoreRound(it) }
        val round = lastRound
        boxPlayer.doOnLayout {
            if (round != null) showFinal(round) else showIdle(Hand.SCISSORS, Hand.PAPER)
        }
        updateStats(round)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastRound?.let {
            outState.putInt(KEY_ROUND_PLAYER, it.player.ordinal)
            outState.putInt(KEY_ROUND_CPU, it.cpu.ordinal)
            outState.putInt(KEY_ROUND_PREDICTED, it.predicted.ordinal)
        }
    }

    override fun onDestroy() {
        cancelRunning()
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // ゲーム本体
    // ---------------------------------------------------------------

    /** 1回勝負する */
    private fun play(player: Hand) {
        if (playing) return
        setPlaying(true)
        haptic(HapticFeedbackConstants.KEYBOARD_TAP)

        // AIは「あなたが選ぶ前の履歴」だけで予想する（ズルはしない）
        val prediction = ai.predict()
        val round = Round(player, ai.choose(prediction), prediction.hand)

        // アニメーションの前に記録して保存する。
        // 途中で画面を回転しても、勝負はちょうど1回だけ数えられる
        recentHits.addLast(round.predicted == player)
        while (recentHits.size > RECENT_SIZE) recentHits.removeFirst()
        ai.learn(player, round.result)
        stats.record(player, round.result)
        save()
        lastRound = round

        animateRound(round)
    }

    /** 両方の動物が同時に変身して、終わったら結果を表示する */
    private fun animateRound(round: Round) {
        cancelRunning()
        resetBox(boxPlayer, 1f)
        resetBox(boxCpu, -1f)
        txtPlayerName.text = ""
        txtCpuName.text = ""
        txtResult.setText(R.string.chant)
        txtResult.setTextColor(color(R.color.text_main))
        setArenaStroke(R.color.card_stroke)

        // 4つのアニメーション（両者のふだんの姿・変身後）を1つにまとめ、
        // 全部終わった瞬間に結果を出す。端末のアニメーション速度の設定を変えても、ずれない
        val set = AnimatorSet().apply {
            playTogether(
                transform(imgPlayerIdle, imgPlayerPose, round.player) +
                    transform(imgCpuIdle, imgCpuPose, round.cpu)
            )
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    running.remove(animation)
                    if (!cancelled && !isDestroyed) showResult(round, animate = true)
                }
            })
        }
        running += set
        set.start()
    }

    /** 変身アニメーションを準備する（まだ始めない） */
    private fun transform(idle: ImageView, pose: ImageView, hand: Hand): List<Animator> {
        val m = AnimalMotion.of(hand)
        idle.setImageResource(hand.idleRes)
        pose.setImageResource(hand.poseRes)
        val k = AnimalMotion.cssPx(idle, m)
        AnimalMotion.applyStart(idle, m.idle, k)
        AnimalMotion.applyStart(pose, m.pose, k, m.poseBox)
        return listOf(
            AnimalMotion.animator(idle, m.idle, k),
            AnimalMotion.animator(pose, m.pose, k, m.poseBox),
        )
    }

    /** 勝敗を表示する（animate = false は画面を作り直したときの再表示） */
    private fun showResult(round: Round, animate: Boolean) {
        val result = round.result
        txtPlayerName.setText(round.player.nameRes)
        txtCpuName.setText(round.cpu.nameRes)
        txtPlayerName.setTextColor(color(round.player.colorRes))
        txtCpuName.setTextColor(color(round.cpu.colorRes))

        val (msg, col) = when (result) {
            Result.WIN -> R.string.msg_win to R.color.win
            Result.LOSE -> R.string.msg_lose to R.color.lose
            Result.DRAW -> R.string.msg_draw to R.color.draw
        }
        txtResult.setText(msg)
        txtResult.setTextColor(color(col))
        setArenaStroke(col)

        // 勝った方・負けた方（あいこのときはどちらも null）
        val (winnerBox, winnerHand, loserPose, loserHand) = when (result) {
            Result.WIN -> Quad(boxPlayer, round.player, imgCpuPose, round.cpu)
            Result.LOSE -> Quad(boxCpu, round.cpu, imgPlayerPose, round.player)
            Result.DRAW -> Quad(null, null, null, null)
        }
        updateStats(round)

        if (!animate) {
            // 画面を作り直したとき：演出なしで最後の状態だけ表示する
            if (winnerBox != null && winnerHand != null) {
                val scale = winScale(winnerHand)
                winnerBox.scaleX = scale * sign(winnerBox)
                winnerBox.scaleY = scale
            }
            (loserPose?.parent as? View)?.alpha = LOSER_ALPHA
            setPlaying(false)
            return
        }

        popText(txtResult)
        haptic(
            if (result == Result.WIN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.LONG_PRESS
        )

        // 結果が出たら次の勝負を選べる。新しい勝負は前回の演出を止める。
        setPlaying(false)
        if (winnerBox == null || winnerHand == null || loserPose == null || loserHand == null) {
            return
        }
        val loserBox = loserPose.parent as View
        val aftermath = AnimatorSet().apply {
            playTogether(
                cry(loserPose, loserHand).apply { startDelay = 0L },
                celebrate(winnerBox, winnerHand),
            )
        }
        // 相手のところへ動く方の動物。もう一方の箱の上に重ねて描く
        // （CPU側の列はあとから描かれるので、何もしないと「あなた」の動物が後ろに隠れてしまう）
        val mover = if (winnerHand == Hand.ROCK) loserBox else winnerBox
        val moverColumn = mover.parent as View
        moverColumn.translationZ = dp(8f)
        val interaction = when (winnerHand) {
            Hand.ROCK -> foxHitsShell(loserBox, winnerBox, aftermath)
            Hand.SCISSORS -> foxCatchesEagle(winnerBox, loserBox, aftermath)
            Hand.PAPER -> eagleLiftsTurtle(winnerBox, loserBox, aftermath)
        }
        val set = AnimatorSet().apply {
            play(interaction)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    running.remove(animation)
                    moverColumn.translationZ = 0f
                }
            })
        }
        running += set
        set.start()
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    /** 勝った方を大きくする倍率。ワシは翼が枠のふちに近いので大きくしない */
    private fun winScale(hand: Hand) = if (hand == Hand.PAPER) 1f else WIN_SCALE

    private fun confirmReset() {
        if (playing) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_title)
            .setMessage(R.string.reset_message)
            .setNegativeButton(R.string.reset_cancel, null)
            .setPositiveButton(R.string.reset_ok) { _, _ -> reset() }
            .show()
    }

    private fun reset() {
        if (playing) return
        cancelRunning()
        stats = Stats()
        ai.clear()
        recentHits.clear()
        lastRound = null
        save()
        showIdle(Hand.SCISSORS, Hand.PAPER)
        txtPlayerName.text = ""
        txtCpuName.text = ""
        txtResult.setText(R.string.msg_start)
        txtResult.setTextColor(color(R.color.text_main))
        setArenaStroke(R.color.card_stroke)
        updateStats(null)
    }

    /** 勝負中は手のボタンもリセットも押せないようにする */
    private fun setPlaying(value: Boolean) {
        playing = value
        handButtons.forEach { it.isEnabled = !value }
        btnReset.isEnabled = !value
        btnLanguage.isEnabled = !value
    }

    // ---------------------------------------------------------------
    // 動物の表示
    // ---------------------------------------------------------------

    /** ふだんの姿で止めて表示する（HTML版の .start の位置・大きさ） */
    private fun showIdle(player: Hand, cpu: Hand) {
        resetBox(boxPlayer, 1f)
        resetBox(boxCpu, -1f)
        restIdle(imgPlayerIdle, imgPlayerPose, player)
        restIdle(imgCpuIdle, imgCpuPose, cpu)
    }

    private fun restIdle(idle: ImageView, pose: ImageView, hand: Hand) {
        val m = AnimalMotion.of(hand)
        idle.setImageResource(hand.idleRes)
        AnimalMotion.apply(idle, m.idleRest, AnimalMotion.cssPx(idle, m), alpha = 1f)
        pose.alpha = 0f
    }

    /** 変身後の姿で止めて、結果も表示する（画面を作り直したとき用） */
    private fun showFinal(round: Round) {
        resetBox(boxPlayer, 1f)
        resetBox(boxCpu, -1f)
        restPose(imgPlayerIdle, imgPlayerPose, round.player)
        restPose(imgCpuIdle, imgCpuPose, round.cpu)
        showResult(round, animate = false)
    }

    private fun restPose(idle: ImageView, pose: ImageView, hand: Hand) {
        val m = AnimalMotion.of(hand)
        idle.alpha = 0f
        pose.setImageResource(hand.poseRes)
        AnimalMotion.apply(pose, AnimalMotion.Tr(), AnimalMotion.cssPx(pose, m), m.poseBox, alpha = 1f)
    }

    // ---------------------------------------------------------------
    // 表示の更新
    // ---------------------------------------------------------------

    private fun updateStats(round: Round?) {
        updateScore()
        updateHabits()
        updateAi(round)
    }

    private fun updateScore() {
        // 数字は言語に合わせた書き方にする（例：1,000 / 1.000）
        val nf = NumberFormat.getIntegerInstance()
        txtWins.text = nf.format(stats.wins)
        txtLosses.text = nf.format(stats.losses)
        txtDraws.text = nf.format(stats.draws)
        // 勝率はあいこを除いて計算する（ラベルにも明記）。勝ち負けが0回なら「―」
        txtWinRate.text = if (stats.wins + stats.losses == 0) "–"
        else getString(R.string.percent_format, stats.winRate())
        txtStreak.text = getString(R.string.streak_format, stats.streak, stats.bestStreak)
        txtStreak.visibility = if (stats.bestStreak > 0) View.VISIBLE else View.INVISIBLE
    }

    /** あなたのクセ：動物ごとの回数と割合（全ての回、あいこも含む） */
    private fun updateHabits() {
        val counts = stats.handCounts
        val summary = HabitSummary.of(counts)
        // 英語では「1 round / 2 rounds」のように数で形が変わるので plurals を使う
        txtHabitScope.text = plural(R.plurals.habit_scope, summary.total, summary.total)

        for ((hand, row) in habitRows) {
            val count = counts[hand.ordinal]
            val percent = percentOf(count, summary.total)
            row.findViewById<TextView>(R.id.txtLabel).text =
                getString(R.string.habit_row_label, hand.emoji, getString(hand.animalRes), getString(hand.nameRes))
            row.findViewById<TextView>(R.id.txtCount).text =
                plural(R.plurals.habit_row_value, summary.total, count, summary.total, percent)
            row.findViewById<LinearProgressIndicator>(R.id.bar).apply {
                setIndicatorColor(color(hand.colorRes))
                setProgressCompat(percent, true)
            }
        }

        val top = summary.top.map { getString(it.animalRes) }
        txtHabit.text = when {
            !summary.enough -> getString(R.string.habit_none)
            top.size == 1 -> plural(R.plurals.habit_top, summary.total, top[0], summary.topCount, summary.total)
            top.size == 2 -> plural(R.plurals.habit_tie2, summary.topCount, top[0], top[1], summary.topCount)
            else -> plural(R.plurals.habit_tie3, summary.topCount, summary.topCount)
        }
    }

    private fun plural(id: Int, quantity: Int, vararg args: Any) =
        resources.getQuantityString(id, quantity, *args)

    /** AIの的中率（直近最大20回）と、直前の勝負でAIが何を予想したか */
    private fun updateAi(round: Round?) {
        val hits = recentHits.count { it }
        val tries = recentHits.size
        val rate = percentOf(hits, tries)
        meter.setProgressCompat(rate, true)
        // まだ1回も勝負していないときは 0% ではなく「データなし」と出す
        txtMeter.text = if (tries == 0) getString(R.string.ai_no_data) else getString(R.string.percent_format, rate)
        meter.setIndicatorColor(
            color(
                when {
                    rate >= 50 -> R.color.lose
                    rate >= 34 -> R.color.draw
                    else -> R.color.win
                }
            )
        )
        txtAiScope.text = plural(R.plurals.ai_meter_scope, tries, hits, tries)
        txtAiScope.visibility = if (tries > 0) View.VISIBLE else View.GONE

        txtAiNote.text = if (round == null) {
            getString(R.string.ai_learning)
        } else {
            val predicted = getString(round.predicted.nameRes)
            val played = getString(round.player.nameRes)
            getString(if (round.predicted == round.player) R.string.ai_hit else R.string.ai_miss, predicted, played)
        }
    }

    // ---------------------------------------------------------------
    // 演出
    // ---------------------------------------------------------------

    private fun popText(view: View) {
        view.scaleX = 0.6f
        view.scaleY = 0.6f
        view.animate().scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(3f)).setDuration(380).start()
    }

    /**
     * 勝った動物の喜ぶ動き（アニメーションを作って返す。始めるのは呼び出し側）
     * - キツネ：ぴょんと跳ねる
     * - カメ：左右にゆらゆら
     * - ワシ：翼をバサッと縮めて戻す
     */
    private fun celebrate(box: View, hand: Hand): Animator {
        val pose = if (box === boxPlayer) imgPlayerPose else imgCpuPose
        val baseY = pose.translationY
        val baseScaleY = pose.scaleY
        val baseRotation = pose.rotation
        val bounce = when (hand) {
            Hand.SCISSORS -> ObjectAnimator.ofFloat(
                pose, View.TRANSLATION_Y, baseY, baseY + dp(2f), baseY - dp(9f), baseY
            )
            Hand.ROCK -> ObjectAnimator.ofFloat(
                pose, View.ROTATION, baseRotation, baseRotation + 7f, baseRotation,
                baseRotation + 5f, baseRotation
            )
            Hand.PAPER -> ObjectAnimator.ofFloat(
                pose, View.SCALE_Y, baseScaleY, baseScaleY * 0.82f, baseScaleY
            )
        }.apply {
            duration = if (hand == Hand.ROCK) 460L else 360L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 途中で止められても、元の位置・大きさに戻す
                    pose.translationY = baseY
                    pose.scaleY = baseScaleY
                    pose.rotation = baseRotation
                }
            })
        }
        val scale = winScale(hand)
        box.animate().scaleX(scale * sign(box)).scaleY(scale).setDuration(220).start()
        return bounce
    }

    /**
     * グー（カメ）がチョキ（キツネ）に勝ったときの特別な演出
     * キツネがカメの甲羅に向かって突進 → ゴツン！とはね返され、カメは少しゆれる → キツネが泣く
     *
     * @param fox キツネの箱  @param turtle カメの箱  @param cry キツネが泣くアニメーション
     */
    private fun foxHitsShell(fox: View, turtle: View, cry: Animator): Animator {
        // 箱の中心の位置を「列（左右反転していない親）」を基準に求める。
        // CPU側の箱は scaleX = -1 で反転しているため、箱そのものの getLocationInWindow は
        // 右端の座標を返してしまい、距離が箱1つ分ずれる（キツネがカメを通り抜けてしまう）
        val gap = centerX(turtle) - centerX(fox)
        val direction = if (gap < 0f) -1f else 1f
        // 甲羅に少しめり込むところまで（箱の幅の4割手前で止まる）
        val reach = direction * (abs(gap) - minOf(fox.width, turtle.width) * 0.4f).coerceAtLeast(0f)

        val foxPose = poseIn(fox)
        val charge = ObjectAnimator.ofFloat(
            fox, View.TRANSLATION_X, 0f, -direction * dp(4f), reach
        ).apply {
            duration = 210L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    foxPose.setImageResource(Hand.SCISSORS.idleRes)
                }
            })
        }
        val recoil = ObjectAnimator.ofFloat(
            fox, View.TRANSLATION_X, reach, -direction * dp(8f), 0f
        ).apply {
            startDelay = 150L
            duration = 210L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    haptic(HapticFeedbackConstants.LONG_PRESS)
                }
                override fun onAnimationEnd(animation: Animator) {
                    foxPose.setImageResource(Hand.SCISSORS.poseRes)
                }
            })
        }

        // カメ：勝ったので少し大きくなり、ぶつかられた瞬間に小さくゆれる
        val scale = winScale(Hand.ROCK)
        turtle.animate().scaleX(scale * sign(turtle)).scaleY(scale).setDuration(220).start()
        val shell = if (turtle === boxPlayer) imgPlayerPose else imgCpuPose
        val baseRotation = shell.rotation
        val thud = ObjectAnimator.ofFloat(
            shell, View.ROTATION, baseRotation, baseRotation - 4f * direction, baseRotation + 2f * direction, baseRotation
        ).apply {
            duration = 260L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    shell.rotation = baseRotation
                }
            })
        }

        cry.startDelay = 0L
        return AnimatorSet().apply {
            play(charge).before(recoil)
            play(recoil).with(thud)
            play(cry).after(recoil)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 途中で止められても、キツネを元の位置に戻す
                    fox.translationX = 0f
                    foxPose.setImageResource(Hand.SCISSORS.poseRes)
                }
            })
        }
    }

    /** その箱の「変身後」の絵 */
    private fun poseIn(box: View): ImageView = if (box === boxPlayer) imgPlayerPose else imgCpuPose

    /**
     * チョキ（キツネ）がパー（ワシ）に勝ったとき：
     * キツネがワシに飛びかかる → ワシは驚いて跳ね、キツネは羽根をくわえる → ワシが泣く
     *
     * @param fox キツネの箱  @param eagle ワシの箱  @param crying からんだあとに続ける演出（喜ぶ・泣く）
     */
    private fun foxCatchesEagle(fox: View, eagle: View, crying: Animator): Animator {
        val reach = approachDistance(fox, eagle)
        val pounce = ObjectAnimator.ofFloat(fox, View.TRANSLATION_X, 0f, reach).apply {
            duration = 220L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    haptic(HapticFeedbackConstants.LONG_PRESS)   // 飛びかかった瞬間にブルッ
                }
            })
        }
        val foxBox = fox as ViewGroup
        val pose = poseIn(fox)
        val feather = TextView(this).apply {
            text = "🪶"
            textSize = 18f
            includeFontPadding = false
            rotation = 24f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            measure(0, 0)
        }
        val retreat = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(fox, View.TRANSLATION_X, reach, 0f),
                ObjectAnimator.ofFloat(eagle, View.TRANSLATION_Y, 0f, -dp(14f), 0f)
            )
            duration = 420L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    val image = pose.drawable ?: return
                    val mouth = floatArrayOf(image.bounds.width() * 0.54f, image.bounds.height() * 0.80f)
                    pose.imageMatrix.mapPoints(mouth)
                    val x = pose.left + pose.translationX + pose.pivotX +
                        (pose.paddingLeft + mouth[0] - pose.pivotX) * pose.scaleX
                    val y = pose.top + pose.translationY + pose.pivotY +
                        (pose.paddingTop + mouth[1] - pose.pivotY) * pose.scaleY
                    val left = x.toInt() - feather.measuredWidth / 4
                    val top = y.toInt() - feather.measuredHeight / 2
                    feather.layout(left, top, left + feather.measuredWidth, top + feather.measuredHeight)
                    foxBox.overlay.add(feather)
                }
            })
        }
        return AnimatorSet().apply {
            val holdFeather = ValueAnimator.ofFloat(0f, 1f).apply { duration = 240L }
            playSequentially(pounce, retreat, holdFeather, crying)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    foxBox.overlay.remove(feather)
                }
            })
        }
    }

    /**
     * パー（ワシ）がグー（カメ）に勝ったとき：
     * ワシがカメの真上まで飛んでいく → カメをつかんで持ち上げ、下ろす → 元の場所へ戻る → カメが泣く
     *
     * @param eagle ワシの箱  @param turtle カメの箱  @param crying からんだあとに続ける演出（喜ぶ・泣く）
     */
    private fun eagleLiftsTurtle(eagle: View, turtle: View, crying: Animator): Animator {
        val reach = centerX(turtle) - centerX(eagle)   // 真上まで行くので、重なり分は引かない
        // 甲羅の上で止まる高さ。高すぎると、持ち上げたときにワシの頭が「あなた／CPU」の文字に重なる
        val gapAboveShell = dp(16f)
        val approach = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(eagle, View.TRANSLATION_X, 0f, reach),
                ObjectAnimator.ofFloat(eagle, View.TRANSLATION_Y, 0f, -gapAboveShell)
            )
            duration = 260L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    haptic(HapticFeedbackConstants.LONG_PRESS)   // カメをつかんだ瞬間にブルッ
                }
            })
        }
        val turtlePose = poseIn(turtle)
        val restingScale = turtlePose.scaleY
        val startled = ObjectAnimator.ofFloat(
            turtlePose, View.SCALE_Y, restingScale, restingScale * 1.12f, restingScale
        ).apply {
            duration = 180L
            addListener(object : AnimatorListenerAdapter() {

                override fun onAnimationEnd(animation: Animator) {
                    turtlePose.scaleY = restingScale
                }
            })
        }
        val lift = AnimatorSet().apply {
            val height = dp(9f)
            playTogether(
                ObjectAnimator.ofFloat(eagle, View.TRANSLATION_Y, -gapAboveShell, -gapAboveShell - height, -gapAboveShell - height, -gapAboveShell),
                ObjectAnimator.ofFloat(turtle, View.TRANSLATION_Y, 0f, -height, -height, 0f)
            )
            duration = 660L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    turtlePose.setImageResource(R.drawable.animal_turtle_surprised)
                }

                override fun onAnimationEnd(animation: Animator) {
                    turtlePose.setImageResource(Hand.ROCK.poseRes)
                }
            })
        }
        val retreat = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(eagle, View.TRANSLATION_X, reach, 0f),
                ObjectAnimator.ofFloat(eagle, View.TRANSLATION_Y, -gapAboveShell, 0f)
            )
            duration = 200L
        }
        return AnimatorSet().apply {
            playSequentially(approach, startled, lift, retreat, crying)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    turtlePose.setImageResource(Hand.ROCK.poseRes)
                }
            })
        }
    }

    /** from の箱が to の箱に少し重なるところ（箱の幅の4割手前）まで動く距離。向きも含む */
    private fun approachDistance(from: View, to: View): Float {
        val gap = centerX(to) - centerX(from)
        val direction = if (gap < 0f) -1f else 1f
        return direction * (abs(gap) - minOf(from.width, to.width) * 0.4f).coerceAtLeast(0f)
    }

    /** 箱の中心のX座標（画面全体の座標）。左右反転していない親の位置から計算する */
    private fun centerX(box: View): Float {
        val parentLocation = IntArray(2)
        (box.parent as View).getLocationInWindow(parentLocation)
        return parentLocation[0] + box.left + box.width / 2f
    }

    /**
     * 負けた動物が泣く動き（アニメーションを作って返す。始めるのは呼び出し側）
     * 少しうつむいて泣き顔の絵に切り替わり、最後は元の絵に戻って薄くなる
     */
    private fun cry(view: ImageView, hand: Hand): Animator {
        val sadPose = when (hand) {
            Hand.SCISSORS -> R.drawable.animal_fox_cry
            Hand.ROCK -> R.drawable.animal_turtle_cry
            Hand.PAPER -> R.drawable.animal_eagle_cry
        }
        val startY = view.translationY
        val startScaleX = view.scaleX
        val startScaleY = view.scaleY
        val dip = dp(if (hand == Hand.ROCK) 7f else 5f)
        var showingSadPose = false
        val reaction = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 220L
            duration = 960L
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                // time：0〜1。0.16〜0.87 の間だけ泣き顔の絵を出す
                val time = animation.animatedFraction
                val sad = time >= 0.16f && time < 0.87f
                if (showingSadPose != sad) {
                    view.setImageResource(if (sad) sadPose else hand.poseRes)
                    showingSadPose = sad
                }
                val bow = when {
                    time < 0.16f -> time / 0.16f
                    time < 0.35f -> 1f - 0.55f * (time - 0.16f) / 0.19f
                    time < 0.87f -> 0.45f
                    else -> 0.45f * (1f - time) / 0.13f
                }
                view.translationY = startY + dip * bow
                view.scaleX = startScaleX * (1f - 0.06f * bow)
                view.scaleY = startScaleY * (1f - 0.18f * bow)
                view.alpha = when {
                    time < 0.16f -> 1f - time / 0.16f
                    time < 0.30f -> (time - 0.16f) / 0.14f
                    time < 0.87f -> (1f - (time - 0.75f).coerceAtLeast(0f) / 0.12f).coerceAtLeast(0f)
                    else -> (time - 0.87f) / 0.13f
                }.coerceIn(0f, 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 途中で止められても、元の絵・位置に戻して薄くする
                    view.setImageResource(hand.poseRes)
                    view.translationY = startY
                    view.scaleX = startScaleX
                    view.scaleY = startScaleY
                    view.alpha = 1f
                    (view.parent as View).alpha = LOSER_ALPHA
                }
            })
        }
        return reaction
    }

    private fun resetBox(box: View, dir: Float) {
        box.animate().cancel()
        (box.parent as View).translationZ = 0f
        box.translationX = 0f
        box.translationY = 0f
        box.alpha = 1f
        box.translationX = 0f
        box.translationY = 0f
        box.scaleX = dir
        box.scaleY = 1f
    }

    private fun sign(view: View) = if (view.scaleX < 0) -1f else 1f

    private fun cancelRunning() {
        txtResult.animate().cancel()
        txtResult.scaleX = 1f
        txtResult.scaleY = 1f
        val list = running.toList()
        running.clear()
        list.forEach { it.cancel() }
    }

    private fun setArenaStroke(@ColorRes col: Int) {
        (arena.background.mutate() as? GradientDrawable)?.setStroke(dp(2f).toInt(), color(col))
    }

    private fun haptic(type: Int) {
        arena.performHapticFeedback(type)
    }

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(this, id)
    private fun dp(v: Float) = v * resources.displayMetrics.density

    // ---------------------------------------------------------------
    // 保存・読み込み
    // ---------------------------------------------------------------

    private fun restoreRound(b: Bundle): Round? {
        if (!b.containsKey(KEY_ROUND_PLAYER)) return null
        val hands = Hand.entries
        return Round(hands[b.getInt(KEY_ROUND_PLAYER)], hands[b.getInt(KEY_ROUND_CPU)], hands[b.getInt(KEY_ROUND_PREDICTED)])
    }

    private fun save() {
        prefs.edit {
            putInt(KEY_WINS, stats.wins)
            putInt(KEY_LOSSES, stats.losses)
            putInt(KEY_DRAWS, stats.draws)
            putInt(KEY_STREAK, stats.streak)
            putInt(KEY_BEST, stats.bestStreak)
            putString(KEY_HANDS, stats.handCounts.joinToString(","))
            putString(KEY_AI, ai.serialize())
            putString(KEY_HITS, recentHits.joinToString("") { if (it) "1" else "0" })
        }
    }

    private fun load() {
        val hands = prefs.getString(KEY_HANDS, null)?.split(",")?.mapNotNull { it.toIntOrNull() }
        stats = Stats(
            wins = prefs.getInt(KEY_WINS, 0),
            losses = prefs.getInt(KEY_LOSSES, 0),
            draws = prefs.getInt(KEY_DRAWS, 0),
            streak = prefs.getInt(KEY_STREAK, 0),
            bestStreak = prefs.getInt(KEY_BEST, 0),
            handCounts = if (hands?.size == 3) hands.toIntArray() else IntArray(3),
        )
        ai.restore(prefs.getString(KEY_AI, null))
        recentHits.clear()
        prefs.getString(KEY_HITS, "")!!.takeLast(RECENT_SIZE).forEach { recentHits.addLast(it == '1') }
    }

    companion object {
        private const val PREFS = "janken"
        private const val KEY_WINS = "wins"
        private const val KEY_LOSSES = "losses"
        private const val KEY_DRAWS = "draws"
        private const val KEY_STREAK = "streak"
        private const val KEY_BEST = "best_streak"
        private const val KEY_HANDS = "hands"
        private const val KEY_AI = "ai_memory"
        private const val KEY_HITS = "recent_hits"
        private const val KEY_ROUND_PLAYER = "round_player"
        private const val KEY_ROUND_CPU = "round_cpu"
        private const val KEY_ROUND_PREDICTED = "round_predicted"
        private const val RECENT_SIZE = 20
        private const val WIN_SCALE = 1.08f
        private const val LOSER_ALPHA = 0.4f
    }
}
