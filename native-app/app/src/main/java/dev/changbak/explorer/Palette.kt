package dev.changbak.explorer

import android.graphics.Color

/**
 * 숯과 라임 — the app's colours, in one place.
 *
 * Chosen from four mockups the family looked at side by side. The point of this one is
 * contrast: a car's centre screen sits beside a lit instrument cluster in daylight, and the
 * palette that survives that is a near-black ground with one bright colour on it. The lime is
 * the only loud thing in the app, so it only ever marks what the child should act on — the
 * button to press, the place being met now, the ring around the thing to find.
 *
 * Two rules keep it readable, and both are tested by eye on the device rather than assumed:
 * nothing bright carries small text (lime on white is a 1.8:1 contrast and would be unreadable
 * at caption size), and nothing that must be told apart differs by hue alone.
 */
object Palette {
    /** Type on a light surface, and the outline on every drawn shape. */
    val ink = Color.rgb(21, 24, 26)

    /** Secondary type. Dark enough for caption size on both light surfaces. */
    val muted = Color.rgb(88, 96, 92)

    /**
     * The colour of a label that belongs to the app rather than to the place.
     *
     * A dark lime, not the bright one: this carries words, and words need 4.5:1.
     */
    val accent = Color.rgb(60, 107, 18)

    /** The one bright colour. Buttons to press, the place being met, the ring to look at. */
    val primary = Color.rgb(168, 224, 95)

    /** Type on [primary]. Dark, because nothing legible sits white on lime. */
    val onPrimary = Color.rgb(22, 32, 10)

    /** The page: the sheet a question is written on. */
    val ground = Color.rgb(246, 247, 244)

    /** A second button, and any panel that must sit quietly inside [ground]. */
    val pale = Color.rgb(228, 232, 225)

    /** The stage behind everything: charcoal, so a photograph is the brightest thing on screen. */
    val road = Color.rgb(21, 24, 26)

    /** The same, a shade up, where a surface has to separate from the stage. */
    val deep = Color.rgb(30, 35, 38)

    /** The cool mark. Used for the one thing that is neither action nor text: the sky. */
    val sky = Color.rgb(89, 194, 232)

    /** The paper a small drawing is printed on. */
    val paper = Color.rgb(239, 245, 230)

    /** A chip over a photograph, and the credit line under it. */
    val onHero = Color.argb(46, 255, 255, 255)
    val onPhoto = Color.argb(226, 255, 255, 255)
}
