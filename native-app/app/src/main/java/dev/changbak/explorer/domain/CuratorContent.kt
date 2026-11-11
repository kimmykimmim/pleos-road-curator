package dev.changbak.explorer.domain

/**
 * Reviewed factual narration and separately marked imagination. Media are original schematic
 * diagrams.
 *
 * Each place carries four rounds rather than one question. The first is the hook, used the first
 * time a family meets the place; the rest rotate on later journeys, because the same road is
 * driven every day and a repeated sentence is how a child learns to tune the car out.
 *
 * Everything stated as fact in a lead, a question or a reveal comes from the place's own source,
 * the same one shown in 설명 출처. A quiz reveal tells the child the answer and never tells them
 * they were wrong.
 */
object CuratorCatalog {
    val places = listOf(
        CuratorPlace("lotte", "롯데월드타워", "높은 건물의 숨은 팔",
            listOf(
                RoundPrompt(RoundKind.HOOK,
                    "한국에서 가장 높은 건물이 어디인지 알아? 잠시 뒤에 그 옆을 지나가.",
                    "키가 아주 큰 건물은 바람이 불면 어떻게 버틸까?",
                    listOf("안에서 함께 붙잡아 줄까?", "아주 무거워서일까?")),
                RoundPrompt(RoundKind.DEEPER,
                    "지난번에는 바람 이야기를 했지. 오늘은 다른 게 궁금해.",
                    "저렇게 높은 꼭대기까지 사람들은 어떻게 올라갈까?",
                    listOf("엘리베이터가 아주 빠를까?", "계단이 끝없이 많을까?")),
                RoundPrompt(RoundKind.QUIZ,
                    "오늘은 맞혀 보기야. 몰라도 괜찮아.",
                    "롯데월드타워는 몇 층짜리 건물일까?",
                    listOf("123층", "50층"),
                    reveal = "123층이야! 높이는 555미터, 우리나라에서 가장 높은 건물이지. 이제 알았으니까 창밖에서 세어 보는 건 어려울 거야."),
                RoundPrompt(RoundKind.HUNT,
                    "오늘은 찾기 놀이야.",
                    "꼭대기가 구름에 닿아 있어, 아니면 끝까지 다 보여?",
                    listOf("구름에 닿았어", "끝까지 다 보여")),
            ),
            "위로 갈수록 가늘어지는 모습을 살펴봐. 안쪽 구조는 그림으로 만나자.",
            "높은 건물 안에는 힘을 나누는 팀이 있어! 롯데월드타워는 중심 몸통과 커다란 바깥 기둥을 연결해 바람에 버텨. 연결하는 팔 같은 구조가 아웃리거야. 바깥 기둥끼리도 벨트트러스로 이어져 있어. 그림은 그 원리를 간단하게 보여주는 거야.",
            "롯데건설 초고층 구조 기술", "https://www.lottecon.co.kr/Medias/tower_prmovie",
            "건축 과학", CuratorVisual.TOWER, "tower",
            PlaceActivity("구조 설계자", "건물 안의 힘 나누기 팀을 연결해 볼까?", listOf(
                PlayChoice("core", "몸통과 기둥 연결!", "네가 연결한 팔이 중심 몸통과 바깥 기둥을 함께 일하게 만들었어. 실제 건물은 훨씬 복잡하지만, 힘을 나눈다는 원리를 만났네!", cues = listOf("몸통", "중심")),
                PlayChoice("belt", "바깥 기둥끼리 연결!", "네가 고른 띠가 바깥 기둥들을 이어 줬어. 혼자보다 함께 힘을 나누는 팀이 된 거야. 그림 속 연결 부분을 켜 두고 다음 길로 가자.", cues = listOf("기둥끼리", "바깥", "띠", "벨트")))),
            talk = PlaceTalk(
                topics = listOf(
                    TalkBranch(listOf("바람","흔들","불면","태풍"),
                        "바람 이야기를 했구나. 저렇게 높으면 바람이 정말 세게 불어. 그런데 건물이 조금 흔들려도 괜찮을까, 하나도 안 흔들려야 할까?"),
                    TalkBranch(listOf("무거","무게","돌","콘크리"),
                        "무거워서 버틴다고 생각했구나. 무거운 게 도움이 될 때도 있어. 그럼 꼭대기까지 전부 무겁게 만들면 어떻게 될까?"),
                    TalkBranch(listOf("기둥","뼈대","쇠","막대","철근"),
                        "기둥을 떠올렸네. 안쪽에도 바깥에도 기둥이 있어. 그 둘이 서로 도울 수 있을까?"),
                    TalkBranch(listOf("땅","뿌리","깊","박아","박혀"),
                        "땅속을 생각했구나. 깊이 박혀 있는 것도 맞아. 그럼 땅 위에서는 무엇이 붙잡아 줄까?"),
                ),
                echo = "“%s”라고 했구나. 그렇게 볼 수도 있겠다. 그럼 바람이 옆에서 밀면 저 건물은 어떻게 될까?",
                unsure = "몰라도 괜찮아. 어른도 처음엔 몰랐을 거야.",
                silence = "괜찮아. 생각만 해도 좋아.",
                closing = "이제 창밖을 봐. 곧 지나가.",
            ),
        ),

        CuratorPlace("building63", "63빌딩", "건물도 겉옷을 입을까?",
            listOf(
                RoundPrompt(RoundKind.HOOK,
                    "지금은 아니지만, 한때 우리나라에서 가장 높았던 건물이 곧 보여. 1985년에 지어진 금빛 건물이야.",
                    "반짝이는 유리벽이 건물 전체를 떠받치는 걸까?",
                    listOf("안쪽에 뼈대가 있을까?", "유리벽이 버티는 걸까?")),
                RoundPrompt(RoundKind.DEEPER,
                    "지난번엔 겉옷 이야기를 했어. 오늘은 안쪽이 궁금해.",
                    "저 안에서 일하는 사람은 창밖으로 무엇이 보일까?",
                    listOf("한강이 보일 거야", "구름이 보일 거야")),
                RoundPrompt(RoundKind.QUIZ,
                    "오늘은 맞혀 보기야. 몰라도 괜찮아.",
                    "63빌딩이 지어졌을 때 우리나라에서 몇 번째로 높았을까?",
                    listOf("첫 번째", "열 번째"),
                    reveal = "첫 번째였어! 1985년에는 우리나라에서 가장 높은 건물이었대. 지금은 더 높은 건물이 많이 생겼지."),
                RoundPrompt(RoundKind.HUNT,
                    "오늘은 색깔 찾기야. 해가 비치는 방향에 따라 달라지거든.",
                    "지금 네 눈에 63빌딩은 무슨 색으로 보여?",
                    listOf("금빛이야", "하얗게 보여")),
            ),
            "반짝이는 바깥 벽을 살펴봐. 뼈대는 밖에서 보이지 않을 수 있어.",
            "63빌딩에는 커튼월이라는 유리 외벽 방식이 쓰였어. 커튼처럼 바깥을 감싸는 벽이라는 뜻이야. 건물을 지탱하는 뼈대와 바깥 유리벽은 역할이 달라. 뼈대가 몸이라면 유리벽은 겉옷에 비유할 수 있지. 진짜 커튼처럼 펄럭이는 건 아니야!",
            "포스코그룹 뉴스룸 커튼월 설명", "https://newsroom.posco.com/kr/글로벌-포스코의-시너지-대륙의-빌딩도-빛낸다/",
            "건축 과학", CuratorVisual.GLASS, "glass",
            PlaceActivity("건물 옷 디자이너", "뼈대와 겉옷 중 무엇부터 살펴볼까?", listOf(
                PlayChoice("skeleton", "안쪽 뼈대 보기", "유리 겉옷을 살짝 비켜 보니 안쪽 뼈대가 드러났어. 건물을 버티는 부분과 감싸는 부분을 나눠 봤네!", cues = listOf("뼈대", "안쪽")),
                PlayChoice("coat", "유리 겉옷 입히기", "뼈대 둘레에 유리 겉옷을 입혔어. 커튼월은 건물 전체를 떠받치는 뼈대와 다른 역할이라는 걸 기억해 줘.", cues = listOf("겉옷", "유리")))),
            talk = PlaceTalk(
                topics = listOf(
                    TalkBranch(listOf("유리","창문","반짝","금빛","거울","빛"),
                        "유리 이야기를 했구나. 저 금빛은 전부 유리야. 그럼 저 유리가 건물을 떠받치고 있을까, 아니면 다른 게 안에 있을까?"),
                    TalkBranch(listOf("뼈대","기둥","철근","안쪽","안에"),
                        "안쪽을 떠올렸네. 밖에서는 안 보이는 게 있다는 거지. 그게 유리보다 단단할까?"),
                    TalkBranch(listOf("무거","무게"),
                        "무게를 생각했구나. 유리는 생각보다 무거워. 그 무게는 누가 들고 있을까?"),
                    TalkBranch(listOf("높","커","크","제일"),
                        "크다고 느꼈구나. 옛날에는 우리나라에서 제일 높았대. 지금은 몇 번째쯤일 것 같아?"),
                ),
                echo = "“%s”라고 했구나. 재미있는 생각이야. 그럼 저 반짝이는 벽은 무슨 일을 하고 있을까?",
                unsure = "몰라도 괜찮아. 보기만 해도 충분해.",
                silence = "괜찮아. 눈으로 먼저 찾아보자.",
                closing = "창밖에서 금빛 벽을 찾아봐.",
            ),
        ),

        CuratorPlace("yeouido", "여의도", "모래섬의 시간 여행",
            listOf(
                RoundPrompt(RoundKind.HOOK,
                    "빌딩이 가득한 이 섬에서 옛날에는 비행기가 뜨고 내렸대.",
                    "지금 빌딩이 많은 이곳은 옛날에도 같은 모습이었을까?",
                    listOf("모래섬이었을까?", "비행장이 있었을까?")),
                RoundPrompt(RoundKind.DEEPER,
                    "지난번엔 옛날 이야기를 했지. 오늘은 앞으로를 생각해 보자.",
                    "100년 뒤에 이 섬은 어떤 모습일까?",
                    listOf("더 높아질 거야", "더 푸르러질 거야")),
                RoundPrompt(RoundKind.QUIZ,
                    "오늘은 맞혀 보기야. 몰라도 괜찮아.",
                    "여의도에 옛날에 있었던 건 무엇일까?",
                    listOf("비행장", "기차역"),
                    reveal = "비행장이었어! 한강의 넓은 모래섬이 비행장으로 쓰이던 시기가 있었대. 지금은 그 자리에 빌딩이 서 있지."),
                RoundPrompt(RoundKind.HUNT,
                    "오늘은 하나만 골라 보자. 다음에 올 때 또 찾아보게.",
                    "여기서 가장 높아 보이는 건물은 어느 거야?",
                    listOf("가운데 뾰족한 거", "강 쪽에 있는 거")),
            ),
            "강과 섬의 가장자리, 건물과 나무가 있는 공간을 살펴봐.",
            "시간을 뒤로 돌려 보자! 여의도는 원래 한강의 넓은 모래섬이었어. 비행장으로 쓰인 시기도 있었고, 나중에 섬 둘레에 둑을 쌓고 땅을 넓히며 도시가 자랐어. 강이 없던 곳에 갑자기 섬을 만든 건 아니야. 자연의 섬과 사람들의 변화가 겹쳐 지금 모습이 된 거지.",
            "서울역사박물관 여의도 전시 / 서울기록원", "https://museum.seoul.go.kr/www/board/NR_boardView.do?bbsCd=1002&seq=20210419212409075&sso=ok",
            "도시와 자연", CuratorVisual.ISLAND, "island",
            PlaceActivity("미래의 섬 설계자", "여기부터는 우리의 상상이야. 섬에 어떤 쉼터를 더하고 싶어?", listOf(
                PlayChoice("park", "나무 그늘 공원", "네가 상상한 나무 그늘이 모형 속 섬에 생겼어. 도시를 만들 때 사람이 쉴 자리도 생각해 본 거야.", cues = listOf("나무", "그늘", "공원")),
                PlayChoice("birds", "새들이 쉬는 물가", "모형 속 물가에 새들이 쉴 자리를 남겼어. 도시와 자연이 함께 있는 미래를 상상했네!", cues = listOf("새들", "물가", "새가")))),
            talk = PlaceTalk(
                topics = listOf(
                    TalkBranch(listOf("비행","비행기","활주","공항"),
                        "비행기 이야기! 맞아, 여기에 비행장이 있던 때가 있었대. 그럼 그 자리에는 지금 뭐가 있을까?"),
                    TalkBranch(listOf("모래","섬","강","모래사장"),
                        "모래랑 강을 떠올렸구나. 원래는 한강의 넓은 모래섬이었어. 모래 위에 이렇게 큰 건물을 세울 수 있을까?"),
                    TalkBranch(listOf("건물","빌딩","높","도시"),
                        "빌딩을 봤구나. 지금은 가득하지. 옛날에도 이런 모습이었을 것 같아?"),
                    TalkBranch(listOf("공원","나무","풀","초록"),
                        "초록을 봤네. 섬에는 쉬는 자리도 있어. 도시랑 나무가 같이 있으면 어떨 것 같아?"),
                ),
                echo = "“%s”라고 했구나. 그렇게 보일 수도 있겠다. 그럼 백 년 전에도 똑같았을까?",
                unsure = "몰라도 괜찮아. 이 섬은 어른도 헷갈려.",
                silence = "괜찮아. 강이랑 섬을 먼저 찾아보자.",
                closing = "창밖에서 강과 섬의 가장자리를 봐.",
            ),
        ),

        CuratorPlace("sungnyemun", "숭례문", "도시의 커다란 현관문",
            listOf(
                RoundPrompt(RoundKind.HOOK,
                    "옛날 서울에도 잠자는 시간이 있었대. 밤이 되면 도시 전체가 문을 닫았거든.",
                    "옛날에는 왜 도시에도 커다란 문이 필요했을까?",
                    listOf("도시를 지키려고", "사람들이 드나들려고")),
                RoundPrompt(RoundKind.DEEPER,
                    "지난번엔 문이 닫히는 이야기를 했어. 오늘은 문을 지키던 사람이 궁금해.",
                    "밤새 문 앞에 서서 무엇을 보고 있었을까?",
                    listOf("별을 봤을 거야", "지나가는 사람을 봤을 거야")),
                RoundPrompt(RoundKind.QUIZ,
                    "오늘은 맞혀 보기야. 몰라도 괜찮아.",
                    "숭례문은 언제 닫혔을까?",
                    listOf("밤에", "비 올 때"),
                    reveal = "밤에 닫았어! 밤에 닫고 새벽에 다시 열었대. 도시 전체에 커다란 현관문이 있었던 셈이지."),
                RoundPrompt(RoundKind.HUNT,
                    "오늘은 지붕 모양을 봐 줘.",
                    "지붕 끝이 하늘로 올라갔어, 아니면 아래로 내려왔어?",
                    listOf("하늘로 올라갔어", "아래로 내려왔어")),
            ),
            "큰 지붕 아래 둥근 통로를 찾아볼래?",
            "옛날 서울에도 잠자는 시간이 있었대! 한양도성 남쪽 문인 숭례문은 밤에 닫고 새벽에 다시 열었어. 도시 전체에 커다란 현관문이 있었던 셈이지. 돌로 쌓은 통로 위에는 두 층 건물도 있어.",
            "국가유산청 궁능유적본부", "https://royal.khs.go.kr/ROYAL/contents/R108010000.do",
            activity = PlaceActivity("아침 문지기", "문을 열면서 어떤 인사를 해 줄래?", listOf(
                PlayChoice("welcome", "좋은 아침! 들어오세요!", "네 인사와 함께 모형 속 문이 열렸어. 네가 도시의 아침을 열어 준 문지기가 됐네!", cues = listOf("좋은아침", "들어오", "환영")),
                PlayChoice("greet", "오늘도 즐겁게 다녀오세요!", "문을 지나가는 사람들에게 네 응원이 전해졌어. 이건 우리가 만든 상상 속 아침이야.", cues = listOf("다녀오", "즐겁", "문뒤")))),
            talk = PlaceTalk(
                topics = listOf(
                    TalkBranch(listOf("지키","막아","도둑","적이","군인","싸움"),
                        "지킨다고 했구나. 문을 닫으면 아무나 들어올 수 없었대. 그럼 문은 언제 닫았을까?"),
                    TalkBranch(listOf("드나","들어","나가","사람","길","다니"),
                        "사람들이 다니는 길을 떠올렸네. 문은 막기도 하고 열어 주기도 해. 밤에도 열려 있었을까?"),
                    TalkBranch(listOf("밤","어두","저녁","잠"),
                        "밤 이야기를 했구나! 밤에 닫고 새벽에 다시 열었대. 그럼 늦게 온 사람은 어떻게 했을까?"),
                    TalkBranch(listOf("지붕","기와","돌","오래","옛날"),
                        "지붕이랑 돌을 봤구나. 돌로 쌓은 통로 위에 두 층 건물이 있어. 저 위에는 누가 있었을까?"),
                ),
                echo = "“%s”라고 했구나. 그렇게 생각할 수도 있겠다. 그럼 도시 전체에 문이 있다면 언제 닫았을까?",
                unsure = "몰라도 괜찮아. 아주 옛날 이야기니까.",
                silence = "괜찮아. 큰 지붕을 먼저 찾아보자.",
                closing = "큰 지붕 아래 둥근 통로를 찾아봐.",
            ),
        ),

        CuratorPlace("gwanghwamun", "광화문", "문 뒤의 또 다른 세상",
            listOf(
                RoundPrompt(RoundKind.HOOK,
                    "문 하나를 지났을 뿐인데 완전히 다른 세상이 나오는 곳이 있어.",
                    "광화문 뒤에는 어떤 곳이 있을 것 같아?",
                    listOf("궁궐이 있을 것 같아", "시장이 있을 것 같아")),
                RoundPrompt(RoundKind.DEEPER,
                    "지난번엔 문 뒤의 세상을 봤지. 오늘은 문 앞이야.",
                    "이 길은 왜 이렇게 넓을까?",
                    listOf("사람이 많이 다녀서", "임금님이 지나가서")),
                RoundPrompt(RoundKind.QUIZ,
                    "오늘은 맞혀 보기야. 몰라도 괜찮아.",
                    "광화문을 지나면 무엇이 나올까?",
                    listOf("궁궐", "시장"),
                    reveal = "궁궐이야! 조선의 궁궐인 경복궁으로 들어가는 정문이 광화문이래. 숭례문이 도시의 문이라면 이건 궁궐의 문이지."),
                RoundPrompt(RoundKind.HUNT,
                    "오늘은 사람 수를 봐 줘. 올 때마다 다르거든.",
                    "문 앞에 사람이 많아, 아니면 한산해?",
                    listOf("사람이 많아", "한산해")),
            ),
            "큰 문 뒤로 이어지는 공간을 살펴봐.",
            "문을 하나 지나면 또 다른 세상이 펼쳐져! 광화문은 조선의 궁궐인 경복궁으로 들어가는 정문이야. 숭례문이 도시의 문이라면 광화문은 궁궐의 문이야. 문도 어디로 들어가느냐에 따라 역할이 다르지.",
            "정부 정책브리핑 궁궐 이야기", "https://www.korea.kr/goNewsRes/attaches/2019.05/23/107_20170109_01IozeNoYDGJM000.pdf",
            activity = PlaceActivity("궁궐 안내자", "처음 온 손님에게 한마디 해 줄래?", listOf(
                PlayChoice("welcome", "궁궐에 오신 걸 환영해요!", "네 안내와 함께 모형 속 궁궐 문이 열렸어. 광화문이 궁궐로 들어가는 문이라는 이야기를 전했네!", cues = listOf("좋은아침", "들어오", "환영")),
                PlayChoice("greet", "문 뒤에는 어떤 곳이 있을까요?", "손님에게 네가 품었던 궁금증을 건넸어. 다음 사람이 발견할 이야기를 열어 준 안내자야.", cues = listOf("다녀오", "즐겁", "문뒤")))),
            talk = PlaceTalk(
                topics = listOf(
                    TalkBranch(listOf("궁궐","임금","왕","경복"),
                        "궁궐을 떠올렸구나! 저 문 뒤가 궁궐이야. 그럼 임금님도 이 문으로 다녔을까?"),
                    TalkBranch(listOf("시장","가게","집","마을"),
                        "사람 사는 곳을 생각했네. 문 뒤가 늘 궁궐인 건 아니야. 그런데 이 문은 어떨 것 같아?"),
                    TalkBranch(listOf("넓","길","커","크"),
                        "길이 넓다고 느꼈구나. 정말 넓지. 왜 이렇게 넓게 만들었을까?"),
                    TalkBranch(listOf("문지기","지키","군인","병사"),
                        "지키는 사람을 떠올렸네. 큰 문에는 늘 누군가 서 있었대. 그 사람은 무엇을 보고 있었을까?"),
                ),
                echo = "“%s”라고 했구나. 그럴 수도 있겠다. 그럼 저 문 뒤에는 무엇이 있을까?",
                unsure = "몰라도 괜찮아. 문 뒤는 보이지 않으니까.",
                silence = "괜찮아. 문 뒤로 이어지는 곳을 봐.",
                closing = "큰 문 뒤로 이어지는 공간을 살펴봐.",
            ),
        ),

        CuratorPlace("cheongjinok", "청진옥", "한 그릇에 이어진 시간",
            listOf(
                RoundPrompt(RoundKind.HOOK,
                    "90년 가까이 같은 자리에서 같은 국을 끓이는 가게가 이 동네에 있어.",
                    "가게가 오래 이어지려면 무엇을 다음 사람에게 전해야 할까?",
                    listOf("맛과 만드는 방법", "손님에게 건네는 마음")),
                RoundPrompt(RoundKind.DEEPER,
                    "지난번엔 전해 주는 이야기를 했어. 오늘은 받는 사람이 궁금해.",
                    "할아버지의 국자를 물려받은 사람은 어떤 기분이었을까?",
                    listOf("뿌듯했을 거야", "조금 떨렸을 거야")),
                RoundPrompt(RoundKind.QUIZ,
                    "오늘은 맞혀 보기야. 몰라도 괜찮아.",
                    "청진옥이 처음 문을 연 건 언제일까?",
                    listOf("1937년", "1990년대"),
                    reveal = "1937년이래! 서울미래유산 기록에 그렇게 남아 있어. 3대에 걸쳐 가업을 이어 왔다고 해."),
                RoundPrompt(RoundKind.HUNT,
                    "오늘은 간판 찾기야. 골목 안이라 차에서는 안 보일 수도 있어.",
                    "이 동네에서 가장 오래되어 보이는 간판은 어느 거야?",
                    listOf("찾았어", "안 보여")),
            ),
            "골목 안 가게라 차에서는 보이지 않을 수 있어. 지금은 이 동네의 이야기를 들어 보자.",
            "국자 하나가 시간을 건너간다면? 서울미래유산에는 청진옥이 1937년에 문을 열고 3대에 걸쳐 가업을 이어 온 해장국집으로 소개돼 있어. 오래된 가게에는 음식뿐 아니라 만드는 방법과 동네 사람들의 기억도 함께 쌓이는 거야.",
            "서울미래유산 청진옥 기록", "https://futureheritage.seoul.go.kr/futureHeritageMeet/futureHeritage/view.do?htNo=135",
            "동네의 생활사", CuratorVisual.RECIPE, "recipe",
            PlaceActivity("가게의 이야기 전달자", "다음 사람에게 무엇을 전해 줄래?", listOf(
                PlayChoice("recipe", "맛의 기억을 전할래", "모형 속 국자가 다음 사람에게 건너갔어. 네가 가게의 맛과 이야기를 잇는 사람이 됐네. 실제 조리법을 재현한 건 아니야.", cues = listOf("맛", "음식", "국자")),
                PlayChoice("kindness", "따뜻한 인사를 전할래", "모형 속 가게에 네 따뜻한 인사가 더해졌어. 오래 이어지는 가게에 어떤 마음이 담길지 상상했네!", cues = listOf("인사", "따뜻")))),
            talk = PlaceTalk(
                topics = listOf(
                    TalkBranch(listOf("맛","음식","해장국","요리","만드는","조리"),
                        "맛 이야기를 했구나. 맛은 어떻게 다음 사람에게 전할까? 글로 적어 둘까, 옆에서 보여 줄까?"),
                    TalkBranch(listOf("마음","인사","친절","따뜻","사랑"),
                        "마음을 떠올렸네. 오래 가는 가게에는 그런 게 쌓인대. 손님은 그 마음을 어떻게 알아볼까?"),
                    TalkBranch(listOf("오래","옛날","할아버지","할머니","가족"),
                        "오래된 걸 생각했구나. 1937년에 문을 열었대. 네 나이보다 몇 배나 많을까?"),
                    TalkBranch(listOf("가게","간판","골목","식당"),
                        "가게를 떠올렸네. 골목 안이라 차에서는 안 보일 수도 있어. 오래된 가게는 무엇이 다를까?"),
                ),
                echo = "“%s”라고 했구나. 그것도 전할 수 있겠다. 그럼 그건 어떻게 다음 사람에게 건넬까?",
                unsure = "몰라도 괜찮아. 어려운 질문이야.",
                silence = "괜찮아. 이 동네 이야기를 들어 보자.",
                closing = "골목 쪽을 한번 봐. 안 보여도 괜찮아.",
            ),
        )
    )
}

data class CuratorDemoRoute(val id: String, val title: String, val subtitle: String, val placeIds: List<String>) {
    fun passageAt(index: Int) = 360 + index * 720
    /** Guardian places join the end of the demo so a family's own place can be shown too. */
    fun stops(alsoVisit: List<String> = emptyList()) = placeIds + alsoVisit.filter { it !in placeIds }
    fun endSeconds(alsoVisit: List<String> = emptyList()) = passageAt(stops(alsoVisit).lastIndex) + 180
    fun fix(seconds: Int, sequence: Long, alsoVisit: List<String> = emptyList()) =
        CuratorFix(id, sequence, stops(alsoVisit).mapIndexed { index,id ->
            PlaceApproach(id, passageAt(index)-seconds, seconds>passageAt(index)) })
}
object CuratorRoutes {
    val routes = listOf(
        CuratorDemoRoute("hangang-science", "한강의 과학 여행", "잠실 → 여의도 / 가상 경로", listOf("lotte","building63","yeouido")),
        CuratorDemoRoute("old-seoul", "도심의 오래된 이야기", "숭례문 → 광화문 → 청진옥 / 가상 경로", listOf("sungnyemun","gwanghwamun","cheongjinok"))
    )
}
