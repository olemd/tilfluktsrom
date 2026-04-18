package no.naiv.tilfluktsrom.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for [DirectionArrowView.sectorIndex] and
 * [DirectionArrowView.snapToSector].
 *
 * These two functions are what the reduce-motion (WCAG 2.3.3) remediation
 * relies on: when animations are disabled, the arrow snaps to one of eight
 * 45° sectors instead of rotating continuously. The sector index also drives
 * the TalkBack accessibility description (WCAG 4.1.2), so any off-by-one in
 * this arithmetic is a silent a11y regression. Worth locking down with tests
 * that can run in milliseconds without booting an emulator.
 */
class DirectionArrowViewTest {

    @Test
    fun sectorIndex_wholeCardinals_returnExpectedSector() {
        assertEquals(0, DirectionArrowView.sectorIndex(0f))    // forward
        assertEquals(1, DirectionArrowView.sectorIndex(45f))   // forward-right
        assertEquals(2, DirectionArrowView.sectorIndex(90f))   // right
        assertEquals(3, DirectionArrowView.sectorIndex(135f))  // back-right
        assertEquals(4, DirectionArrowView.sectorIndex(180f))  // back
        assertEquals(5, DirectionArrowView.sectorIndex(225f))  // back-left
        assertEquals(6, DirectionArrowView.sectorIndex(270f))  // left
        assertEquals(7, DirectionArrowView.sectorIndex(315f))  // forward-left
    }

    @Test
    fun sectorIndex_boundariesSnapToNextSector() {
        // A sector covers [center - 22.5°, center + 22.5°). The boundary
        // value itself should belong to the following sector.
        assertEquals(0, DirectionArrowView.sectorIndex(22.4f))
        assertEquals(1, DirectionArrowView.sectorIndex(22.5f))
        assertEquals(1, DirectionArrowView.sectorIndex(44.9f))
        assertEquals(1, DirectionArrowView.sectorIndex(67.4f))
        assertEquals(2, DirectionArrowView.sectorIndex(67.5f))
    }

    @Test
    fun sectorIndex_wrapsAroundAt360() {
        // Anything within 22.5° of north should land back in sector 0.
        assertEquals(7, DirectionArrowView.sectorIndex(337.4f))
        assertEquals(0, DirectionArrowView.sectorIndex(337.5f))
        assertEquals(0, DirectionArrowView.sectorIndex(359.99f))
        assertEquals(0, DirectionArrowView.sectorIndex(360f))
        assertEquals(0, DirectionArrowView.sectorIndex(720f))
        assertEquals(2, DirectionArrowView.sectorIndex(450f))  // 450 % 360 = 90
    }

    @Test
    fun sectorIndex_handlesNegativeAngles() {
        // Sensors can produce angles slightly below zero due to smoothing;
        // subtracting deviceHeading from shelterBearing routinely yields
        // negative values. The function must normalise rather than throw.
        assertEquals(7, DirectionArrowView.sectorIndex(-45f))
        assertEquals(4, DirectionArrowView.sectorIndex(-180f))
        assertEquals(0, DirectionArrowView.sectorIndex(-10f))
        assertEquals(6, DirectionArrowView.sectorIndex(-90f))
        assertEquals(0, DirectionArrowView.sectorIndex(-360f))
    }

    @Test
    fun snapToSector_alwaysReturnsMultipleOf45InZeroTo315() {
        // Sweep a dense range of inputs; every output must be a whole 45°
        // step in [0, 315]. This is the invariant that prevents vestibular
        // motion triggers when reduce-motion is enabled.
        var angle = -720f
        while (angle <= 720f) {
            val snapped = DirectionArrowView.snapToSector(angle)
            val remainder = snapped % 45f
            assertEquals(
                "snap($angle) = $snapped is not a multiple of 45°",
                0f, remainder, 0.0001f
            )
            assertEquals(
                "snap($angle) = $snapped is outside [0, 315]",
                true, snapped in 0f..315f
            )
            angle += 0.37f  // irrational-ish step hits boundaries from both sides
        }
    }

    @Test
    fun snapToSector_knownPoints() {
        assertEquals(0f, DirectionArrowView.snapToSector(0f), 0.0001f)
        assertEquals(0f, DirectionArrowView.snapToSector(22.4f), 0.0001f)
        assertEquals(45f, DirectionArrowView.snapToSector(22.5f), 0.0001f)
        assertEquals(90f, DirectionArrowView.snapToSector(90f), 0.0001f)
        assertEquals(180f, DirectionArrowView.snapToSector(180f), 0.0001f)
        // -45° normalises to 315° (sector 7), which snaps to 315°.
        assertEquals(315f, DirectionArrowView.snapToSector(-45f), 0.0001f)
        // Just past full rotation wraps to forward.
        assertEquals(0f, DirectionArrowView.snapToSector(359.9f), 0.0001f)
    }

