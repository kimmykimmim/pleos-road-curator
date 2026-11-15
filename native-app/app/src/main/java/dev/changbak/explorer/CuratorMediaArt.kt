package dev.changbak.explorer

import android.graphics.*
import dev.changbak.explorer.domain.CuratorVisual
import dev.changbak.explorer.domain.SceneScrap
import dev.changbak.explorer.domain.SceneScraps
import kotlin.math.*

/**
 * The explanation, made of real photographs.
 *
 * This used to build each idea out of rectangles: a gate out of four of them, three
 * generations of a family out of circles on boxes. A model of a thing is something an adult
 * reads; a child in the back seat needs the thing. So every scene here is now a clipping — a
 * licensed photograph laid on the page the way a cutting is stuck into a scrapbook, with its
 * own caption and the name of whoever took it underneath.
 *
 * Nothing is drawn on top of a photograph except where the words need somewhere pointed at: a
 * ring around the middle of a tower that is still being built, a ring around a shop sign. The
 * photographs themselves are untouched, and what is listed in [SceneScraps] is what is shown.
 *
 * 가족 장소 is the one scene with no photograph, and keeps its paper card. The app has no
 * picture of a family's own place, and borrowing somebody else's would be inventing their
 * memory for them.
 */
object CuratorMediaArt {
    private val ink=Color.rgb(11,45,63)
    private val teal=Color.rgb(8,98,136)
    private val gold=Color.rgb(224,180,92)
    private val muted=Color.rgb(104,130,151)
    fun draw(canvas: Canvas,w:Float,h:Float,kind:CuratorVisual,progress:Float,variant:String="",
             placeId:String?=null) {
        val c=canvas; val p=Paint(Paint.ANTI_ALIAS_FLAG); val t=progress.coerceIn(0f,1f)
        c.drawColor(Color.rgb(238,246,252))
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
        // Sized by what is written on it. Every chip used to be handed a width by hand, so a
        // short label sat in a long white bar with nothing in the rest of it.
        fun chip(value:String,x:Float,y:Float,width:Float=0f) {
            p.textSize=21f;p.typeface=Typeface.create("sans-serif",Typeface.BOLD)
            val span=max(width,p.measureText(value)+28f)
            rect(x,y-28,x+span,y+13,Color.WHITE,16f);text(value,x+14,y,21f,teal,true)
        }
        /**
         * A line of text centred on the page.
         *
         * Every scene used to place its closing line at a hand-found x, which was right for the
         * drawing that used to be under it and wrong for everything since: the line sat off to
         * one side of a centred photograph.
         */
        fun middle(value:String,y:Float,size:Float=26f,color:Int=teal) {
            p.textSize=size;p.typeface=Typeface.create("sans-serif",Typeface.BOLD)
            text(value,480f-p.measureText(value)/2f,y,size,color,true)
        }
        /** The same chip, hung from a point rather than from its left edge. */
        fun chipMid(value:String,middle:Float,y:Float) {
            p.textSize=21f;p.typeface=Typeface.create("sans-serif",Typeface.BOLD)
            chip(value,middle-(p.measureText(value)+28f)/2f,y)
        }
        // The place names its own explanation. Two places that share a kind of story — a city
        // gate and a palace gate — are not the same story and must not be headed as one.
        val titles=SceneScraps.titleFor(placeId) ?: when(kind) {
            CuratorVisual.TOWER -> "높은 건물 안의 힘 나누기 팀"
            CuratorVisual.GLASS -> "건물의 뼈대와 유리 겉옷"
            CuratorVisual.ISLAND -> "모래섬에서 오늘의 도시까지"
            CuratorVisual.RECIPE -> "사람에서 사람으로 건너가는 이야기"
            CuratorVisual.GATE -> "도시의 문"
            CuratorVisual.FAMILY -> "우리 가족이 함께 왔던 곳"
        }
        text(titles,42f,56f,32f,ink,true)
        if(kind==CuratorVisual.FAMILY) text("보호자가 적어 둔 기억을 담은 표시",42f,89f,20f,muted)

        /**
         * One clipping: the photograph, the white paper it is stuck on, its caption, and the
         * name of whoever took it.
         *
         * The paper is torn along the bottom and sits at a slight angle, because that is what
         * a cutting looks like and because two clippings at the same angle read as a form.
         */
        fun clipping(scrap: SceneScrap,cx:Float,cy:Float,photoW:Float,photoH:Float,tilt:Float,
                     chosen:Boolean=false,faded:Boolean=false) {
            if(faded) c.saveLayerAlpha(0f,0f,960f,540f,96)
            val photo=ScenePhotos.of(scrap.asset)
            val pad=13f; val band=if(scrap.credit.isBlank()) 34f else 60f
            val paperW=photoW+pad*2; val paperH=photoH+pad*2+band
            c.save();c.translate(cx,cy);c.rotate(tilt)
            val left=-paperW/2; val top=-paperH/2
            // The paper, torn along its bottom edge.
            val paper=Path().apply {
                moveTo(left,top);lineTo(left+paperW,top);lineTo(left+paperW,top+paperH-9f)
                var x=left+paperW
                var step=0
                while(x>left) {
                    val dip=if(step%2==0) 9f else 0f
                    x-=paperW/13f
                    lineTo(max(x,left),top+paperH-9f+dip)
                    step++
                }
                lineTo(left,top);close()
            }
            p.style=Paint.Style.FILL
            // Its shadow, so it sits on the page rather than in it.
            c.save();c.translate(3f,6f);p.color=Color.argb(38,11,45,63);c.drawPath(paper,p);c.restore()
            p.color=Color.WHITE;c.drawPath(paper,p)
            val box=RectF(left+pad,top+pad,left+pad+photoW,top+pad+photoH)
            if(photo!=null) {
                // Centre-crop towards whatever the scrap says must not be lost.
                val want=photoW/photoH
                val have=photo.width.toFloat()/photo.height
                val srcW=if(have>want) photo.height*want else photo.width.toFloat()
                val srcH=if(have>want) photo.height.toFloat() else photo.width/want
                val srcX=(scrap.focusX*photo.width-srcW/2).coerceIn(0f,photo.width-srcW)
                val srcY=(scrap.focusY*photo.height-srcH/2).coerceIn(0f,photo.height-srcH)
                val src=Rect(srcX.toInt(),srcY.toInt(),(srcX+srcW).toInt(),(srcY+srcH).toInt())
                c.drawBitmap(photo,src,box,p)
                // Where to look, if this photograph has somewhere.
                scrap.mark?.let { mark ->
                    val mx=box.left+(mark.x*photo.width-srcX)/srcW*photoW
                    val my=box.top+(mark.y*photo.height-srcY)/srcH*photoH
                    val r=(mark.r*photo.width)/srcW*photoW*(1f+.06f*sin(t*2f*PI).toFloat())
                    if(box.contains(mx,my)) {
                        p.style=Paint.Style.STROKE;p.strokeWidth=5f;p.color=gold
                        c.drawCircle(mx,my,r,p);p.style=Paint.Style.FILL
                        if(mark.label.isNotBlank()) {
                            p.textSize=19f;p.typeface=Typeface.create("sans-serif",Typeface.BOLD)
                            val span=p.measureText(mark.label)+22f
                            val labelY=(my+r+26f).coerceAtMost(box.bottom-10f)
                            rect(mx-span/2,labelY-21f,mx+span/2,labelY+7f,Color.argb(232,255,255,255),13f)
                            text(mark.label,mx-span/2+11f,labelY,19f,teal,true)
                        }
                    }
                }
            } else {
                rect(box.left,box.top,box.right,box.bottom,Color.rgb(226,236,245),6f)
            }
            p.style=Paint.Style.STROKE
            // The one the child picked is outlined, so their answer is visible on the page.
            p.strokeWidth=if(chosen) 5f else 1.5f
            p.color=if(chosen) gold else Color.rgb(205,219,232)
            c.drawRect(box,p);p.style=Paint.Style.FILL
            // The caption, kept to the width of the paper it is written on.
            p.textSize=19f;p.typeface=Typeface.create("sans-serif",Typeface.BOLD)
            var caption=scrap.caption
            while(p.measureText(caption)>photoW&&caption.length>4) caption=caption.dropLast(1)
            if(caption!=scrap.caption) caption=caption.dropLast(1)+"…"
            text(caption,box.left,box.bottom+28f,19f,ink,true)
            if(scrap.credit.isNotBlank()) {
                p.textSize=13f;p.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
                var credit=scrap.credit
                while(p.measureText(credit)>photoW&&credit.length>4) credit=credit.dropLast(1)
                text(credit,box.left,box.bottom+50f,13f,muted)
            }
            c.restore()
            if(faded) c.restore()
        }

        /**
         * The clippings of this scene, laid out for however many there are.
         *
         * @param picked which clipping the child's role-play answer chose, or -1. The chosen
         *   one is outlined and the other steps back, so the answer changes the page.
         */
        fun clippings(kind:CuratorVisual,picked:Int = -1) {
            val scraps=SceneScraps.of(placeId)
            // A gentle settle, so the cuttings look placed rather than printed.
            val sway=sin(t*2f*PI).toFloat()
            when(scraps.size) {
                0 -> {}
                1 -> clipping(scraps[0],480f,278f,470f,236f,-1.6f+sway*.8f,picked==0)
                else -> {
                    clipping(scraps[0],272f,274f,330f,196f,-3.4f+sway*.7f,picked==0,picked==1)
                    clipping(scraps[1],690f,282f,330f,196f,3.1f-sway*.7f,picked==1,picked==0)
                    // Between them: this one became that one.
                    arrow(470f,278f,500f,278f,gold)
                }
            }
        }

        when(kind) {
            CuratorVisual.TOWER -> {
                clippings(kind)
                middle(if(variant=="belt") "바깥 기둥끼리도 서로 이어져 있어요"
                       else "중심과 바깥 기둥이 함께 힘을 나눠요",497f)
            }
            CuratorVisual.GLASS -> {
                // The child's answer picks which of the two the page is about.
                clippings(kind,when(variant) { "skeleton" -> 0; "coat" -> 1; else -> -1 })
                middle(when(variant) {
                    "skeleton" -> "뼈대가 건물을 떠받치고 있어요"
                    "coat" -> "유리 겉옷은 바깥에 걸려 있어요"
                    else -> "커튼월 = 건물 바깥을 감싸는 벽"
                },497f)
            }
            CuratorVisual.ISLAND -> {
                clippings(kind)
                middle(when(variant) {
                    "park" -> "네가 더한 나무 그늘까지 이 섬의 이야기예요"
                    "birds" -> "네가 남긴 새들의 쉼터까지 이 섬의 이야기예요"
                    else -> "자연의 섬 위에 사람들의 변화가 더해졌어요"
                },497f)
            }
            CuratorVisual.RECIPE -> {
                clippings(kind)
                middle(if(variant=="kindness") "다음 사람에게 따뜻한 인사를!"
                       else "음식과 함께 동네의 기억도 이어져요",497f)
            }
            CuratorVisual.GATE -> {
                clippings(kind)
                middle(if(variant=="greet") "오늘도 즐겁게 다녀오세요!" else "좋은 아침! 문을 열어요",497f,28f)
            }
            CuratorVisual.FAMILY -> {
                // Deliberately not a building, and deliberately not a photograph. Drawing one
                // would be inventing what this place looks like, and the only true thing the
                // app holds here is the guardian's line.
                rect(250f,196f,710f,430f,Color.WHITE,28f)
                rect(250f,196f,710f,252f,Color.rgb(77,141,186),28f)
                rect(250f,236f,710f,252f,Color.rgb(77,141,186),0f)
                for(i in 0..2) rect(296f,296f+i*44f,664f-i*96f,316f+i*44f,Color.rgb(226,240,250),10f)
                val beat=1f+0.08f*sin(t*2f*PI.toFloat())
                val heart=Path().apply {
                    val cx=480f;val cy=404f;val s=34f*beat
                    moveTo(cx,cy+s*0.9f)
                    cubicTo(cx-s*1.6f,cy-s*0.2f,cx-s*0.7f,cy-s*1.2f,cx,cy-s*0.3f)
                    cubicTo(cx+s*0.7f,cy-s*1.2f,cx+s*1.6f,cy-s*0.2f,cx,cy+s*0.9f)
                    close()
                }
                p.color=gold;c.drawPath(heart,p)
                dot(292f,224f,11f,Color.rgb(255,245,219))
                middle("오늘 이 길에서 다시 만났어요",497f)
            }
        }
        if(variant=="own") chipMid("네가 더한 상상",760f,126f)
        c.restore()
    }
}
