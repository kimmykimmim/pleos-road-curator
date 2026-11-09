package dev.changbak.explorer.rendering

import dev.changbak.explorer.domain.*
import org.junit.Assert.*
import org.junit.Test

class CloudSceneMotionTest {
    private val rabbit = CloudFriend(CloudForm.RABBIT, "토끼")
    private val whale = CloudFriend(CloudForm.WHALE, "고래")
    private fun one() = CloudWorld(listOf(SkyCloud(1, rabbit, CloudRole.FRIEND)), 1, 1)

    @Test fun newlyFoundFriendFadesInRatherThanAppearingAtFullOpacity() {
        val motion = CloudSceneMotion(CloudWorld())
        motion.retarget(one(), 100)
        assertTrue(motion.frame(100).isEmpty())
        val middle = motion.frame(510).single()
        assertEquals(0.5f, middle.opacity, 0.001f)
        assertEquals(0.5f, middle.face, 0.001f)
        assertEquals(1f, motion.frame(920).single().opacity, 0f)
    }
    @Test fun previewMovesIntoItsNewRoleAndGrowsAFaceWithoutAnExtraCloud() {
        val preview = one().copy(draft = whale)
        val destination = preview.copy(clouds = preview.clouds + SkyCloud(2, whale, CloudRole.FRIEND), activeId = 2, draft = null)
        val motion = CloudSceneMotion(preview)
        motion.retarget(destination, 0)
        val frames = motion.frame(410)
        assertEquals(2, frames.size)
        val created = frames.single { it.cloudId == 2 }
        assertEquals(1f, created.opacity, 0f)
        assertEquals(0.5f, created.face, 0.001f)
        assertTrue(created.scale > 0.35f && created.scale < 0.82f)
        assertTrue(created.y > -0.18f && created.y < 1.18f)
    }
    @Test fun backgroundRemainsFacelessThroughoutItsTransition() {
        val preview = one().copy(draft = whale)
        val destination = preview.copy(clouds = preview.clouds + SkyCloud(2, whale, CloudRole.BACKGROUND), draft = null)
        val motion = CloudSceneMotion(preview)
        motion.retarget(destination, 0)
        for (time in listOf(0L, 200L, 410L, 820L)) assertEquals(0f, motion.frame(time).single { it.cloudId == 2 }.face, 0f)
    }
    @Test fun selectingAnotherFriendInterpolatesBothPositions() {
        val initial = one().copy(clouds = one().clouds + SkyCloud(2, whale, CloudRole.FRIEND))
        val motion = CloudSceneMotion(initial)
        val before = motion.frame(0).single { it.cloudId == 1 }
        motion.retarget(initial.copy(activeId = 2), 0)
        val middle = motion.frame(410).single { it.cloudId == 1 }
        val end = motion.frame(820).single { it.cloudId == 1 }
        assertTrue(middle.x != before.x && middle.x != end.x)
        assertEquals((before.scale + end.scale) / 2f, middle.scale, 0.001f)
    }
    @Test fun interruptionStartsAtTheCurrentVisibleFrame() {
        val initial = one().copy(clouds = one().clouds + SkyCloud(2, whale, CloudRole.FRIEND))
        val motion = CloudSceneMotion(initial)
        motion.retarget(initial.copy(activeId = 2), 0)
        val visible = motion.frame(300)
        motion.retarget(initial, 300)
        assertEquals(visible, motion.frame(300))
        assertEquals(initial.activeId, motion.frame(1120).single { it.active == 1f }.cloudId)
    }
    @Test fun exchangingSpeakersTakeSeparatePathsAtTheCrossing() {
        val initial = one().copy(clouds = one().clouds + SkyCloud(2, whale, CloudRole.FRIEND))
        val motion = CloudSceneMotion(initial)
        motion.retarget(initial.copy(activeId = 2), 0)
        val middle = motion.frame(410)
        assertTrue(kotlin.math.abs(middle[0].x - middle[1].x) > 1.4f)
    }
    @Test fun correctedShapeCrossfadesOldAndNewWithoutDroppingToEmpty() {
        val motion = CloudSceneMotion(one())
        motion.retarget(one().copy(clouds = listOf(SkyCloud(1, whale, CloudRole.FRIEND))), 0)
        val middle = motion.frame(410)
        assertEquals(setOf(CloudForm.RABBIT, CloudForm.WHALE), middle.map { it.figure.form }.toSet())
        assertTrue(middle.all { it.opacity in 0.49f..0.51f })
        assertEquals(CloudForm.WHALE, motion.frame(820).single().figure.form)
    }
    @Test fun undoOrNewTripFadesCloudsOut() {
        val motion = CloudSceneMotion(one())
        motion.retarget(CloudWorld(), 0)
        assertEquals(0.5f, motion.frame(410).single().opacity, 0.001f)
        assertTrue(motion.frame(820).isEmpty())
    }
    @Test fun repeatedNonVisualUpdatesDoNotRestartAnAnimation() {
        val motion = CloudSceneMotion(CloudWorld())
        motion.retarget(one(), 0)
        motion.retarget(one(), 700)
        assertEquals(1f, motion.frame(820).single().opacity, 0f)
        assertFalse(motion.isMoving(820))
    }
    @Test fun reducedMotionAppliesTheFinalStateImmediately() {
        val motion = CloudSceneMotion(CloudWorld())
        motion.retarget(one(), 0, animate = false)
        assertEquals(1f, motion.frame(0).single().opacity, 0f)
        assertFalse(motion.isMoving(0))
    }
}
