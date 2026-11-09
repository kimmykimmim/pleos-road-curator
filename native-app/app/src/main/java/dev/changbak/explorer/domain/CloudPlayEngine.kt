package dev.changbak.explorer.domain

import java.text.Normalizer

enum class CloudRole { FRIEND, BACKGROUND }
enum class JourneyExpectation(val label: String, val title: String) {
    FAMILY("가족 만나기", "가족을 만나러 가던 날"),
    PLAY("즐겁게 놀기", "신나게 놀러 가던 날"),
    DISCOVERY("새로운 곳 만나기", "새로운 곳으로 가던 날"),
}
data class SkyCloud(val id: Int, val figure: CloudFriend, val role: CloudRole)
data class CloudWorld(
    val clouds: List<SkyCloud> = emptyList(),
    val activeId: Int? = null,
    val lastTouchedId: Int? = null,
    val draft: CloudFriend? = null,
    val expectation: JourneyExpectation? = null,
    val introDone: Boolean = false,
    val introPending: Boolean = false,
    val favoriteId: Int? = null,
    val journey: SkyJourney = SkyJourney(),
)

/** A cloud is created only by a shape observation. Daily conversation cannot create scenery. */
object CloudPlayEngine {
    const val MAX_CLOUDS = 8
    private val comparison = Regex("같아|같네|같다|처럼|모양|닮")
    private val question = Regex("왜|어디|누구|어떻게|무슨|무엇|뭐|뭘|언제|얼마|몇|할까|일까|수있")
    private val correction = Regex("아니라|아니고|말고|아니야|바꿔")
    private val backgroundRole = Regex("배경|하늘에(?:띄|놓|남|있)|띄워둘래|띄워줘")
    private val friendRole = Regex("친구로|친구가되|친구만들|친구를만들")
    private val shapeWords = "토끼|고래|공룡|용|강아지|고양이|거북이|새|빵|배|우주선|하트"
    private val bareShape = Regex("^(?:저건|이건|저거|이거)?(?:$shapeWords)(?:야|이야|네|이네|다)?$")
    private val bareFeature = Regex("^(?:(?:아주|엄청|좀|조금)?(?:동글동글|동그래|둥글어|몽실몽실|길쭉해|길쭉길쭉|커다래|작아|반짝반짝))(?:해|하다|해요|야|요)?$")

    fun create() = TravelState(cloudWorld = CloudWorld())
    fun active(state: TravelState): SkyCloud? = state.cloudWorld?.let { world ->
        world.clouds.firstOrNull { it.id == world.activeId && it.role == CloudRole.FRIEND }
    }
    fun title(cloud: SkyCloud) = cloud.figure.nickname ?: cloud.figure.label
    fun friends(state: TravelState) = state.cloudWorld?.clouds.orEmpty().filter { it.role == CloudRole.FRIEND }
    fun describe(figure: CloudFriend) = (listOf(figure.label) + figure.features.map { it.label }).distinct().joinToString(" ")
    fun journeyTitle(state: TravelState) = state.cloudWorld?.expectation?.title ?: "오늘 우리가 함께 온 길"
    fun favorite(state: TravelState) = state.cloudWorld?.let { world -> world.clouds.firstOrNull { it.id == world.favoriteId } }
    fun remember(state: TravelState, id: Int? = state.cloudWorld?.lastTouchedId): TravelTurn {
        val world = state.cloudWorld ?: CloudWorld()
        val cloud = world.clouds.firstOrNull { it.id == id }
            ?: return TravelTurn(state, "기억하고 싶은 구름이 생기면 알려줘. 지금 고르지 않아도 괜찮아.", TravelOutcome.HELP)
        return TravelTurn(state.copy(cloudWorld = world.copy(favoriteId = cloud.id)),
            "${title(cloud)}를 만난 순간을 오늘의 기억으로 담았어.", TravelOutcome.CHAT)
    }

    /** Keeps the existing renderer/conversation context grounded in the selected cloud only. */
    fun project(state: TravelState, world: CloudWorld): TravelState {
        val selected = world.clouds.firstOrNull { it.id == world.activeId && it.role == CloudRole.FRIEND }
        val body = selected?.figure
        val memories = if (selected == null) emptyList() else listOf(
            TravelMemory(selected.id, ScenerySource.CLOUD, "구름에서 떠올린 ${describe(selected.figure)} 모습", FriendTrait.BODY)
        )
        return state.copy(body = body, memories = memories, pending = FriendChoice.NONE, cloudWorld = world)
    }

