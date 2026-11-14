package dev.changbak.explorer.domain

/**
 * The journey's own sentences, in the child's language.
 *
 * These are the words around the places rather than the places themselves: what is said while
 * waiting, what links a place's question to the window, what closes a turn. They matter as much
 * as the content, because they are spoken aloud — a Korean sentence read by an English voice is
 * not a cosmetic problem, it is the car talking gibberish at a six year old.
 */
data class Narration(
    val waiting: String,
    val routeChanged: String,
    val comingUp: (name: String, side: String, observe: String) -> String,
    val notVisible: String,
    val closeBy: (name: String, side: String, ask: String, observe: String) -> String,
    val minutesAhead: (minutes: Int, name: String, ask: String) -> String,
    val thoughtNoted: String,
    val notSeenYet: String,
    val sawIt: (name: String) -> String,
    val arrived: (name: String) -> String,
    val wentPast: (name: String) -> String,
    val pretend: (role: String, prompt: String) -> String,
    val listenedOnly: String,
    val ownIdea: String,
    val skipped: String,
    val finished: String,
    val sideLeft: String,
    val sideRight: String,
    val sideAhead: String,
    /**
     * The app's frame around a guardian's record. The guardian's own words — their note and the
     * way they chose to say when — are never translated: those are theirs, not the app's.
     */
    val familyRecall: (whenLabel: String) -> String,
    /**
     * How the app hands over to a story the guardian wrote.
     *
     * When there is one, it is the whole of what this place has to say and the app's own frame
     * must not talk over it. "우리 가족이 함께 왔던 곳이야" is wrong for half of what a parent
     * would write — the child was not at the place their parents first met — so the app says
     * only that somebody left a story here, and then says theirs.
     */
    val familyIntro: String,
    val familyReassure: String,
    /** How a guardian may choose to say when, offered in the language they are setting up in. */
    val whenChoices: List<String>,
) {
    /** What to add after a place's name, or nothing when the fixes cannot name a window. */
    fun side(which: WindowSide): String = when (which) {
        WindowSide.LEFT -> " $sideLeft"
        WindowSide.RIGHT -> " $sideRight"
        WindowSide.AHEAD -> " $sideAhead"
        WindowSide.UNKNOWN -> ""
    }

    companion object {
        fun of(language: ChildLanguage): Narration = when (language) {
            ChildLanguage.KOREAN -> korean
            ChildLanguage.ENGLISH -> english
        }

        private val korean = Narration(
            waiting = "가는 길에서 만날 이야기를 기다리고 있어.",
            routeChanged = "길이 바뀌었어. 새 길에서 만날 곳을 다시 살펴볼게.",
            comingUp = { name, side, observe -> "곧 $name 근처야.$side $observe 안 보이면 그냥 지나가도 좋아." },
            notVisible = "지금은 보기 어려운 구간이야. 지나간 뒤 이야기로 만나자.",
            closeBy = { name, side, ask, observe -> "$name 근처야.$side $ask $observe 안 보이면 듣기만 해도 좋아." },
            minutesAhead = { minutes, name, ask -> "약 ${minutes}분 뒤에 $name 근처를 지나갈 거야. $ask" },
            thoughtNoted = "그렇게 생각했구나. 가까이 가면 직접 살펴보자.",
            notSeenYet = "괜찮아. 지나가면 이야기로 들려줄게.",
            sawIt = { name -> "네가 본 $name 이야기야. " },
            arrived = { name -> "$name 에 도착했어. 보지 못했어도 괜찮아. " },
            wentPast = { name -> "$name 근처를 지났어. 보지 못했어도 괜찮아. " },
            pretend = { role, prompt -> "이제 우리가 상상해 보자. 너는 ${role}야! $prompt" },
            listenedOnly = "이번에는 이야기로 만났어. 편하게 다음 풍경을 기다려도 좋아.",
            ownIdea = "네 생각으로 장면을 완성했어. 이제 편하게 다음 풍경을 만나러 가자.",
            skipped = "좋아. 편하게 밖을 보고 있어도 돼.",
            finished = "오늘 길에서 만난 이야기를 돌아볼까?",
            sideLeft = "왼쪽 창밖을 봐.",
            sideRight = "오른쪽 창밖을 봐.",
            sideAhead = "앞쪽을 봐.",
            familyRecall = { whenLabel -> "그런데 있잖아. 여기는 $whenLabel 우리 가족이 함께 왔던 곳이야." },
            familyIntro = "그런데 있잖아. 여기에는 엄마 아빠가 너한테 들려주고 싶은 이야기가 있어.",
            familyReassure = "기억나지 않아도 괜찮아. 오늘 다시 지나가는 것만으로도 좋아.",
            whenChoices = listOf("네가 지금보다 더 어렸을 때", "네가 아기였을 때", "작년에", "얼마 전에"),
        )

        private val english = Narration(
            waiting = "I'm waiting for a story to come up along the way.",
            routeChanged = "The road changed. I'll look again for what we will meet on this one.",
            comingUp = { name, side, observe -> "$name is coming up.$side $observe If you can't see it, that's fine." },
            notVisible = "This bit is hard to see. Let's meet it as a story once we're past.",
            closeBy = { name, side, ask, observe -> "We're near $name.$side $ask $observe You can just listen if you can't see it." },
            minutesAhead = { minutes, name, ask -> "In about $minutes minutes we go past $name. $ask" },
            thoughtNoted = "So that's what you think. Let's look properly when we get closer.",
            notSeenYet = "That's okay. I'll tell you the story once we're past.",
            sawIt = { name -> "This is the story of the $name you just saw. " },
            arrived = { name -> "We're at $name. It's fine if you didn't see it. " },
            wentPast = { name -> "We just went past $name. It's fine if you didn't see it. " },
            pretend = { role, prompt -> "Now let's pretend. You are the $role! $prompt" },
            listenedOnly = "We met this one as a story today. Just wait for the next view if you like.",
            ownIdea = "Your idea finished the scene. Now let's go and meet the next view.",
            skipped = "Okay. You can just watch outside.",
            finished = "Shall we look back at the stories we met today?",
            sideLeft = "Look out of the left window.",
            sideRight = "Look out of the right window.",
            sideAhead = "Look straight ahead.",
            familyRecall = { whenLabel -> "Here's a thing. $whenLabel, our family came here together." },
            familyIntro = "Here's a thing. Someone at home left a story about this place for you.",
            familyReassure = "It's fine if you don't remember. Going past it again today is lovely on its own.",
            whenChoices = listOf("When you were littler than now", "When you were a baby", "Last year", "Not long ago"),
        )
    }
}
