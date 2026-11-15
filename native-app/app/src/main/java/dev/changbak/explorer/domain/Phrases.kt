package dev.changbak.explorer.domain

/**
 * Everything the child reads on screen, in their language.
 *
 * The places have their own packs; this is the chrome around them — step headings, buttons,
 * the line that says the car is listening. A child answering in English must not be handed a
 * Korean button, and a Korean question inside an English frame reads as a bug to the family
 * even when every fact in it is right.
 *
 * The guardian's own screens are deliberately not here. They are a Korean-market admin surface
 * and translating them is listed as remaining work rather than half done.
 */
data class Phrases(
    val stepPredictImagine: String,
    val stepPredictDeeper: String,
    val stepPredictQuiz: String,
    val stepPredictHunt: String,
    val stepWatch: String,
    val stepExplain: String,
    val stepFamily: String,
    val stepPlayPrefix: String,
    val stepMemory: String,
    val stepFinished: String,
    val stepWaiting: String,

    val listening: String,
    val waitingForYou: String,
    val speakFreely: String,
    val watchListening: String,
    val watchWaiting: String,
    val playListening: String,
    val playWaiting: String,

    val youSaid: String,
    val askAgain: String,
    val useTheScreen: String,
    val sawIt: String,
    val didNotSee: String,
    val myTurn: String,
    val readTheStory: String,
    val rememberIt: String,
    val notSure: String,
    /**
     * The two answers to a story somebody at home wrote down.
     *
     * "기억나!" and "잘 모르겠어" are the right pair for a place the app noticed the family
     * visiting. They are the wrong pair for a parent's own story, which the child was never
     * at: there is nothing there to remember or to fail to remember. So that story is answered
     * by taking it in, or by going off to picture it.
     */
    val heardIt: String,
    val imagineIt: String,
    /**
     * What marks the step where the facts stop and the child's own story starts.
     *
     * It read "여기부터는 우리의 상상 놀이", which announces a game rather than starting one.
     * This step is the child's to make up, so the line hands it to them.
     */
    val pretendPlay: String,
    /**
     * Everything the microphone says for itself.
     *
     * The title of this dialog came from the phrase pack and the rest of it did not, so an
     * English child pressed "I think… (say it out loud)" and was answered in Korean. What the
     * app does with a child's voice is exactly the thing it must not say in a language they
     * cannot read.
     */
    val micAsk: String,
    val micLater: String,
    val micGo: String,
    val micOpen: String,
    val micClosed: String,
    /** The other two lines the child's own status row can carry. */
    val needsLocation: String,
    val preparingVoice: String,
    val naviBefore: String,
    /**
     * The way out of the two answers on screen, and into the child's own.
     *
     * It read "내 말로 이야기하기" — a label for a feature. Said as the child would start the
     * sentence, it is an invitation instead, and the bracket says plainly what pressing it does.
     */
    val sayItMyself: String,
    val inMyOwnWords: String,
    val keepWatching: String,
    val listenAgain: String,
    val skip: String,
    val guardianNote: String,
    val noNeedToSpeak: String,
    /** Which visit this is, as a child would hear it said. */
    val meeting: (Int) -> String,

    val readyTitle: String,
    val readySubtitle: String,
    /**
     * The one who does the talking, by name.
     *
     * A child is being asked to answer out loud. Answering out loud to nobody is a strange
     * thing to ask, so the voice has a name and a face.
     */
    val companionName: String,
    val startLive: String,
    /** Demo route names, by route id. The routes themselves are the same journey either way. */
    val routeNames: Map<String, String>,

    val tagline: String,
    val noPlaceYet: String,
    val routeDiscovery: String,
    val todaysRoute: String,
    /** Minutes until this place, as the child hears it. */
    val minutesAway: (Int) -> String,
    /**
     * The two states of the one button that stops and starts the talking.
     *
     * "잠깐 쉬기" and "이어서 가기" describe what the app does. A child pressing a button is
     * saying something to somebody, so these say it in their words instead: stop telling me
     * things, start telling me things.
     */
    val pause: String,
    val resume: String,
    val replayFilm: String,
    val nowSpeaking: String,
    val nowListening: String,

    /**
     * What the app says back when it could not act on what it heard.
     *
     * These lived in the screen as Korean literals, so an English child who said exactly what
     * the screen asked them to say was answered in Korean. They are read by the child, so they
     * belong in the pack with everything else the child reads.
     */
    val resting: String,
    val sayAgain: String,
    val sayIfYouSee: String,
    val justListening: String,
    /** One line of what the picture is showing, by what is drawn. */
    val visualSummary: Map<CuratorVisual, String>,
) {
    companion object {
        fun of(language: ChildLanguage): Phrases = when (language) {
            ChildLanguage.KOREAN -> korean
            ChildLanguage.ENGLISH -> english
        }

        private val korean = Phrases(
            stepPredictImagine = "01  먼저 상상해 보기",
            stepPredictDeeper = "01  또 다른 질문",
            stepPredictQuiz = "01  맞혀 보기",
            stepPredictHunt = "01  찾기 놀이",
            stepWatch = "02  창밖에서 만나기",
            stepExplain = "03  장소의 이야기",
            stepFamily = "우리 가족의 기억",
            stepPlayPrefix = "04  오늘의 ",
            stepMemory = "오늘의 발견",
            stepFinished = "오늘 만난 이야기",
            stepWaiting = "다음 이야기를 기다려요",
            listening = "듣고 있어. 그냥 말해도 돼.",
            waitingForYou = "잠깐 기다려 줄게.",
            speakFreely = "말로 답해도 돼요.",
            watchListening = "보이면 ‘봤어’라고 말해 줘.",
            watchWaiting = "찾으면 말해 줘.",
            playListening = "듣고 있어. 네 생각을 말해 줘.",
            playWaiting = "생각나면 말해 줘.",
            youSaid = "네가 한 말: ",
            askAgain = "다시 물어봐 줘",
            useTheScreen = "화면으로 고를래",
            sawIt = "봤어!",
            didNotSee = "안 보여",
            myTurn = "이제 내가 해 볼래!",
            readTheStory = "해설을 글로 보기",
            rememberIt = "기억나!",
            notSure = "잘 모르겠어",
            heardIt = "그랬구나!",
            imagineIt = "그때를 상상해 볼래",
            pretendPlay = "여기부터는 네가 만드는 이야기야",
            micAsk = "확인을 누르면 이번 답변을 듣기 시작해요. 음성은 기기 안에서 처리하고 외부 AI로 보내지 않아요.",
            micLater = "나중에",
            micGo = "이야기할게",
            micOpen = "듣고 있어. 그냥 말해도 돼.",
            micClosed = "지금은 화면으로 골라줘",
            needsLocation = "주변 장소를 찾으려면 정확한 위치 권한이 필요해요.",
            preparingVoice = "음성을 준비하고 있어요",
            naviBefore = "내비게이션 연결 전",
            sayItMyself = "내 생각엔… (직접 말하기)",
            inMyOwnWords = "내 생각엔… (직접 말하기)",
            keepWatching = "계속 창밖 보기",
            listenAgain = "다시 듣기",
            skip = "넘어갈래",
            guardianNote = "보호자가 적어 둔 기억이에요",
            noNeedToSpeak = "말하지 않아도 괜찮아",
            meeting = { nth -> "${nth}번째 만남" },
            readyTitle = "지나칠 뻔한 곳에\n어떤 이야기가 있을까?",
            readySubtitle = "두리가 약 5분 전에 물어볼 거야.\n가까이 가면 직접 살펴봐요.",
            companionName = "두리",
            startLive = "실제 내비로 음성 여행 시작",
            routeNames = mapOf(
                "hangang-science" to "한강의 과학 여행",
                "old-seoul" to "서울을 가로지르는 길",
            ),
            tagline = "가는 길이 이야기가 되는 시간",
            noPlaceYet = "길에서 만날 이야기",
            routeDiscovery = "경로에서 만나는 발견",
            todaysRoute = "오늘 지나온 발견",
            minutesAway = { minutes -> "이 지점까지 약 ${minutes}분" },
            pause = "그만 들을래",
            resume = "다시 들을래",
            replayFilm = "짧은 원리 영상 다시 보기",
            nowSpeaking = "이야기를 듣는 중",
            nowListening = "네 생각을 듣고 있어",
            resting = "쉬는 중이에요",
            sayAgain = "다시 한 번 말해 줄래?",
            sayIfYouSee = "보이면 ‘봤어’, 안 보이면 ‘안 보여’라고 알려줘",
            justListening = "지금은 편하게 듣고 있어도 좋아",
            visualSummary = mapOf(
                CuratorVisual.TOWER to "몸통과 바깥 기둥이 힘을 나눠요.",
                CuratorVisual.GLASS to "뼈대와 유리벽은 역할이 달라요.",
                CuratorVisual.ISLAND to "모래섬 위에 도시가 자랐어요.",
                CuratorVisual.RECIPE to "가게의 이야기가 다음 사람에게 이어져요.",
                CuratorVisual.GATE to "문을 지나면 어떤 세상이 펼쳐질까요?",
            ),
        )

        private val english = Phrases(
            stepPredictImagine = "01  Guess first",
            stepPredictDeeper = "01  A different question",
            stepPredictQuiz = "01  Guessing game",
            stepPredictHunt = "01  Looking game",
            stepWatch = "02  Out of the window",
            stepExplain = "03  The story of this place",
            stepFamily = "Our family's memory",
            stepPlayPrefix = "04  Today you are the ",
            stepMemory = "Today's discovery",
            stepFinished = "Stories we met today",
            stepWaiting = "Waiting for the next story",
            listening = "I'm listening. Just say it.",
            waitingForYou = "I'll wait for you.",
            speakFreely = "You can answer out loud.",
            watchListening = "Say “I see it” when you spot it.",
            watchWaiting = "Tell me when you find it.",
            playListening = "I'm listening. Tell me your idea.",
            playWaiting = "Tell me when you think of something.",
            youSaid = "You said: ",
            askAgain = "Ask me again",
            useTheScreen = "Let me pick on screen",
            sawIt = "I see it!",
            didNotSee = "I can't see it",
            myTurn = "My turn now!",
            readTheStory = "Read the story",
            rememberIt = "I remember!",
            notSure = "I'm not sure",
            heardIt = "Oh, I see!",
            imagineIt = "I'll picture that day",
            pretendPlay = "From here, the story is yours to make up",
            micAsk = "Press OK and I'll start listening for your answer. Your voice stays in this car and is never sent to an outside AI.",
            micLater = "Not now",
            micGo = "I'll talk",
            micOpen = "I'm listening. Just say it.",
            micClosed = "Let's use the screen for now",
            needsLocation = "I need precise location to find places along the way.",
            preparingVoice = "Getting my voice ready",
            naviBefore = "Not connected to navigation yet",
            sayItMyself = "I think… (say it out loud)",
            inMyOwnWords = "I think… (say it out loud)",
            keepWatching = "Keep watching outside",
            listenAgain = "Say it again",
            skip = "Move on",
            guardianNote = "A memory your grown-up wrote down",
            noNeedToSpeak = "You don't have to say anything",
            meeting = { nth ->
                val suffix = when {
                    nth % 100 in 11..13 -> "th"
                    nth % 10 == 1 -> "st"
                    nth % 10 == 2 -> "nd"
                    nth % 10 == 3 -> "rd"
                    else -> "th"
                }
                "$nth$suffix time here"
            },
            readyTitle = "What story is hiding\nin a place we drive past?",
            readySubtitle = "Doori asks about five minutes ahead,\nthen you look for it yourself.",
            companionName = "Doori",
            startLive = "Start the trip with real navigation",
            routeNames = mapOf(
                "hangang-science" to "Science along the Han River",
                "old-seoul" to "Across Seoul",
            ),
            tagline = "When the drive becomes a story",
            noPlaceYet = "A story along the way",
            routeDiscovery = "Something to find on this route",
            todaysRoute = "Where we went today",
            minutesAway = { minutes -> "About $minutes min to go" },
            pause = "Stop talking",
            resume = "Talk to me again",
            replayFilm = "Play the short clip again",
            nowSpeaking = "Telling the story",
            nowListening = "Listening to you",
            resting = "Taking a break",
            sayAgain = "Could you say that again?",
            sayIfYouSee = "Say “I see it”, or “I can't see it”",
            justListening = "You can just listen for now",
            visualSummary = mapOf(
                CuratorVisual.TOWER to "The middle and the outer columns share the push.",
                CuratorVisual.GLASS to "The frame and the glass wall have different jobs.",
                CuratorVisual.ISLAND to "A city grew on a sandy island.",
                CuratorVisual.RECIPE to "One shop's story passed to the next person.",
                CuratorVisual.GATE to "What kind of world is through the gate?",
            ),
        )
    }
}
