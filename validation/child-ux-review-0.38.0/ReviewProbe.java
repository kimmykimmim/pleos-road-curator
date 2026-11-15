import dev.changbak.explorer.domain.*;
import java.util.*;
public class ReviewProbe {
 public static void main(String[] args) {
  for (String s:List.of("그만", "그만해", "쉬고 싶어", "잠깐만", "잠깐 쉴래", "그만 듣고 싶어", "다음에 또 보고 싶어"))
   System.out.println("KO "+s+" => "+SpokenReply.INSTANCE.of(s,List.of(),ChildLanguage.KOREAN));
  for (String s:List.of("stop","I want a break","I don't know"))
   System.out.println("EN "+s+" => "+SpokenReply.INSTANCE.of(s,List.of(),ChildLanguage.ENGLISH));
  var places=CuratorCatalog.INSTANCE.places(ChildLanguage.KOREAN);
  var p=places.stream().filter(x->x.getId().equals("sungnyemun")).findFirst().get();
  var e=new RouteCurator(places);
  var fix=new CuratorFix("review",1L,List.of(new PlaceApproach("sungnyemun",300,false,true,false,false,WindowSide.UNKNOWN)),true);
  var s=e.tick(e.start(),fix).getState();
  for(String input:List.of("모르겠어","공룡이 문을 지켜")) {
   var reply=VoiceTalk.INSTANCE.reply(p,input,1,ChildLanguage.KOREAN,InterestField.HISTORY);
   var next=e.heard(s,input,1).getState();
   System.out.println(input+" => reply="+reply+"; engine.expectsAnswer="+e.expectsAnswer(next,1));
  }
 }
}
