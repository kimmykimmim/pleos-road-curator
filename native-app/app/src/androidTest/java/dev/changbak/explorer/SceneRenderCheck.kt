package dev.changbak.explorer

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Bundle
import dev.changbak.explorer.domain.TravelEngine
import dev.changbak.explorer.domain.TravelState
import dev.changbak.explorer.domain.*
import java.io.File
import java.nio.ByteBuffer

/** Renders synthetic art to an offscreen buffer. Does not capture or operate the user's screen. */
class SceneRenderCheck : Instrumentation() {
    private var requestedCheck: String? = null
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        requestedCheck = arguments?.getString("check")
        start()
    }

    override fun onStart() {
        val report = Bundle()
        try {
            if(requestedCheck=="navi-connection") {
                report.putString("result",NaviConnectionCheck.run(targetContext));finish(Activity.RESULT_OK,report);return
            }
            if (requestedCheck == "curator-media-build") {
                exportCuratorMedia(report); finish(Activity.RESULT_OK, report); return
            }
            if (requestedCheck == "curator-media") {
                checkCuratorMedia(report); finish(Activity.RESULT_OK, report); return
            }
            if (requestedCheck == "stickers") {
                checkStickers(report); finish(Activity.RESULT_OK, report); return
            }
            if (requestedCheck == "curator") {
                checkCurator(report); finish(Activity.RESULT_OK, report); return
            }
            if (requestedCheck == "journey") {
                checkJourney(report); finish(Activity.RESULT_OK, report); return
            }
            if (requestedCheck == "chat") {
                report.putString("result", FriendChatTransportCheck.run(targetContext))
                finish(Activity.RESULT_OK, report)
                return
            }
            if (requestedCheck == "world") {
                checkCloudWorld(report)
                finish(Activity.RESULT_OK, report)
                return
            }
            if (requestedCheck == "motion") {
                checkMotion(report)
                finish(Activity.RESULT_OK, report)
                return
            }
            val output = File(targetContext.filesDir, "scene-render-check").apply { mkdirs() }
            var sample = TravelEngine.start(TravelEngine.create()).state
            sample = TravelEngine.observe(sample, "구름이 동글동글해").state
            val cloudPixels = render(sample, File(output, "friend-cloud.png"))
            sample = TravelEngine.observe(sample, "나뭇잎이 살랑살랑 움직여").state
            sample = TravelEngine.observe(sample, "강물이 반짝여").state
            val landscapePixels = render(sample, File(output, "friend-landscape.png"))
            sample = TravelEngine.observe(sample, "그거 강이 아니라 바다였어").state
            render(sample, File(output, "friend-sea.png"))
            sample = TravelEngine.observe(sample, "날개 달아줘").state
            render(sample, File(output, "friend-wings.png"))
            val changed = cloudPixels.indices.count { cloudPixels[it] != landscapePixels[it] }
            check(changed > 2500) { "Accumulated scenery did not visibly change the character" }
            val green = landscapePixels.count { c ->
                val r = c shr 16 and 255; val g = c shr 8 and 255; val b = c and 255
                g > r + 12 && g > b + 3
            }
            check(green > 300) { "Leaf ears are missing from the rendered sample" }
            report.putString("result", "PASS: four offscreen 3D renders, scenery changes, and leaf ears")
            report.putString("artifacts", output.absolutePath)
            report.putInt("changed_pixels", changed)
            report.putInt("leaf_pixels", green)
            finish(Activity.RESULT_OK, report)
        } catch (error: Throwable) {
            report.putString("result", "FAIL: ${error.javaClass.simpleName}: ${error.message}")
            finish(Activity.RESULT_CANCELED, report)
        }
    }

    private fun exportCuratorMedia(report:Bundle) {
        val output=File(targetContext.filesDir,"curator-media-build").apply { mkdirs() }
        val types=mapOf("tower" to CuratorVisual.TOWER,"glass" to CuratorVisual.GLASS,"island" to CuratorVisual.ISLAND,"recipe" to CuratorVisual.RECIPE,"gate" to CuratorVisual.GATE)
        types.forEach { (key,kind) ->
            val frames=File(output,key).apply { mkdirs() }
            val bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(bitmap)
            for(index in 0..143) {
                CuratorMediaArt.draw(canvas,960f,540f,kind,index/143f)
                File(frames,"frame-%03d.png".format(index)).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            }
            bitmap.recycle()
        }
        report.putString("result","PASS: original five-topic motion artwork, 720 production Canvas frames")
        report.putString("artifacts",output.absolutePath)
    }
    private fun checkCuratorMedia(report:Bundle) {
        for(key in listOf("tower","glass","island","recipe","gate")) {
            val id=targetContext.resources.getIdentifier("curator_$key","raw",targetContext.packageName)
            check(id!=0) { "Missing bundled clip $key" }
            val reader=android.media.MediaMetadataRetriever()
            try {
                targetContext.resources.openRawResourceFd(id).use { reader.setDataSource(it.fileDescriptor,it.startOffset,it.length) }
                val duration=reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                check(duration in 5900..6100) { "Unexpected duration $key: $duration" }
                val first=reader.getFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST)!!
                val last=reader.getFrameAtTime(5_750_000,android.media.MediaMetadataRetriever.OPTION_CLOSEST)!!
                check(first.width==960 && first.height==540)
                val a=IntArray(960*540);val b=IntArray(a.size)
                first.getPixels(a,0,960,0,0,960,540);last.getPixels(b,0,960,0,0,960,540)
                check(a.indices.count { a[it]!=b[it] }>500) { "Clip has no visible change: $key" }
                first.recycle();last.recycle()
            } finally {reader.release()}
        }
        val output=File(targetContext.filesDir,"curator-role-check").apply { mkdirs() }
        CuratorCatalog.places.forEach { place ->
            val images=place.activity.choices.take(2).map { choice ->
                val state=CuratorState(phase=CuratorPhase.MEMORY,currentId=place.id,
                    visits=listOf(PlaceMemory(place.id,activityId=choice.id,activityLabel=choice.label)))
                val bitmap=Bitmap.createBitmap(960,600,Bitmap.Config.ARGB_8888)
                CuratorSceneView.drawScene(android.graphics.Canvas(bitmap),960f,600f,state)
                val pixels=IntArray(960*600);bitmap.getPixels(pixels,0,960,0,0,960,600)
                File(output,"${place.id}-${choice.id}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle();pixels
            }
            check(images.size==2 && images[0].indices.count {images[0][it]!=images[1][it]}>80) {"Role choices have no visible difference: ${place.id}"}
        }
        report.putString("result","PASS: five bundled MP4s, six-second duration, 960x540 decoding, visible motion and distinct role scenes at all six places")
        report.putString("artifacts",output.absolutePath)
    }

    /**
     * Every small picture draws something, and no two of them draw the same thing.
     *
     * These are hand-built paths. A typo in one of them leaves an empty square or, worse, a
     * shape that reads as a different animal, and neither shows up anywhere else: the picture
     * appears for three seconds in the corner of a screen nobody is supposed to be staring at.
     * The contact sheet this leaves behind is how they get looked at on purpose.
     */
    private fun checkStickers(report: Bundle) {
        val output = File(targetContext.filesDir, "sticker-check").apply { mkdirs() }
        val side = 240
        val pictures = TopicPicture.entries
        val sheet = Bitmap.createBitmap(side * 4, side * 4, Bitmap.Config.ARGB_8888)
        val sheetCanvas = android.graphics.Canvas(sheet)
        sheetCanvas.drawColor(android.graphics.Color.rgb(150, 168, 182))
        val drawn = ArrayList<IntArray>()
        pictures.forEachIndexed { index, picture ->
            val cell = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(cell)
            canvas.drawColor(android.graphics.Color.rgb(150, 168, 182))
            TopicStickerView.draw(canvas, side.toFloat(), picture)
            val pixels = IntArray(side * side)
            cell.getPixels(pixels, 0, side, 0, 0, side, side)
            check(pixels.distinct().size > 8) { "$picture drew nothing" }
            val inked = pixels.count { it != android.graphics.Color.rgb(150, 168, 182) }
            check(inked > side * side / 12) { "$picture is too thin to read: $inked" }
            drawn.add(pixels)
            sheetCanvas.drawBitmap(cell, (index % 4 * side).toFloat(), (index / 4 * side).toFloat(), null)
            cell.recycle()
        }
        drawn.indices.forEach { a ->
            (a + 1 until drawn.size).forEach { b ->
                val same = drawn[a].indices.count { drawn[a][it] == drawn[b][it] }
                check(same < side * side * 95 / 100) {
                    "${pictures[a]} and ${pictures[b]} look the same"
                }
            }
        }
        // The real clippings too: these are files on disk, and a missing one would quietly
        // fall back to a drawing without anyone noticing it had gone.
        PlaceScrap.assets().forEach { asset ->
            val bytes = targetContext.assets.open(asset).use { it.readBytes() }
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            checkNotNull(bmp) { "$asset did not decode" }
            check(bmp.hasAlpha()) { "$asset has no transparency, so it was never cut out" }
            check(bmp.height >= 200) { "$asset is too small to read: ${bmp.width}x${bmp.height}" }
            // A cut-out is mostly background. One that is fully opaque is the whole photograph.
            val corners = listOf(0 to 0, bmp.width - 1 to 0, 0 to bmp.height - 1,
                bmp.width - 1 to bmp.height - 1)
            check(corners.any { (x, y) -> android.graphics.Color.alpha(bmp.getPixel(x, y)) == 0 }) {
                "$asset keeps its background"
            }
            bmp.recycle()
        }
        // 두리 in each of the things it can be doing, so a face that stopped drawing or
        // stopped changing shows up here rather than in front of a child.
        val faces = CompanionView.Doing.entries.mapIndexed { index, doing ->
            val cell = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(cell)
            canvas.drawColor(android.graphics.Color.rgb(150, 168, 182))
            CompanionView.draw(canvas, side.toFloat(), doing, 1f)
            val pixels = IntArray(side * side)
            cell.getPixels(pixels, 0, side, 0, 0, side, side)
            check(pixels.distinct().size > 8) { "companion $doing drew nothing" }
            sheetCanvas.drawBitmap(cell, (index * side).toFloat(), (3 * side).toFloat(), null)
            cell.recycle()
            pixels
        }
        check(faces[0].indices.count { faces[0][it] != faces[1][it] } > 200) {
            "the companion looks the same resting as it does speaking"
        }
        check(faces[0].indices.count { faces[0][it] != faces[2][it] } > 200) {
            "the companion looks the same resting as it does listening"
        }
        File(output, "stickers.png").outputStream().use {
            check(sheet.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        sheet.recycle()
        report.putString("artifacts", output.absolutePath)
        report.putString("result", "PASS: ${pictures.size} topic pictures each drawn and different, " +
            "${PlaceScrap.assets().size} photo clippings cut out and readable, " +
            "the companion drawn differently in all ${CompanionView.Doing.entries.size} states")
    }

    private fun checkCurator(report: Bundle) {
        val output=File(targetContext.filesDir,"route-curator-check").apply { mkdirs() }
        val engine=RouteCurator(CuratorCatalog.places)
        val start=engine.start()
        val asked=engine.tick(start,CuratorFix("test-route",1,listOf(PlaceApproach("sungnyemun",300)))).state
        val predicted=engine.predict(asked,"사람들이 드나들려고").state
        val near=engine.tick(predicted,CuratorFix("test-route",2,listOf(PlaceApproach("sungnyemun",20)))).state
        val unseen=engine.seen(near,false).state
        val explained=engine.tick(unseen,CuratorFix("test-route",3,listOf(PlaceApproach("sungnyemun",-2,true)))).state
        check(explained.phase==CuratorPhase.EXPLAIN)
        check(engine.memory(explained)!!.seen==SeenStatus.NOT_SEEN)
        val compared=engine.afterExplanation(explained).state
        val remembered=engine.perform(compared,"welcome").state
        check(engine.memory(remembered)!!.guess=="사람들이 드나들려고")
        val stages=listOf(start,asked,near,explained,compared,remembered)
        val samples=ArrayList<IntArray>()
        stages.forEachIndexed { index,state ->
            val bitmap=Bitmap.createBitmap(720,600,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.rgb(226,238,233))
            CuratorSceneView.drawScene(canvas,720f,600f,state)
            val pixels=IntArray(720*600);bitmap.getPixels(pixels,0,720,0,0,720,600)
            check(pixels.distinct().size>10)
            samples.add(pixels)
            File(output,"stage-%02d.png".format(index)).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            bitmap.recycle()
        }
        check(samples[0].indices.count { samples[0][it]!=samples[1][it] }>3000) {
            "The mystery scene and the place's scene look the same"
        }
        // Before and after passing the place. The scene used to carry a labelled step strip as
        // well, which made every phase differ from every other by the strip alone; now that the
        // steps are shown as dots beside the turn, this compares the two states whose artwork
        // actually differs — the place as a thing to look for, and the place as a thing
        // explained. At a gate the role-play pose is almost the same drawing, so comparing
        // against that was measuring the strip rather than the scene.
        check(samples[1].indices.count { samples[1][it]!=samples[3][it] }>1000) {
            "The scene does not change between looking for the place and hearing about it"
        }
        report.putString("result","PASS: five-minute question, prediction, unseen encounter, automatic passage explanation, role play, six production Canvas renders")
        report.putString("artifacts",output.absolutePath)
    }

    private fun checkJourney(report: Bundle) {
        val output = File(targetContext.filesDir, "sky-journey-check").apply { mkdirs() }
        val initial = CloudPlayEngine.observe(CloudPlayEngine.start(CloudPlayEngine.create()).state, "구름이 토끼 같아").state
        fun advance(state: TravelState, distance: Float) = state.copy(cloudWorld = state.cloudWorld!!.copy(
            journey = SkyJourneyEngine.advance(state.cloudWorld!!.journey, distance, JourneySource.DEMO)))
        var state = advance(initial, 180f)
        val startPixels = render(state, File(output, "01-start.png"))
        state = CloudPlayEngine.observe(state, "나무가 엄청 많아").state
        state = advance(state, 300f)
        state = CloudPlayEngine.observe(state, "커다란 다리를 건너고 있어").state
        val roadPixels = render(state, File(output, "02-forest-bridge.png"))
        state = CloudPlayEngine.observe(state, "터널에 들어왔어").state
        val tunnelPixels = render(state, File(output, "03-tunnel.png"))
        state = advance(state, 180f)
        state = CloudPlayEngine.observe(state, "터널에서 나왔어").state
        render(state, File(output, "04-sunlight.png"))
        val end = CloudPlayEngine.finish(state).state
        val islandPixels = render(end, File(output, "05-island.png"))
        check(startPixels.indices.count { startPixels[it] != roadPixels[it] } > 1000)
        check(roadPixels.indices.count { roadPixels[it] != tunnelPixels[it] } > 10000)
        check(roadPixels.indices.count { roadPixels[it] != islandPixels[it] } > 1000)
        for (i in 0..25) render(end, File(output, "morph-%03d.png".format(i)), islandProgress = i/25f)
        val testContext = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                baseContext.getSharedPreferences("sky_journey_validation_only", Context.MODE_PRIVATE)
        }
        val prefs = testContext.getSharedPreferences("unused", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = CloudWorldStore(testContext); store.save(end)
            check(store.restore().cloudWorld!!.journey == end.cloudWorld!!.journey)
            check(store.lastKeepsake()!!.cloudWorld!!.journey == end.cloudWorld!!.journey)
            store.save(CloudPlayEngine.create())
            check(store.lastKeepsake()!!.cloudWorld!!.journey == end.cloudWorld!!.journey)
        } finally { prefs.edit().clear().commit() }
        report.putString("result", "PASS: synthetic movement, forest, bridge, tunnel light, 26 island morph frames, persistence and keepsake")
        report.putString("artifacts", output.absolutePath)
    }

    private fun checkCloudWorld(report: Bundle) {
        val output = File(targetContext.filesDir, "cloud-world-render-check").apply { mkdirs() }
        val empty = CloudPlayEngine.start(CloudPlayEngine.create()).state
        val emptyPixels = render(empty, File(output, "01-empty-sky.png"), expectEmpty = true)
        val rabbit = CloudPlayEngine.observe(empty, "저 구름 토끼 같아").state
        val rabbitPixels = render(rabbit, File(output, "02-rabbit-friend.png"))
        val preview = CloudPlayEngine.observe(rabbit, "저쪽 구름은 고래 같아").state
        val background = CloudPlayEngine.observe(preview, "하늘에 띄우자").state
        render(background, File(output, "03-rabbit-and-whale-cloud.png"))
        val more = CloudPlayEngine.observe(background, "공룡 구름을 친구로 만들어줘").state
        render(more, File(output, "04-two-friends-and-cloud.png"))
        val selected = CloudPlayEngine.select(more, rabbit.cloudWorld!!.activeId!!).state
        render(selected, File(output, "05-rabbit-speaking.png"))
        val backgroundOnly = CloudPlayEngine.observe(empty, "토끼 같은 구름을 하늘에 띄워줘").state
        val backgroundPixels = render(backgroundOnly, File(output, "06-background-without-face.png"))
        fun inkPixels(pixels: IntArray) = pixels.count { c -> (c shr 16 and 255) < 90 && (c shr 8 and 255) < 110 && (c and 255) < 145 }
        check(inkPixels(rabbitPixels) > 50) { "Friend face is missing" }
        check(inkPixels(backgroundPixels) == 0) { "A background cloud was given a face" }
        check(emptyPixels.distinct().size == 1) { "Empty sky contains pre-created geometry" }

        // Isolated test preferences: never reads or resets the user's current sky.
        val testContext = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                baseContext.getSharedPreferences("cloud_world_validation_only", Context.MODE_PRIVATE)
        }
        val preferences = testContext.getSharedPreferences("unused", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val store = CloudWorldStore(testContext)
            check(store.restore().cloudWorld!!.clouds.isEmpty()) { "Fresh store is not empty" }
            val remembered = CloudPlayEngine.remember(more).state.let {
                it.copy(cloudWorld = it.cloudWorld!!.copy(expectation = JourneyExpectation.FAMILY))
            }
            store.save(remembered.copy(cloudWorld = remembered.cloudWorld!!.copy(draft = CloudFriend(CloudForm.HEART, "하트"))))
            val restored = store.restore()
            check(restored.phase == CloudPhase.RESTING && restored.cloudWorld!!.draft == null) { "Listening or pending choice survived restore" }
            check(restored.cloudWorld!!.clouds == more.cloudWorld!!.clouds && restored.body == more.body) { "Restored sky lost roles or selected friend" }
            check(restored.cloudWorld!!.expectation == JourneyExpectation.FAMILY &&
                restored.cloudWorld!!.favoriteId == remembered.cloudWorld!!.favoriteId) { "Journey expectation or chosen memory was lost" }
            store.save(CloudPlayEngine.finish(more).state)
            store.save(CloudPlayEngine.create())
            check(store.restore().cloudWorld!!.clouds.isEmpty()) { "New trip did not reset sky" }
            check(store.lastKeepsake()!!.cloudWorld!!.clouds == more.cloudWorld!!.clouds) { "New trip erased previous keepsake" }
        } finally { preferences.edit().clear().commit() }
        report.putString("result", "PASS: empty sky, friend birth, faceless background, multiple friends, selected friend, isolated save/restore and keepsake")
        report.putString("artifacts", output.absolutePath)
        report.putInt("friend_face_pixels", inkPixels(rabbitPixels))
        report.putInt("background_face_pixels", inkPixels(backgroundPixels))
    }

    private fun checkMotion(report: Bundle) {
        val output = File(targetContext.filesDir, "cloud-motion-check").apply { mkdirs() }
        val empty = CloudPlayEngine.start(CloudPlayEngine.create()).state
        val rabbit = CloudPlayEngine.observe(empty, "저 구름 토끼 같아").state
        val preview = CloudPlayEngine.observe(rabbit, "고래 같아").state
        val friends = CloudPlayEngine.observe(preview, "친구로 만들자").state
        val selected = CloudPlayEngine.select(friends, rabbit.cloudWorld!!.activeId!!).state
        val stages = listOf(empty to rabbit, rabbit to preview, preview to friends, friends to selected)
        var frameIndex = 0
        val contrasts = mutableListOf<Long>()
        fun nextFile() = File(output, "frame-${(frameIndex++).toString().padStart(3, '0')}.png")
        stages.forEachIndexed { stage, (from, to) ->
            var last: File? = null
            for (i in 0..20) {
                val file = nextFile()
                val pixels = render(to, file, stage == 0 && i == 0) { error ->
                    createCloudTransitionRendererForCheck(from, to, i / 20f, error)
                }
                if (stage == 0 && i in listOf(0, 5, 10, 15, 20)) {
                    val bg = pixels[0]
                    contrasts.add(pixels.sumOf { c ->
                        kotlin.math.abs((c shr 16 and 255) - (bg shr 16 and 255)).toLong() +
                            kotlin.math.abs((c shr 8 and 255) - (bg shr 8 and 255)) + kotlin.math.abs((c and 255) - (bg and 255))
                    })
                }
                if (stage == 0 && i == 0) repeat(10) { file.copyTo(nextFile(), overwrite = true) }
                last = file
            }
            repeat(10) { last!!.copyTo(nextFile(), overwrite = true) }
        }
        check(contrasts.first() == 0L) { "Birth starts with a visible avatar" }
        check(contrasts.zipWithNext().all { (before, after) -> after > before }) { "Birth opacity did not progress through intermediate frames" }
        report.putString("result", "PASS: actual GLES intermediate frames for birth fade, preview fade, preview-to-friend face and movement, and speaker exchange")
        report.putString("artifacts", output.absolutePath)
        report.putString("birth_contrast_steps", contrasts.joinToString())
        report.putInt("frames", frameIndex)
    }

    private fun render(state: TravelState, output: File, expectEmpty: Boolean = false,
        islandProgress: Float? = null, factory: (((String) -> Unit) -> android.opengl.GLSurfaceView.Renderer)? = null): IntArray {
        val width = 720
        val height = 600
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL initialization failed" }
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        val attributes = intArrayOf(
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_NONE,
        )
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) { "No GLES2 pbuffer config" }
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "No GLES2 context" }
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0],
            intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE), 0)
        check(surface != EGL14.EGL_NO_SURFACE) { "No pbuffer surface" }
        try {
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "Cannot make render context current" }
            var renderError: String? = null
            val renderer = factory?.invoke { renderError = it } ?: createLandscapeRendererForCheck(state) { renderError = it }
            renderer.onSurfaceCreated(null, null)
            renderer.onSurfaceChanged(null, width, height)
            renderer.onDrawFrame(null)
            GLES20.glFinish()
            check(renderError == null) { renderError.orEmpty() }
            check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "OpenGL draw failed" }
            val rgba = ByteBuffer.allocateDirect(width * height * 4)
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgba)
            check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "OpenGL readback failed" }
            val pixels = IntArray(width * height)
            for (y in 0 until height) for (x in 0 until width) {
                val i = (y * width + x) * 4
                val r = rgba.get(i).toInt() and 255
                val g = rgba.get(i + 1).toInt() and 255
                val b = rgba.get(i + 2).toInt() and 255
                pixels[(height - 1 - y) * width + x] = (255 shl 24) or (r shl 16) or (g shl 8) or b
            }
            val background = pixels[0]
            if (!expectEmpty) check(pixels.count { it != background } > if (factory == null) 1500 else 50) { "Rendered image is blank" }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, width, 0, 0, width, height) }
            val journey = state.cloudWorld?.journey ?: SkyJourney()
            if (journey.source != JourneySource.NONE || journey.landmarks.isNotEmpty() || journey.complete) {
                JourneySceneView.drawScene(android.graphics.Canvas(bitmap), width.toFloat(), height.toFloat(), journey,
                    island = islandProgress ?: if (journey.complete) 1f else 0f)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            }
            try { output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
            return pixels
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
}
