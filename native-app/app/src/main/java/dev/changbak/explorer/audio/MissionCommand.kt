package dev.changbak.explorer.audio

import java.text.Normalizer

enum class MissionCommand {
    FOUND, SKIP, DIFFERENT, CHANGED, SAME, HINT, REPEAT, PAUSE, UNDO
}

private val commandPhrases = listOf(
    MissionCommand.SKIP to setOf(
        "못찾았어", "못찾았어요", "못찾겠어", "못찾겠어요", "못봤어", "못봤어요",
        "안보여", "안보여요", "넘어가", "넘어가자", "넘겨줘", "넘겨주세요", "다음으로"
    ),
    MissionCommand.PAUSE to setOf(
        "쉬자", "쉬어요", "쉴래", "잠깐멈춰", "잠깐멈춰줘", "잠깐만", "잠깐쉬자",
        "멈춰", "멈춰줘", "그만", "그만할래"
    ),
    MissionCommand.FOUND to setOf("찾았어", "찾았어요", "찾았다", "봤어", "봤어요", "발견했어", "발견했어요", "살펴봤어", "살펴봤어요"),
    MissionCommand.SAME to setOf("그대로야", "그대로예요", "비슷해", "비슷해요", "똑같아", "똑같아요", "아직그대로야"),
    MissionCommand.DIFFERENT to setOf(
        "달라졌어", "달라졌어요", "바뀌었어", "바뀌었어요", "풍경달라졌어", "풍경달라졌어요",
        "풍경이달라졌어", "풍경이달라졌어요", "풍경바뀌었어", "풍경바뀌었어요",
        "풍경이바뀌었어", "풍경이바뀌었어요"
    ),
    MissionCommand.CHANGED to setOf("지나갔어", "지나갔어요"),
    MissionCommand.HINT to setOf("힌트", "힌트줘", "힌트주세요", "힌트알려줘"),
    MissionCommand.REPEAT to setOf("다시들려줘", "다시들려주세요", "다시말해줘", "다시말해주세요", "한번더", "한번더들려줘"),
    MissionCommand.UNDO to setOf("잘못눌렀어", "잘못눌렀어요", "잘못말했어", "잘못말했어요", "방금취소", "방금거취소")
)

/** Complete short phrases only: never infer success from a substring of a negative answer. */
fun parseMissionCommand(text: String): MissionCommand? {
    if (text.length > 80) return null
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replace(Regex("[\\s\\p{Z}\\p{P}]"), "")
    if (normalized.isEmpty()) return null
    return commandPhrases.firstOrNull { normalized in it.second }?.first
}
