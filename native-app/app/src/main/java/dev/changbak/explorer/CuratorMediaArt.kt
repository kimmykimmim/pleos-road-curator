package dev.changbak.explorer

import android.graphics.*
import dev.changbak.explorer.domain.CuratorVisual
import kotlin.math.*

/** Original educational artwork, shared by local MP4 generation and the interactive scene. Not an engineering simulation. */
object CuratorMediaArt {
    private val ink=Color.rgb(32,57,68)
    private val teal=Color.rgb(39,116,122)
    private val gold=Color.rgb(238,181,83)
    private val muted=Color.rgb(99,123,134)
    fun draw(canvas: Canvas,w:Float,h:Float,kind:CuratorVisual,progress:Float,variant:String="") {
        val c=canvas; val p=Paint(Paint.ANTI_ALIAS_FLAG); val t=progress.coerceIn(0f,1f)
        c.drawColor(Color.rgb(235,244,241))
        c.save();val scale=min(w/960f,h/540f);c.translate((w-960f*scale)/2,(h-540f*scale)/2);c.scale(scale,scale)
        fun text(value:String,x:Float,y:Float,size:Float=24f,color:Int=ink,bold:Boolean=false) {
            p.shader=null;p.style=Paint.Style.FILL;p.color=color;p.textSize=size;p.typeface=Typeface.create("sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL);p.textAlign=Paint.Align.LEFT;c.drawText(value,x,y,p)
        }
        fun rect(l:Float,top:Float,r:Float,b:Float,color:Int,radius:Float=12f) { p.style=Paint.Style.FILL;p.color=color;c.drawRoundRect(l,top,r,b,radius,radius,p) }
        fun line(x:Float,y:Float,x2:Float,y2:Float,color:Int=teal,width:Float=7f) {p.style=Paint.Style.STROKE;p.strokeWidth=width;p.strokeCap=Paint.Cap.ROUND;p.color=color;c.drawLine(x,y,x2,y2,p);p.style=Paint.Style.FILL}
        fun arrow(x:Float,y:Float,x2:Float,y2:Float,color:Int=teal) {
            line(x,y,x2,y2,color,5f);val a=atan2(y2-y,x2-x);val s=15f
            line(x2,y2,x2-cos(a-.5f)*s,y2-sin(a-.5f)*s,color,5f);line(x2,y2,x2-cos(a+.5f)*s,y2-sin(a+.5f)*s,color,5f)
        }
        fun dot(x:Float,y:Float,r:Float,color:Int) {p.color=color;c.drawCircle(x,y,r,p)}
        fun chip(value:String,x:Float,y:Float,width:Float=220f) {rect(x,y-28,x+width,y+13,Color.WHITE,16f);text(value,x+14,y,21f,teal,true)}
        val titles=when(kind) {
            CuratorVisual.TOWER -> "높은 건물 안의 힘 나누기 팀"
            CuratorVisual.GLASS -> "건물의 뼈대와 유리 겉옷"
            CuratorVisual.ISLAND -> "모래섬에서 오늘의 도시까지"
            CuratorVisual.RECIPE -> "사람에서 사람으로 건너가는 이야기"
            CuratorVisual.GATE -> "도시의 아침을 여는 문"
            CuratorVisual.FAMILY -> "우리 가족이 함께 왔던 곳"
        }
        text(titles,42f,56f,32f,ink,true)
        // A guardian place is a memory, not a mechanism, so it is not labelled as a model of one.
        text(if(kind==CuratorVisual.FAMILY) "보호자가 적어 둔 기억을 담은 표시"
             else "원리를 간단하게 그린 설명용 모형",42f,89f,20f,muted)
        when(kind) {
            CuratorVisual.TOWER -> {
                rect(90f,430f,530f,450f,Color.rgb(200,216,210))
                val silhouette=Path().apply {moveTo(225f,427f);lineTo(250f,184f);quadTo(277f,140f,309f,120f);quadTo(340f,140f,364f,184f);lineTo(394f,427f);close()}
                p.color=Color.rgb(183,209,215);c.drawPath(silhouette,p)
                val reveal=((t-.15f)/.4f).coerceIn(0f,1f)
                c.saveLayerAlpha(0f,0f,960f,540f,(255*reveal).toInt())
                rect(298f,175f,322f,427f,teal,4f)
                line(245f,250f,245f,427f,ink,12f);line(375f,250f,375f,427f,ink,12f)
                for(y in listOf(267f,345f)) {
                    val connect=((t-.4f)/.3f).coerceIn(0f,1f)
                    val highlight=if(variant=="core") gold else Color.rgb(222,160,77)
                    line(310f,y,310f+(245f-310f)*connect,y+26*connect,highlight,9f)
                    line(310f,y,310f+(375f-310f)*connect,y+26*connect,highlight,9f)
                    line(245f,y+26,245f+(375f-245f)*connect,y+26,if(variant=="belt") gold else teal,7f)
                }
                c.restore()
                val windX=90f+22*sin(t*PI*4).toFloat()
                for(y in listOf(222f,265f,308f)) arrow(windX,y,196f,y,Color.rgb(105,164,188))
                chip("중심 몸통",555f,191f)
                arrow(548f,185f,329f,211f)
                chip("바깥의 큰 기둥",555f,262f)
                arrow(548f,256f,382f,328f)
                chip(if(t<.5f) "연결하는 팔은?" else "아웃리거 = 연결하는 팔",555f,343f,345f)
                if(t>.45f) arrow(548f,337f,344f,286f,gold)
                text("중심과 바깥 기둥이 함께 힘을 나눠요",155f,491f,26f,teal,true)
            }
            CuratorVisual.GLASS -> {
                rect(140f,425f,520f,444f,Color.rgb(200,216,210))
                val split=if(variant=="coat") 0f else if(variant=="skeleton") 1f else ((t-.25f)/.55f).coerceIn(0f,1f)
                val left=215f;val right=390f
                for(x in listOf(left,272f,332f,right)) line(x,155f,x,425f,ink,12f)
                for(y in 0..7) line(left,162f+y*36,right,162f+y*36,ink,6f)
                val offset=split*140f
                c.saveLayerAlpha(0f,0f,960f,540f,205)
                rect(left+offset-12,144f,right+offset+12,425f,Color.rgb(223,178,85),8f)
                for(i in 0..5) line(left+offset-9+i*38,151f,left+offset-9+i*38,418f,Color.rgb(248,226,165),2f)
                for(i in 0..9) line(left+offset-10,151f+i*29,right+offset+10,151f+i*29,Color.rgb(248,226,165),2f)
                c.restore()
                chip("안쪽 뼈대",620f,220f,240f);arrow(609f,212f,274f,244f)
                chip("바깥 유리벽",620f,323f,240f);arrow(609f,316f,right+offset,338f,gold)
                text("커튼월 = 건물 바깥을 감싸는 벽",175f,491f,26f,teal,true)
            }
            CuratorVisual.ISLAND -> {
                rect(85f,136f,875f,424f,Color.rgb(184,217,229),38f)
                for(i in 0..6) line(108f+i*115,175f,160f+i*115,175f,Color.rgb(219,239,241),3f)
                val island=RectF(220f-t*25,202f-t*12,736f+t*25,359f+t*12)
                p.color=Color.rgb(229,207,156);c.drawOval(island,p)
                if(t>.2f) {
                    val alpha=((t-.2f)/.2f).coerceIn(0f,1f)
                    p.color=Color.argb((220*alpha).toInt(),80,131,127);p.style=Paint.Style.STROKE;p.strokeWidth=13f;c.drawOval(island,p);p.style=Paint.Style.FILL
                }
                if(t>.45f) {
                    val grow=((t-.45f)/.4f).coerceIn(0f,1f)
                    for(i in 0..5) {val x=300f+i*60;val ht=(55+i%3*23)*grow;rect(x,290-ht,x+35,300f,if(i%2==0)teal else Color.rgb(153,176,179),5f)}
                }
                if(variant=="park") {
                    for(i in 0..4) {val x=300f+i*70;line(x,315f,x,346f,muted,5f);dot(x,311f,17f,Color.rgb(90,161,113))}
                    chip("네가 더한 나무 그늘",565f,388f,280f)
                } else if(variant=="birds") {
                    for(i in 0..3) {val x=293f+i*85;line(x-12,353f,x,361f,ink,4f);line(x,361f,x+12,353f,ink,4f)}
                    chip("네가 남긴 새들의 쉼터",525f,388f,320f)
                }
                chip(if(t<.25f) "원래 있던 모래섬" else if(t<.6f) "섬 둘레에 쌓은 둑" else "땅을 넓히며 자란 도시",95f,128f,335f)
                text("자연의 섬 위에 사람들의 변화가 더해졌어요",155f,491f,26f,teal,true)
            }
            CuratorVisual.RECIPE -> {
                val positions=listOf(230f,480f,730f)
                for((index,x) in positions.withIndex()) {
                    dot(x,224f,32f,Color.rgb(230,191,154))
                    rect(x-49,266f,x+49,367f,if(index==2)teal else Color.rgb(119,163,157),24f)
                    rect(x-30,285f,x+30,363f,Color.rgb(251,242,215),12f)
                    text("${index+1}대",x-20,409f,24f,ink,true)
                }
                for(i in 0..1) arrow(297f+i*250,300f,407f+i*250,300f,gold)
                val ladleX=230f+500f*t
                line(ladleX,276f,ladleX+26,324f,Color.rgb(112,123,132),7f);dot(ladleX+29,331f,14f,Color.rgb(112,123,132))
                chip("서울미래유산에 기록된 3대 이야기",247f,145f,470f)
                text(if(variant=="kindness") "다음 사람에게 따뜻한 인사를!" else "음식과 함께 동네의 기억도 이어져요",180f,491f,26f,teal,true)
            }
            CuratorVisual.GATE -> {
                rect(220f,310f,740f,425f,Color.rgb(188,180,160),7f)
                p.color=Color.rgb(245,240,221);c.drawRoundRect(431f,334f,529f,429f,49f,49f,p)
                for(level in 0..1) {
                    val y=290f-level*84
                    rect(300f,y-47,660f,y,Color.rgb(127,103,84),3f)
                    for(i in 0..6) line(315f+i*52,y-42,315f+i*52,y,teal,8f)
                    val roof=Path().apply { moveTo(175f,y);quadTo(296f,y-6,340f,y-68);lineTo(620f,y-68);quadTo(665f,y-6,785f,y);quadTo(480f,y+28,175f,y) }
                    p.color=ink;c.drawPath(roof,p)
                }
                val open=if(variant.isNotBlank()) 1f else t
                rect(432f,345f,480f-46*open,425f,Color.rgb(148,113,83),2f)
                rect(480f+46*open,345f,528f,425f,Color.rgb(148,113,83),2f)
                dot(782f,163f,30f,gold)
                text(if(variant=="greet") "오늘도 즐겁게 다녀오세요!" else "좋은 아침! 문을 열어요",282f,491f,28f,teal,true)
            }
            CuratorVisual.FAMILY -> {
                // Deliberately not a building. Drawing one would be inventing what this place
                // looks like, and the only true thing the app holds here is the guardian's line.
                rect(250f,196f,710f,430f,Color.WHITE,28f)
                rect(250f,196f,710f,252f,Color.rgb(119,163,157),28f)
                rect(250f,236f,710f,252f,Color.rgb(119,163,157),0f)
                for(i in 0..2) rect(296f,296f+i*44f,664f-i*96f,316f+i*44f,Color.rgb(222,234,230),10f)
                val beat=1f+0.08f*sin(t*2f*PI.toFloat())
                val heart=Path().apply {
                    val cx=480f;val cy=404f;val s=34f*beat
                    moveTo(cx,cy+s*0.9f)
                    cubicTo(cx-s*1.6f,cy-s*0.2f,cx-s*0.7f,cy-s*1.2f,cx,cy-s*0.3f)
                    cubicTo(cx+s*0.7f,cy-s*1.2f,cx+s*1.6f,cy-s*0.2f,cx,cy+s*0.9f)
                    close()
                }
                p.color=gold;c.drawPath(heart,p)
                dot(292f,224f,11f,Color.rgb(251,242,215))
                text("오늘 이 길에서 다시 만났어요",286f,491f,26f,teal,true)
            }
        }
        if(variant=="own") chip("네가 더한 상상",635f,126f,260f)
        c.restore()
    }
}
