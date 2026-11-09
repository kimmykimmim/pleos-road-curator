package dev.changbak.explorer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MissionCommandTest {
    @Test fun negativeAnswersNeverBecomeFound() {
        listOf("못 찾았어", "못 봤어요!", "안 보여", "못 찾겠어요").forEach {
            assertEquals(it, MissionCommand.SKIP, parseMissionCommand(it))
        }
        listOf("찾았어", "봤어요!", " 발견했어. ", "살펴봤어", "살펴봤어요").forEach {
            assertEquals(it, MissionCommand.FOUND, parseMissionCommand(it))
        }
        listOf("찾았어라고 말하지 않았어", "못 찾았어 찾았어", "아직 찾았어가 아니야").forEach {
            assertNull(it, parseMissionCommand(it))
        }
    }

    @Test fun observedDifferenceIsNotSceneryThatPassed() {
        listOf("달라졌어", "달라졌어요", "바뀌었어", "바뀌었어요", "풍경이 달라졌어", "풍경 바뀌었어요").forEach {
            assertEquals(it, MissionCommand.DIFFERENT, parseMissionCommand(it))
        }
        listOf("지나갔어", "지나갔어요").forEach {
            assertEquals(it, MissionCommand.CHANGED, parseMissionCommand(it))
        }
        listOf("안 달라졌어", "아직 안 바뀌었어", "달라지지 않았어").forEach {
            assertNull(it, parseMissionCommand(it))
        }
    }

    @Test fun undoRequiresAnExplicitCorrection() {
        listOf("잘못 눌렀어", "잘못눌렀어요", "잘못 말했어", "잘못말했어요", "방금 취소", "방금 거 취소").forEach {
            assertEquals(it, MissionCommand.UNDO, parseMissionCommand(it))
        }
        listOf("아니야", "이전", "뒤", "취소", "잘못", "방금 거 취소하지 마").forEach {
            assertNull(it, parseMissionCommand(it))
        }
    }

    @Test fun remainingCommandsKeepTheirMeaning() {
        mapOf(
            "그대로야" to MissionCommand.SAME,
            "비슷해" to MissionCommand.SAME,
            "힌트" to MissionCommand.HINT,
            "다시 들려줘" to MissionCommand.REPEAT,
            "쉬자" to MissionCommand.PAUSE,
            "잠깐 멈춰" to MissionCommand.PAUSE
        ).forEach { (phrase, expected) -> assertEquals(expected, parseMissionCommand(phrase)) }
    }

    @Test fun onlySpacingAndPunctuationAreIgnored() {
        assertEquals(MissionCommand.FOUND, parseMissionCommand("  “찾았어!”  "))
        assertEquals(MissionCommand.FOUND, parseMissionCommand("찾았어\u00A0"))
        listOf("", "   ", "!", "어제 찾았어", "찾았어 😀", "찾았어".repeat(30)).forEach {
            assertNull(it, parseMissionCommand(it))
        }
    }
}