    fun start(state: TravelState): TravelTurn {
        val world = state.cloudWorld ?: CloudWorld()
        val askExpectation = world.clouds.isEmpty() && !world.introDone
        val next = state.copy(phase = CloudPhase.WATCHING,
            cloudWorld = world.copy(draft = null, introDone = true, introPending = askExpectation))
        val reply = if (askExpectation) "오늘은 뭐가 기대돼? 구름부터 만나도 좋아." else active(next)?.let { "${title(it)}와 다시 만났네. 오늘은 어떤 일이 있을 것 같아?" }
            ?: "창밖의 구름은 어떤 모습 같아? 떠오르면 들려줘."
        return TravelTurn(next, reply, TravelOutcome.RESUME)
    }
    fun rest(state: TravelState) = TravelTurn(state.copy(
        phase = if (state.phase == CloudPhase.KEEPSAKE) CloudPhase.KEEPSAKE else CloudPhase.RESTING,
        cloudWorld = state.cloudWorld?.copy(draft = null, introPending = false)), "편하게 쉬어도 좋아. 하늘은 여기 남겨둘게.", TravelOutcome.REST)
    fun finish(state: TravelState) = TravelTurn(state.copy(phase = CloudPhase.KEEPSAKE,
        cloudWorld = state.cloudWorld?.let { it.copy(draft = null, introPending = false, journey = it.journey.copy(complete = true)) }),
        if (!state.cloudWorld?.journey?.landmarks.isNullOrEmpty()) "우리가 함께 지나온 길이 하늘섬이 됐어. ${state.cloudWorld!!.journey.landmarks.map { it.kind.label }.distinct().joinToString(", ")}도 여기 남았네."
        else if (state.cloudWorld?.clouds.isNullOrEmpty()) "오늘은 비어 있는 하늘로 남겨둘게."
        else favorite(state)?.let { "${journeyTitle(state)}, ${title(it)}를 만난 순간이 남았네." }
            ?: "오늘 오는 길에 어떤 구름이 가장 기억나? 골라서 남겨도 좋아.", TravelOutcome.FINISH)
    fun help(state: TravelState) = TravelTurn(state,
        "토끼 같아, 길쭉해처럼 말해도 좋아. 아무것도 떠오르지 않으면 그냥 쉬어도 괜찮아.", TravelOutcome.HELP)

    fun select(state: TravelState, id: Int): TravelTurn {
        val world = state.cloudWorld ?: CloudWorld()
        val cloud = world.clouds.firstOrNull { it.id == id && it.role == CloudRole.FRIEND }
            ?: return TravelTurn(state, null, TravelOutcome.IGNORED)
        return TravelTurn(project(state, world.copy(activeId = id, lastTouchedId = id, draft = null)),
            "안녕! 나는 네가 구름에서 떠올린 ${title(cloud)}야. 오늘은 어떤 일이 있을 것 같아?", TravelOutcome.CHAT)
    }

    private fun createCloud(state: TravelState, figure: CloudFriend, role: CloudRole): TravelTurn {
        val world = state.cloudWorld ?: CloudWorld()
        if (world.clouds.size >= MAX_CLOUDS) return TravelTurn(state.copy(cloudWorld = world.copy(draft = null)),
            "지금 하늘에 있는 구름들과 더 놀아볼까? 새 하늘은 보호자 메뉴에서 시작할 수 있어.", TravelOutcome.HELP)
        val cloud = SkyCloud((world.clouds.maxOfOrNull { it.id } ?: 0) + 1, figure, role)
        val next = project(state, world.copy(clouds = world.clouds + cloud, draft = null, introPending = false, lastTouchedId = cloud.id,
            activeId = if (role == CloudRole.FRIEND) cloud.id else world.activeId))
        val reply = if (role == CloudRole.FRIEND)
            if (world.expectation == null) "안녕! 나도 우리 차에 같이 탈게. 네 창밖에는 어떤 곳이 보여?"
            else "안녕! ${journeyTitle(state)}에 만난 ${title(cloud)}가 나야. 나도 함께 탈게. 창밖에는 어떤 곳이 보여?"
        else "${figure.label} 구름을 하늘에 띄웠어. ${active(next)?.let { "${title(it)}도 그대로 함께 있어." } ?: "친구로 만들 구름도 떠오르면 들려줘."}"
        return TravelTurn(next, reply, TravelOutcome.OBSERVED)
    }