    @Test
    fun clampIndicatorRadius_returnsPreferredWhenViewportIsBig() {
        // Viewport generous enough to fit the preferred radius + label
        // at any angle.
        val r = DirectionArrowView.clampIndicatorRadius(
            cx = 500f, cy = 500f, viewWidth = 1000, viewHeight = 1000,
            angleDegrees = 45f,
            preferredRadius = 100f, labelReach = 20f, minRadius = 30f
        )
        assertEquals(100f, r, 0.01f)
    }

    @Test
    fun clampIndicatorRadius_clampsWhenNorthPointsToShortAxis() {
        // Portrait-ish compass viewport (1080 × 2400), facing east.
        // Horizontal half-width = 540, label reach = 100 → max radius 440.
        val r = DirectionArrowView.clampIndicatorRadius(
            cx = 540f, cy = 1200f, viewWidth = 1080, viewHeight = 2400,
            angleDegrees = 90f,
            preferredRadius = 600f, labelReach = 100f, minRadius = 30f
        )
        assertEquals(440f, r, 0.01f)
    }

    @Test
    fun clampIndicatorRadius_usesPreferredWhenVerticalAxisIsLonger() {
        // Same portrait viewport, facing north. Vertical half-height is
        // 1200, so preferred radius 600 fits comfortably.
        val r = DirectionArrowView.clampIndicatorRadius(
            cx = 540f, cy = 1200f, viewWidth = 1080, viewHeight = 2400,
            angleDegrees = 0f,
            preferredRadius = 600f, labelReach = 100f, minRadius = 30f
        )
        assertEquals(600f, r, 0.01f)
    }

    @Test
    fun clampIndicatorRadius_diagonalGetsMoreRoomThanAxial() {
        // On a square viewport, the distance to the corner (√2 × half)
        // exceeds the axial distance. Used as a regression check that we
        // don't accidentally clamp by min(width, height) regardless of
        // angle.
        val axial = DirectionArrowView.clampIndicatorRadius(
            cx = 500f, cy = 500f, viewWidth = 1000, viewHeight = 1000,
            angleDegrees = 90f,
            preferredRadius = 10_000f, labelReach = 0f, minRadius = 0f
        )
        val diagonal = DirectionArrowView.clampIndicatorRadius(
            cx = 500f, cy = 500f, viewWidth = 1000, viewHeight = 1000,
            angleDegrees = 45f,
            preferredRadius = 10_000f, labelReach = 0f, minRadius = 0f
        )
        assertEquals(500f, axial, 0.01f)
        assertEquals(707.11f, diagonal, 0.1f)
    }

    @Test
    fun clampIndicatorRadius_enforcesMinRadiusWhenLabelCantFit() {
        // Viewport far too small to fit the requested label. Clamp to the
        // minimum instead of returning a negative radius.
        val r = DirectionArrowView.clampIndicatorRadius(
            cx = 50f, cy = 50f, viewWidth = 100, viewHeight = 100,
            angleDegrees = 0f,
            preferredRadius = 40f, labelReach = 200f, minRadius = 10f
        )
        assertEquals(10f, r, 0.01f)
    }

    @Test
    fun clampIndicatorRadius_stayInsideViewport_sweep() {
        // For every 5° step, the rendered indicator (at `radius + labelReach`
        // from the centre) must remain inside the viewport rectangle. This
        // is the invariant the user reported being violated.
        val cx = 540f
        val cy = 711f
        val w = 1080
        val h = 1422
        val labelReach = 170f
        var angle = 0f
        while (angle < 360f) {
            val radius = DirectionArrowView.clampIndicatorRadius(
                cx, cy, w, h, angle,
                preferredRadius = 583f, labelReach = labelReach, minRadius = 30f
            )
            val outermost = radius + labelReach
            val thetaRad = Math.toRadians(angle.toDouble())
            val px = cx + (outermost * Math.sin(thetaRad)).toFloat()
            val py = cy - (outermost * Math.cos(thetaRad)).toFloat()
            // Allow ~1 px of slack so float precision at the exact edge
            // (e.g. 540 * sin(240°) rounding to -5.4e-5 instead of 0) is
            // not treated as a clipping regression.
            val slack = 1f
            val inBounds = px in -slack..(w + slack) && py in -slack..(h + slack)
            assertEquals(
                "indicator tip at angle $angle° landed at ($px, $py), outside [0,$w]×[0,$h]",
                true, inBounds
            )
            angle += 5f
        }
    }
}
