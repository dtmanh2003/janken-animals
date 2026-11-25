package com.example.kadai7

import android.animation.Animator
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.TimeInterpolator
import android.util.Property
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator

/**
 * 動物の変身アニメーション
 *
 * animal-animation-updated.html（確認済みのHTML版）の @keyframes をそのまま移植したもの。
 * 数値はCSSのpxで書いてあり、実行時に画面のpxへ変換する。
 *
 * HTMLでは各コマ（元画像 1254px 幅）を幅 234px の箱に表示している。
 * アプリでは同じコマを中心そろえで切り抜いた画像（[cropW] × [cropH]）を fitCenter で表示するので、
 * 「CSSの1px = 元画像の 1254/234 px」から画面のpxを求める（[cssPx]）。
 */
object AnimalMotion {

    /** 変形（CSSの translate → rotate → scale と同じ順番。Androidのビューも同じ順に適用される） */
    data class Tr(
        val tx: Float = 0f,
        val ty: Float = 0f,
        val rot: Float = 0f,
        val sx: Float = 1f,
        val sy: Float = sx,
    )

    /**
     * キーフレーム。tr / alpha が null のときは、その時点ではそのプロパティを指定しない（CSSと同じ）。
     * [ease] はこのキーフレームから次のキーフレームまでの区間に使う（CSSの animation-timing-function と同じ）。
     */
    class Frame(val at: Float, val tr: Tr? = null, val alpha: Float? = null, val ease: TimeInterpolator? = null)

    class Clip(val durationMs: Long, val delayMs: Long, val ease: TimeInterpolator, val frames: List<Frame>) {
        val endMs get() = delayMs + durationMs
    }

    /**
     * @param idleRest ふだんの姿を止めて表示するときの変形（HTMLの .fox .start などの指定）
     * @param poseBox 変身後の箱の位置・大きさのずれ（ワシだけHTMLで箱を上に寄せて小さくしている）
     */
    class Motion(
        val cropW: Int,
        val cropH: Int,
        val idleRest: Tr,
        val poseBox: Tr,
        val idle: Clip,
        val pose: Clip,
    ) {
        val totalMs get() = maxOf(idle.endMs, pose.endMs)
    }

    // CSSのイージング
    private val EASE = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    private val EASE_IN = PathInterpolator(0.42f, 0f, 1f, 1f)
    private val EASE_IN_OUT = PathInterpolator(0.42f, 0f, 0.58f, 1f)
    private val LINEAR = LinearInterpolator()

    private const val FRAME_W = 1254f   // 元画像のコマの幅
    private const val BOX_W = 234f      // HTMLでの箱の幅（ボタン285px × 82%）

    fun of(hand: Hand): Motion = when (hand) {
        Hand.SCISSORS -> FOX
        Hand.ROCK -> TURTLE
        Hand.PAPER -> EAGLE
    }

    /** キツネ：とことこ走ってジャンプ（run-jump）→ 着地して耳がチョキ（land） */
    private val FOX = Motion(
        cropW = 618, cropH = 603,
        // HTML版は -39px から走り出す。アプリの枠はHTMLより幅が狭いので、走る距離を約6割にしている
        idleRest = Tr(-24f, 0f, 0f, .7f),
        poseBox = Tr(),
        idle = Clip(1040, 0, LINEAR, listOf(
            Frame(0f, Tr(-24f, 0f, 0f, .7f), 1f),
            Frame(.09f, Tr(-24f, 3f, 0f, .73f, .66f)),
            Frame(.17f, Tr(-18f, -4f, -2f, .7f)),
            Frame(.25f, Tr(-13f, 0f, 0f, .7f)),
            Frame(.33f, Tr(-8f, -4f, -2f, .7f)),
            Frame(.41f, Tr(-3f, 0f, 0f, .7f)),
            Frame(.49f, Tr(0f, 4f, 0f, .74f, .64f)),
            // HTML版は -28px。アプリでは「軽くぴょん」にするため低めにしている
            Frame(.64f, Tr(7f, -16f, -3f, .7f, .73f), ease = EASE_IN),
            Frame(.99f, Tr(0f, 3f, 0f, .76f, .54f), 1f),
            Frame(1f, Tr(0f, 3f, 0f, .76f, .54f), 0f),   // visibility:hidden
        )),
        pose = Clip(350, 1040, EASE, listOf(
            Frame(0f, Tr(0f, 3f, 0f, .76f, .54f), 0f),   // visibility:hidden
            Frame(.01f, Tr(0f, 3f, 0f, .76f, .54f), 1f),
            Frame(.49f, Tr(0f, -6f, 0f, 1.02f, 1.08f)),
            Frame(.76f, Tr(0f, 1f, 0f, 1.02f, .97f)),
            Frame(1f, Tr()),
        )),
    )

    /** カメ：甲羅にこもる（tuck）→ ころんと1回転（roll） */
    private val TURTLE = Motion(
        cropW = 746, cropH = 537,
        // キツネと同じく、横に動く距離はHTML版の約6割
        idleRest = Tr(),
        poseBox = Tr(),
        idle = Clip(340, 0, EASE_IN_OUT, listOf(
            Frame(0f, Tr()),
            Frame(.6f, Tr(-14f, 4f, 0f, .82f, .78f), 1f),
            Frame(1f, Tr(-22f, 8f, 0f, .73f, .66f), 0f),
        )),
        pose = Clip(680, 280, PathInterpolator(.22f, .62f, .32f, 1f), listOf(
            Frame(0f, Tr(-21f, 7f, 0f, .72f), 0f),
            Frame(.12f, alpha = 1f),
            Frame(.76f, Tr(3f, 1f, 350f, .97f)),
            Frame(.9f, Tr(1f, 1f, 365f, 1f, .98f)),
            Frame(1f, Tr(0f, 0f, 360f)),
        )),
    )

