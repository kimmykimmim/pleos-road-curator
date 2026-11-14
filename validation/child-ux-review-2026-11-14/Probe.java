import dev.changbak.explorer.domain.*; import java.util.*;
public class Probe { public static void main(String[] args) {
for (String said : List.of("stop", "again", "next", "I see it", "다음에 또 보고 싶어", "다시 지으면 좋겠어"))
System.out.println(said+" => "+SpokenReply.INSTANCE.of(said,List.of()));
CuratorPlace p=CuratorCatalog.INSTANCE.places(ChildLanguage.ENGLISH).get(0);
System.out.println("I don't know => "+VoiceTalk.INSTANCE.reply(p,"I don't know",1));
System.out.println("wind => "+VoiceTalk.INSTANCE.reply(p,"wind",1));
System.out.println("Wind => "+VoiceTalk.INSTANCE.reply(p,"Wind",1));
}}