    fun observe(state: TravelState, text: String): TravelTurn {
        val input = Normalizer.normalize(text, Normalizer.Form.NFKC).trim()
        val compact = input.replace(Regex("[\\s\\p{Z}\\p{P}]"), "")
        val world = state.cloudWorld ?: CloudWorld()
        SkyJourneyEngine.observe(state, input)?.let { return it }
        fun chat(reply: String? = null) = TravelTurn(state, reply, TravelOutcome.CHAT)
        if (input.isBlank()) return TravelTurn(state, null, TravelOutcome.IGNORED)
        if (compact in setOf("쉬자", "잠깐쉬자", "쉬고싶어", "조용히있고싶어", "그만", "그만할래", "구름이안보여", "구름이없어", "밤이야", "밖이안보여")) return rest(state)
        if (compact in setOf("다시보여", "시작", "시작하자", "다시할래", "계속하자")) return start(state)
        if (compact in setOf("도착했어", "도착했어요", "여행끝", "오늘은끝", "끝났어")) return finish(state)
        if (state.phase == CloudPhase.RESTING) return TravelTurn(state, null, TravelOutcome.IGNORED)
        if (compact in setOf("되돌려", "되돌려줘", "잘못말했어", "방금취소")) return TravelTurn(state, null, TravelOutcome.UNDO)
        if (compact in setOf("다시말해줘", "다시들려줘", "다시듣기")) return TravelTurn(state, null, TravelOutcome.REPEAT)
        if (compact in setOf("구름부터볼래", "구름부터", "하늘부터볼래", "건너뛸래") ||
            (world.introPending && compact in setOf("모르겠어", "생각안나", "아직몰라"))) {
            return TravelTurn(state.copy(cloudWorld = world.copy(introPending = false)), "좋아. 구름은 어떤 모습 같아?", TravelOutcome.RESUME)
        }
        if (world.introPending && !Regex("구름|같아|모양|닮").containsMatchIn(compact)) {
            val expectation = when {
                Regex("할머니|할아버지|가족|엄마|아빠|친척").containsMatchIn(compact) -> JourneyExpectation.FAMILY
                Regex("놀러|놀이|놀거|놀꺼|공원|수영|캠핑|소풍|신나").containsMatchIn(compact) -> JourneyExpectation.PLAY
                Regex("처음|새로운곳|박물관|동물원|여행").containsMatchIn(compact) -> JourneyExpectation.DISCOVERY
                else -> null
            }
            if (expectation != null) return TravelTurn(state.copy(cloudWorld = world.copy(expectation = expectation, introPending = false)),
                "${expectation.label}가 기대되는구나. 가는 길의 구름은 어떤 모습 같아?", TravelOutcome.CHAT)
        }
        if (Regex("기억할래|기억해줘|기억에담|순간남기|순간을남|간직할래").containsMatchIn(compact)) {
            if (world.draft != null) return TravelTurn(state, "이 구름을 친구로 만들지, 하늘에 띄울지 먼저 골라줘.", TravelOutcome.HELP)
            val named = world.clouds.lastOrNull { compact.contains(title(it)) || compact.contains(it.figure.label) }
            return remember(state, named?.id ?: world.lastTouchedId)
        }
        if (compact in setOf("기억선택취소", "기억에서빼줘")) return TravelTurn(state.copy(cloudWorld = world.copy(favoriteId = null)),
            "알겠어. 기억할 순간은 나중에 골라도 좋아.", TravelOutcome.CHAT)
        if (compact in setOf("모르겠어", "생각안나", "힌트", "도와줘", "어려워")) return help(state)
        if (input.length > 500) return chat()
        if (compact in setOf("취소", "나중에", "지금은말고", "그냥둘래")) return TravelTurn(state.copy(cloudWorld = world.copy(draft = null)),
            "좋아. 천천히 생각해도 괜찮아.", TravelOutcome.UNCHANGED)

        val role = when {
            backgroundRole.containsMatchIn(compact) -> CloudRole.BACKGROUND
            friendRole.containsMatchIn(compact) -> CloudRole.FRIEND
            else -> null
        }
        if (world.draft != null && role != null && !compact.contains("같") && !compact.contains("모양") &&
            (!Regex(shapeWords).containsMatchIn(compact) || compact.contains(world.draft.label))) {
            return createCloud(state, world.draft, role)
        }

        val named = world.clouds.lastOrNull { compact.contains(title(it)) || compact.contains(it.figure.label) }
        if (named != null && Regex("얘기|이야기|대화").containsMatchIn(compact) && Regex("랑|하고|와|과").containsMatchIn(compact)) {
            return if (named.role == CloudRole.FRIEND) select(state, named.id)
            else TravelTurn(state.copy(cloudWorld = world.copy(draft = named.figure)), "이 구름을 새 친구로도 만들어볼까?", TravelOutcome.HELP)
        }

        if (active(state) != null && !question.containsMatchIn(compact) && !input.contains('?') &&
            Regex("^(?:이름|별명)(?:은|을)?").containsMatchIn(compact)) {
            val current = active(state)!!
            val parsed = CloudEngine.observe(CloudState(CloudPhase.WATCHING, current.figure), input)
            if (parsed.state.friend?.nickname != null && parsed.state.friend != current.figure) {
                val revised = current.copy(figure = parsed.state.friend)
                return TravelTurn(project(state, world.copy(clouds = world.clouds.map { if (it.id == current.id) revised else it })),
                    "좋아, 내 이름은 ${revised.figure.nickname}!", TravelOutcome.CORRECTED)
            }
        }
        if (question.containsMatchIn(compact) || input.contains('?') || input.contains('？')) {
            if (Regex("어디서왔|어떻게태어|어떻게생겼|무슨구름|누구야|이름이뭐|이름뭐|어떤구름|뭘봤").containsMatchIn(compact)) {
                val current = active(state)
                val answer = if (current == null) "아직 대화하는 친구는 없어. 구름에서 어떤 모습이 떠오르면 들려줘."
                    else "나는 네가 구름에서 떠올린 ${describe(current.figure)} 모습이야. ${current.figure.nickname?.let { "이름은 ${it}야." } ?: "네가 발견해 줘서 여기 생겼어."}"
                return chat(answer)
            }
            return chat()
        }

        // Feelings, plans and real objects are not cloud observations, even when animal nouns occur.
        val hasCloud = compact.contains("구름")
        if (Regex("기분|마음|꿈|인형|장난감|그림|사진|텔레비전|책에서|먹고|먹었|좋아해|갖고").containsMatchIn(compact)) return chat()
        val comparisonContext = comparison.containsMatchIn(compact) &&
            (active(state) == null || Regex("^(?:저거|이거|저건|이건|저쪽)").containsMatchIn(compact) ||
                Regex("^(?:$shapeWords)(?:같|처럼|모양)").containsMatchIn(compact))
        val explicitRoleShape = role != null && Regex(shapeWords).containsMatchIn(compact)
        val isCorrection = correction.containsMatchIn(compact) && world.clouds.isNotEmpty()
        if (!hasCloud && !comparisonContext && !bareShape.matches(compact) && !bareFeature.matches(compact) && !explicitRoleShape && !isCorrection) {
            return chat(if (active(state) == null) "구름에서 닮은 모습이 떠오르면 알려줘. 그때 친구를 만날 수 있어." else null)
        }
        val cloudDescription = input
            .replace(Regex("([가-힣]{1,20})\\s*같은\\s*구름")) { "구름이 ${it.groupValues[1]} 같아" }
            .replace(Regex("($shapeWords)\\s*구름")) { "구름이 ${it.groupValues[1]} 같아" }
        val parsed = CloudEngine.observe(CloudState(CloudPhase.WATCHING), cloudDescription)
        val figure = parsed.state.friend
        val hasShape = figure != null && (figure.form != CloudForm.CLOUD || figure.features.isNotEmpty())
        if (!hasShape) return if (active(state) == null || hasCloud) help(state) else chat()
        val shape = figure!!
        if (isCorrection) {
            if (world.draft != null) return TravelTurn(state.copy(cloudWorld = world.copy(draft = shape)),
                "${shape.label} 모습으로 바꿨어. 친구로 만들까, 하늘에 띄울까?", TravelOutcome.CORRECTED)
            val target = world.clouds.firstOrNull { it.id == world.lastTouchedId } ?: world.clouds.last()
            val changed = target.copy(figure = shape.copy(nickname = target.figure.nickname))
            return TravelTurn(project(state, world.copy(clouds = world.clouds.map { if (it.id == target.id) changed else it })),
                "알겠어. 그 구름은 ${shape.label} 모습으로 기억할게.", TravelOutcome.CORRECTED)
        }
        if (role != null) return createCloud(state, shape, role)
        if (friends(state).isEmpty()) return createCloud(state, shape, CloudRole.FRIEND)
        val last = world.clouds.lastOrNull()
        val isAnother = Regex("다른|저쪽|또|하나더|새로운").containsMatchIn(compact)
        if (last?.figure?.copy(nickname = null) == shape && !isAnother) return TravelTurn(state, "응, 그 구름은 여기 그대로 있어.", TravelOutcome.UNCHANGED)
        return TravelTurn(state.copy(cloudWorld = world.copy(draft = shape)),
            "${shape.label} 구름이구나. 친구로 만들까, 하늘에 띄울까?", TravelOutcome.OBSERVED)
    }
}