    /** ワシ：飛び立つ（takeoff）→ 翼を広げて舞う（soar） */
    private val EAGLE = Motion(
        cropW = 1152, cropH = 605,
        idleRest = Tr(0f, -4f, 0f, .76f),
        // .eagle.picked .result{top:70px;width:70%} → 箱が 234px → 199.5px、中心が 22.6px 上
        poseBox = Tr(0f, -22.625f, 0f, 199.5f / BOX_W),
        idle = Clip(360, 0, EASE_IN_OUT, listOf(
            Frame(0f, Tr(0f, -4f, 0f, .76f)),
            Frame(.45f, Tr(-5f, 3f, -6f, .8f, .67f), 1f),
            Frame(1f, Tr(4f, -24f, 7f, .79f, .87f), 0f),
        )),
        // 翼を「少しだけ開いた」状態（横に細い）から入って、それから大きく広げる。
        // 飛び立つ姿と少し重ねて切り替えるので、閉じた翼 → 開いた翼の入れ替わりが目立たない
        pose = Clip(980, 260, EASE, listOf(
            // 縦横の比率をほぼそろえて、引き伸ばしたように見えないようにする
            Frame(0f, Tr(3f, -8f, 6f, .5f, .56f), 0f),
            Frame(.14f, Tr(5f, -11f, 6f, .66f, .72f), 1f),
            Frame(.29f, Tr(7f, -13f, 5f, .94f, 1.05f)),
            Frame(.47f, Tr(13f, -17f, 3f, .86f, .72f)),
            Frame(.7f, Tr(4f, -14f, -4f, 1f, 1.06f)),
            Frame(.87f, Tr(1f, -12f, -2f, .98f, .95f)),
            Frame(1f, Tr()),
        )),
    )

    /** CSSの1pxが、このビューで何pxになるか */
    fun cssPx(view: View, m: Motion): Float {
        val w = view.width - view.paddingLeft - view.paddingRight
        val h = view.height - view.paddingTop - view.paddingBottom
        val fit = minOf(w / m.cropW.toFloat(), h / m.cropH.toFloat())
        return fit * FRAME_W / BOX_W
    }

    /** 箱のずれ（poseBox）を重ねる。translate はpxのまま足し、scale は掛ける */
    private fun inBox(t: Tr, box: Tr) =
        Tr(box.tx + t.tx, box.ty + t.ty, t.rot, box.sx * t.sx, box.sy * t.sy)

    fun apply(view: View, t: Tr, k: Float, box: Tr = Tr(), alpha: Float = view.alpha) {
        val b = inBox(t, box)
        view.translationX = b.tx * k
        view.translationY = b.ty * k
        view.rotation = b.rot
        view.scaleX = b.sx
        view.scaleY = b.sy
        view.alpha = alpha
    }

    /** アニメーション開始前の状態（CSSの animation-fill-mode: both の「前」側） */
    fun applyStart(view: View, clip: Clip, k: Float, box: Tr = Tr()) {
        val t = clip.frames.first { it.tr != null }.tr!!
        val a = clip.frames.firstOrNull { it.alpha != null }?.let { if (it.at == 0f) it.alpha else 1f } ?: 1f
        apply(view, t, k, box, a)
    }

    /** キーフレームから ObjectAnimator を作る */
    fun animator(view: View, clip: Clip, k: Float, box: Tr = Tr()): Animator {
        val tf = clip.frames.filter { it.tr != null }
        // 不透明度：0% / 100% の指定がなければ元の値（1）を補う（CSSの暗黙のキーフレーム）
        val af = clip.frames.filter { it.alpha != null }.toMutableList()
        if (af.isNotEmpty()) {
            if (af.first().at > 0f) af.add(0, Frame(0f, alpha = 1f))
            if (af.last().at < 1f) af.add(Frame(1f, alpha = 1f))
        }

        fun holder(prop: Property<View, Float>, frames: List<Frame>, value: (Frame) -> Float) =
            PropertyValuesHolder.ofKeyframe(prop, *frames.mapIndexed { i, f ->
                Keyframe.ofFloat(f.at, value(f)).also {
                    if (i > 0) it.interpolator = frames[i - 1].ease ?: clip.ease
                }
            }.toTypedArray())

        val holders = mutableListOf(
            holder(View.TRANSLATION_X, tf) { inBox(it.tr!!, box).tx * k },
            holder(View.TRANSLATION_Y, tf) { inBox(it.tr!!, box).ty * k },
            holder(View.ROTATION, tf) { it.tr!!.rot },
            holder(View.SCALE_X, tf) { inBox(it.tr!!, box).sx },
            holder(View.SCALE_Y, tf) { inBox(it.tr!!, box).sy },
        )
        if (af.isNotEmpty()) holders += holder(View.ALPHA, af) { it.alpha!! }

        return ObjectAnimator.ofPropertyValuesHolder(view, *holders.toTypedArray()).apply {
            duration = clip.durationMs
            startDelay = clip.delayMs
            interpolator = LINEAR   // 区間ごとのイージングはキーフレーム側で指定
        }
    }
}
