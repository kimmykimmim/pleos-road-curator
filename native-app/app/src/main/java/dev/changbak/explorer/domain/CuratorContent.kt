package dev.changbak.explorer.domain

/** Reviewed factual narration and separately marked imagination. Media are original schematic diagrams. */
object CuratorCatalog {
    val places = listOf(
        CuratorPlace("lotte", "롯데월드타워", "높은 건물의 숨은 팔", "키가 아주 큰 건물은 바람이 불면 어떻게 버틸까?",
            listOf("안에서 함께 붙잡아 줄까?", "아주 무거워서일까?"), "위로 갈수록 가늘어지는 모습을 살펴봐. 안쪽 구조는 그림으로 만나자.",
            "높은 건물 안에는 힘을 나누는 팀이 있어! 롯데월드타워는 중심 몸통과 커다란 바깥 기둥을 연결해 바람에 버텨. 연결하는 팔 같은 구조가 아웃리거야. 바깥 기둥끼리도 벨트트러스로 이어져 있어. 그림은 그 원리를 간단하게 보여주는 거야.",
            "롯데건설 초고층 구조 기술", "https://www.lottecon.co.kr/Medias/tower_prmovie",
            "건축 과학", CuratorVisual.TOWER, "tower",
            PlaceActivity("구조 설계자", "건물 안의 힘 나누기 팀을 연결해 볼까?", listOf(
                PlayChoice("core", "몸통과 기둥 연결!", "네가 연결한 팔이 중심 몸통과 바깥 기둥을 함께 일하게 만들었어. 실제 건물은 훨씬 복잡하지만, 힘을 나눈다는 원리를 만났네!", cues = listOf("몸통", "중심")),
                PlayChoice("belt", "바깥 기둥끼리 연결!", "네가 고른 띠가 바깥 기둥들을 이어 줬어. 혼자보다 함께 힘을 나누는 팀이 된 거야. 그림 속 연결 부분을 켜 두고 다음 길로 가자.", cues = listOf("기둥끼리", "바깥", "띠", "벨트"))))),
        CuratorPlace("building63", "63빌딩", "건물도 겉옷을 입을까?", "반짝이는 유리벽이 건물 전체를 떠받치는 걸까?",
            listOf("안쪽에 뼈대가 있을까?", "유리벽이 버티는 걸까?"), "반짝이는 바깥 벽을 살펴봐. 뼈대는 밖에서 보이지 않을 수 있어.",
            "63빌딩에는 커튼월이라는 유리 외벽 방식이 쓰였어. 커튼처럼 바깥을 감싸는 벽이라는 뜻이야. 건물을 지탱하는 뼈대와 바깥 유리벽은 역할이 달라. 뼈대가 몸이라면 유리벽은 겉옷에 비유할 수 있지. 진짜 커튼처럼 펄럭이는 건 아니야!",
            "포스코그룹 뉴스룸 커튼월 설명", "https://newsroom.posco.com/kr/글로벌-포스코의-시너지-대륙의-빌딩도-빛낸다/",
            "건축 과학", CuratorVisual.GLASS, "glass",
            PlaceActivity("건물 옷 디자이너", "뼈대와 겉옷 중 무엇부터 살펴볼까?", listOf(
                PlayChoice("skeleton", "안쪽 뼈대 보기", "유리 겉옷을 살짝 비켜 보니 안쪽 뼈대가 드러났어. 건물을 버티는 부분과 감싸는 부분을 나눠 봤네!", cues = listOf("뼈대", "안쪽")),
                PlayChoice("coat", "유리 겉옷 입히기", "뼈대 둘레에 유리 겉옷을 입혔어. 커튼월은 건물 전체를 떠받치는 뼈대와 다른 역할이라는 걸 기억해 줘.", cues = listOf("겉옷", "유리"))))),
        CuratorPlace("yeouido", "여의도", "모래섬의 시간 여행", "지금 빌딩이 많은 이곳은 옛날에도 같은 모습이었을까?",
            listOf("모래섬이었을까?", "비행장이 있었을까?"), "강과 섬의 가장자리, 건물과 나무가 있는 공간을 살펴봐.",
            "시간을 뒤로 돌려 보자! 여의도는 원래 한강의 넓은 모래섬이었어. 비행장으로 쓰인 시기도 있었고, 나중에 섬 둘레에 둑을 쌓고 땅을 넓히며 도시가 자랐어. 강이 없던 곳에 갑자기 섬을 만든 건 아니야. 자연의 섬과 사람들의 변화가 겹쳐 지금 모습이 된 거지.",
            "서울역사박물관 여의도 전시 / 서울기록원", "https://museum.seoul.go.kr/www/board/NR_boardView.do?bbsCd=1002&seq=20210419212409075&sso=ok",
            "도시와 자연", CuratorVisual.ISLAND, "island",
            PlaceActivity("미래의 섬 설계자", "여기부터는 우리의 상상이야. 섬에 어떤 쉼터를 더하고 싶어?", listOf(
                PlayChoice("park", "나무 그늘 공원", "네가 상상한 나무 그늘이 모형 속 섬에 생겼어. 도시를 만들 때 사람이 쉴 자리도 생각해 본 거야.", cues = listOf("나무", "그늘", "공원")),
                PlayChoice("birds", "새들이 쉬는 물가", "모형 속 물가에 새들이 쉴 자리를 남겼어. 도시와 자연이 함께 있는 미래를 상상했네!", cues = listOf("새들", "물가", "새가"))))),
        CuratorPlace("sungnyemun", "숭례문", "도시의 커다란 현관문", "옛날에는 왜 도시에도 커다란 문이 필요했을까?",
            listOf("도시를 지키려고", "사람들이 드나들려고"), "큰 지붕 아래 둥근 통로를 찾아볼래?",
            "옛날 서울에도 잠자는 시간이 있었대! 한양도성 남쪽 문인 숭례문은 밤에 닫고 새벽에 다시 열었어. 도시 전체에 커다란 현관문이 있었던 셈이지. 돌로 쌓은 통로 위에는 두 층 건물도 있어.",
            "국가유산청 궁능유적본부", "https://royal.khs.go.kr/ROYAL/contents/R108010000.do",
            activity = PlaceActivity("아침 문지기", "문을 열면서 어떤 인사를 해 줄래?", listOf(
                PlayChoice("welcome", "좋은 아침! 들어오세요!", "네 인사와 함께 모형 속 문이 열렸어. 네가 도시의 아침을 열어 준 문지기가 됐네!", cues = listOf("좋은아침", "들어오", "환영")),
                PlayChoice("greet", "오늘도 즐겁게 다녀오세요!", "문을 지나가는 사람들에게 네 응원이 전해졌어. 이건 우리가 만든 상상 속 아침이야.", cues = listOf("다녀오", "즐겁", "문뒤"))))),
        CuratorPlace("gwanghwamun", "광화문", "문 뒤의 또 다른 세상", "광화문 뒤에는 어떤 곳이 있을 것 같아?",
            listOf("궁궐이 있을 것 같아", "시장이 있을 것 같아"), "큰 문 뒤로 이어지는 공간을 살펴봐.",
            "문을 하나 지나면 또 다른 세상이 펼쳐져! 광화문은 조선의 궁궐인 경복궁으로 들어가는 정문이야. 숭례문이 도시의 문이라면 광화문은 궁궐의 문이야. 문도 어디로 들어가느냐에 따라 역할이 다르지.",
            "정부 정책브리핑 궁궐 이야기", "https://www.korea.kr/goNewsRes/attaches/2019.05/23/107_20170109_01IozeNoYDGJM000.pdf",
            activity = PlaceActivity("궁궐 안내자", "처음 온 손님에게 한마디 해 줄래?", listOf(
                PlayChoice("welcome", "궁궐에 오신 걸 환영해요!", "네 안내와 함께 모형 속 궁궐 문이 열렸어. 광화문이 궁궐로 들어가는 문이라는 이야기를 전했네!", cues = listOf("좋은아침", "들어오", "환영")),
                PlayChoice("greet", "문 뒤에는 어떤 곳이 있을까요?", "손님에게 네가 품었던 궁금증을 건넸어. 다음 사람이 발견할 이야기를 열어 준 안내자야.", cues = listOf("다녀오", "즐겁", "문뒤"))))),
        CuratorPlace("cheongjinok", "청진옥", "한 그릇에 이어진 시간", "가게가 오래 이어지려면 무엇을 다음 사람에게 전해야 할까?",
            listOf("맛과 만드는 방법", "손님에게 건네는 마음"), "골목 안 가게라 차에서는 보이지 않을 수 있어. 지금은 이 동네의 이야기를 들어 보자.",
            "국자 하나가 시간을 건너간다면? 서울미래유산에는 청진옥이 1937년에 문을 열고 3대에 걸쳐 가업을 이어 온 해장국집으로 소개돼 있어. 오래된 가게에는 음식뿐 아니라 만드는 방법과 동네 사람들의 기억도 함께 쌓이는 거야.",
            "서울미래유산 청진옥 기록", "https://futureheritage.seoul.go.kr/futureHeritageMeet/futureHeritage/view.do?htNo=135",
            "동네의 생활사", CuratorVisual.RECIPE, "recipe",
            PlaceActivity("가게의 이야기 전달자", "다음 사람에게 무엇을 전해 줄래?", listOf(
                PlayChoice("recipe", "맛의 기억을 전할래", "모형 속 국자가 다음 사람에게 건너갔어. 네가 가게의 맛과 이야기를 잇는 사람이 됐네. 실제 조리법을 재현한 건 아니야.", cues = listOf("맛", "음식", "국자")),
                PlayChoice("kindness", "따뜻한 인사를 전할래", "모형 속 가게에 네 따뜻한 인사가 더해졌어. 오래 이어지는 가게에 어떤 마음이 담길지 상상했네!", cues = listOf("인사", "따뜻")))))
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
