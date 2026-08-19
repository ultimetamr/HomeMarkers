package com.spatialapps.homemarkers.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import android.util.Log
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.TextField
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.platform.Material
import com.pico.spatial.core.ecs.AnchorComponent
import com.pico.spatial.core.ecs.LookAtComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.anchor.AnchorTarget
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hmd.HMDPose
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.spatialapps.homemarkers.domain.*
import com.spatialapps.homemarkers.data.HomeMarkerRepository
import com.spatialapps.homemarkers.data.WorldAnchorRepository
import com.spatialapps.homemarkers.data.local.HomeMarkerStore
import com.spatialapps.homemarkers.platform.DebugLaunchArguments
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.sense.plane.PlaneTrackingManager
import com.pico.spatial.sense.base.SemanticLabelType
import java.time.LocalDate
import java.util.Locale
import com.pico.spatial.tracking.controller.ControllerTrackingData
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.pico.spatial.tracking.hand.HandJoint.Index
import com.pico.spatial.tracking.hand.HandTrackingProvider

private enum class Mode { CREATE, FIND }
private enum class PanelScreen { HOME, FIND, EDITOR, HIDDEN }
private val DIALOG_TEXT_COLOR = Color.Black
private const val QA_MARKER_NOTE = "Debug 启动参数生成的高亮验收点"

@Composable fun HomeMarkersVolume() {
    var mode by remember { mutableStateOf(Mode.CREATE) }; var query by remember { mutableStateOf("") }
    var panelScreen by remember { mutableStateOf(PanelScreen.HOME) }
    var name by remember { mutableStateOf("") }; var location by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    var expiryInput by remember { mutableStateOf("") }
    var markerColor by remember { mutableStateOf(MarkerColor.LIGHT_BLUE) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var relocatingId by remember { mutableStateOf<String?>(null) }
    var placementPhase by remember { mutableStateOf(MarkerPlacementPhase.IDLE) }
    var draftPosition by remember { mutableStateOf<Vector3?>(null) }
    var draftRotation by remember { mutableStateOf(EulerAngles()) }
    var hintMessage by remember { mutableStateOf("") }
    var hintGeneration by remember { mutableIntStateOf(0) }
    var hintVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val repository = remember { HomeMarkerRepository(HomeMarkerStore.database(context).markerDao()) }
    val anchors = remember { WorldAnchorRepository() }
    val consoleAnchor = remember { Entity().apply { setName("HomeMarkersConsoleAnchor") } }
    val sceneEntities = remember { mutableMapOf<String, Entity>() }
    val scope = rememberCoroutineScope()
    val markers by repository.observe().collectAsState(emptyList())
    val latestMarkers by rememberUpdatedState(markers)
    var highlight by remember { mutableStateOf<String?>(null) }
    var restoredPositions by remember { mutableStateOf<Map<String, Vector3>>(emptyMap()) }
    var restoredRotations by remember { mutableStateOf<Map<String, EulerAngles>>(emptyMap()) }
    var dragOffsets by remember { mutableStateOf<Map<String, Vector3>>(emptyMap()) }
    var expandedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var interactionMessage by remember { mutableStateOf("填写物品信息后，对准墙面或桌面创建。") }
    val qaDistanceArgument by DebugLaunchArguments.highlightDistanceMeters.collectAsStateWithLifecycle()
    val qaPlacementArmed by DebugLaunchArguments.placementArmed.collectAsStateWithLifecycle()
    val qaEditorVisible by DebugLaunchArguments.editorVisible.collectAsStateWithLifecycle()
    val qaHighlightDistance = qaDistanceArgument.takeIf { it > 0f }
    var qaHighlightPrepared by remember { mutableStateOf(false) }
    var qaPlacementPrepared by remember { mutableStateOf(false) }
    var qaEditorPrepared by remember { mutableStateOf(false) }
    val hmd = remember { HMDTrackingProvider() }
    var hmdPose by remember { mutableStateOf<HMDPose?>(null) }
    val controller = remember { ControllerTrackingProvider() }
    val controllerData by controller.dataFlow.collectAsState(ControllerTrackingData(null, null, 0L))
    val latestControllerData by rememberUpdatedState(controllerData)
    val hand = remember { HandTrackingProvider() }
    val pinchClickGate = remember { PinchClickGate() }
    val surfaceTouchGate = remember { SurfaceTouchGate() }
    val highlightedGuidance = highlight?.let { id ->
        val marker = markers.firstOrNull { it.id == id } ?: return@let null
        val pose = hmdPose ?: return@let marker.name to "正在获取头显方向…"
        val anchorPosition = restoredPositions[id] ?: return@let marker.name to "空间锚点尚未恢复"
        val drag = dragOffsets[id] ?: Vector3()
        val target = Vector3(anchorPosition.x + drag.x, anchorPosition.y + drag.y, anchorPosition.z + drag.z)
        val forward = pose.rotation.rotateVector(Vector3.BACK)
        val guidance = MarkerGuidanceCalculator.calculate(
            pose.position.x, pose.position.y, pose.position.z,
            forward.x, forward.z,
            target.x, target.y, target.z,
        )
        marker.name to "${guidance.direction} · ${String.format(Locale.CHINA, "%.1f", guidance.distanceMeters)} 米"
    }
    val highlightedColor = highlight?.let { id -> markers.firstOrNull { it.id == id }?.color }
    fun showTransientHint(message: String) {
        hintMessage = message
        hintGeneration++
    }
    fun clearEditor() {
        name = ""
        location = ""
        note = ""
        expiryInput = ""
        markerColor = MarkerColor.LIGHT_BLUE
        editingId = null
        relocatingId = null
        draftPosition = null
        draftRotation = EulerAngles()
    }
    fun returnHome(message: String? = null) {
        placementPhase = MarkerPlacementPhase.IDLE
        panelScreen = PanelScreen.HOME
        hintVisible = false
        clearEditor()
        message?.let { interactionMessage = it }
    }
    LaunchedEffect(hintGeneration) {
        if (hintGeneration == 0) return@LaunchedEffect
        hintVisible = true
        delay(MarkerPlacementFlow.MISS_HINT_DURATION_MILLIS)
        hintVisible = false
    }
    DisposableEffect(Unit) {
        PlaneTrackingManager.start()
        onDispose {
            PlaneTrackingManager.stop()
            sceneEntities.values.forEach { entity -> runCatching { entity.destroy() } }
            sceneEntities.clear()
            consoleAnchor.destroy()
        }
    }
    suspend fun selectSurface(hit: Vector3, direction: Vector3) {
        if (!MarkerPlacementFlow.canAcceptSurfaceInput(placementPhase)) return
        placementPhase = MarkerPlacementPhase.PLACING
        draftPosition = hit
        draftRotation = cameraFacingRotation(hmdPose?.rotation?.rotateVector(Vector3.BACK) ?: direction)
        hintVisible = false
        panelScreen = PanelScreen.EDITOR
        interactionMessage = "已选择表面，请填写物品信息后保存。"
        Log.i(PLACEMENT_TAG, "surface selected hit=$hit relocation=${relocatingId != null}; editor opened")
    }
    suspend fun createAtRay(origin: Vector3, direction: Vector3) {
        if (!MarkerPlacementFlow.canAcceptSurfaceInput(placementPhase)) return
        val hit = findSurfaceRayHit(origin, direction)
        if (hit == null) {
            placementPhase = MarkerPlacementFlow.afterPlacementAttempt(false)
            showTransientHint("未命中墙面或桌面，请重新点击已识别表面")
            Log.i(PLACEMENT_TAG, "surface input missed; placement remains armed")
            return
        }
        selectSurface(hit, direction)
    }
    suspend fun saveEditor() {
        if (name.isBlank()) {
            interactionMessage = "请填写物品名称。"
            return
        }
        val editing = editingId?.let { id -> markers.firstOrNull { it.id == id } }
        if (editing != null && relocatingId == null) {
            val position = restoredPositions[editing.id] ?: editing.worldPosition ?: run {
                interactionMessage = "原空间锚点位置不可用，无法保存修改。"
                return
            }
            repository.save(editing.copy(name = name, location = location, note = note, expiry = parseExpiry(expiryInput), color = markerColor), position)
            returnHome("已保存 ${editing.name} 的标签内容。")
            return
        }
        val position = draftPosition ?: run {
            interactionMessage = "尚未选择墙面或桌面。"
            return
        }
        val relocationTarget = relocatingId?.let { id -> markers.firstOrNull { it.id == id } }
        val uuid = anchors.create(position, draftRotation, "home-marker-$name")
        if (uuid == null) {
            interactionMessage = "空间锚点创建失败，请取消后重新选择表面。"
            Log.e(PLACEMENT_TAG, "anchor creation failed item=$name hit=$position")
            return
        }
        val marker = relocationTarget?.copy(anchorUuid = uuid, name = name, location = location, note = note, expiry = parseExpiry(expiryInput), color = markerColor)
            ?: HomeMarker(anchorUuid = uuid, name = name, location = location, note = note, expiry = parseExpiry(expiryInput), color = markerColor)
        repository.save(marker, position)
        relocationTarget?.anchorUuid?.let { anchors.remove(it) }
        restoredPositions = restoredPositions + (marker.id to position)
        restoredRotations = restoredRotations + (marker.id to draftRotation)
        Log.i(PLACEMENT_TAG, "placement completed marker=${marker.id} anchor=$uuid hit=$position")
        returnHome("已创建并持久化标签：${marker.name}")
    }
    suspend fun saveDraggedPosition(marker: HomeMarker) {
        val base = restoredPositions[marker.id] ?: return
        val delta = dragOffsets[marker.id] ?: return
        val position = Vector3(base.x + delta.x, base.y + delta.y, base.z + delta.z)
        val rotation = restoredRotations[marker.id] ?: EulerAngles()
        val replacementUuid = anchors.create(position, rotation, "home-marker-${marker.name}") ?: run {
            interactionMessage = "拖拽位置的空间锚点保存失败。"
            return
        }
        repository.save(marker.copy(anchorUuid = replacementUuid), position)
        marker.anchorUuid?.let { anchors.remove(it) }
        restoredPositions = restoredPositions + (marker.id to position)
        dragOffsets = dragOffsets - marker.id
        interactionMessage = "已保存 ${marker.name} 的拖拽位置。"
    }
    val latestCreateAtRay by rememberUpdatedState<suspend (Vector3, Vector3) -> Unit>(::createAtRay)
    val latestSelectSurface by rememberUpdatedState<suspend (Vector3, Vector3) -> Unit>(::selectSurface)
    val latestPlacementPhase by rememberUpdatedState(placementPhase)
    DisposableEffect(hmd) { hmd.start(); onDispose { hmd.stop() } }
    DisposableEffect(controller) {
        var leftWasPressed = false
        var rightWasPressed = false
        val listener = ControllerTrackingProvider.ControllerActionListener { action ->
            val leftPressed = action.left.triggerPressed
            val rightPressed = action.right.triggerPressed
            if (latestPlacementPhase == MarkerPlacementPhase.ARMED) {
                if (leftPressed && !leftWasPressed) {
                    latestControllerData.left?.let { pose -> scope.launch { latestCreateAtRay(pose.position, pose.rotation.rotateVector(Vector3.BACK)) } }
                }
                if (rightPressed && !rightWasPressed) {
                    latestControllerData.right?.let { pose -> scope.launch { latestCreateAtRay(pose.position, pose.rotation.rotateVector(Vector3.BACK)) } }
                }
            }
            leftWasPressed = leftPressed
            rightWasPressed = rightPressed
        }
        controller.addControllerActionListener(listener); controller.start()
        onDispose { controller.removeControllerActionListener(listener); controller.stop() }
    }
    DisposableEffect(hand) {
        hand.start()
        onDispose { hand.stop() }
    }
    LaunchedEffect(hmd) { hmd.dataFlow.collect { hmdPose = it.hmdPose } }
    LaunchedEffect(hand, placementPhase) {
        if (placementPhase != MarkerPlacementPhase.ARMED) {
            pinchClickGate.reset()
            surfaceTouchGate.reset()
            return@LaunchedEffect
        }
        var handClickReleasedSinceArming = false
        while (placementPhase == MarkerPlacementPhase.ARMED) {
            val trackedHand = hand.latestData.right ?: hand.latestData.left
            val cameraPose = hmdPose
            if (trackedHand == null || cameraPose == null) {
                pinchClickGate.reset()
                surfaceTouchGate.reset()
                delay(PLACEMENT_GESTURE_SAMPLE_MILLIS)
                continue
            }
            val indexTip = trackedHand[Index.INDEX_TIP].position
            val thumbTip = trackedHand[Index.THUMB_TIP].position
            val direction = normalized(subtract(indexTip, cameraPose.position))
            val hit = findSurfaceRayHit(cameraPose.position, direction)
            val fingertipToSurface = hit?.let { distanceMeters(indexTip, it) }
            val fingertipDistance = distanceMeters(indexTip, thumbTip)
            if (!handClickReleasedSinceArming) {
                handClickReleasedSinceArming = fingertipDistance > PINCH_RELEASE_METRES
                pinchClickGate.reset()
                surfaceTouchGate.consume(fingertipToSurface)
                delay(PLACEMENT_GESTURE_SAMPLE_MILLIS)
                continue
            }
            val pinched = pinchClickGate.consume(fingertipDistance)
            val touched = surfaceTouchGate.consume(fingertipToSurface)
            if ((pinched || touched) && hit != null) {
                latestSelectSurface(hit, direction)
            }
            delay(PLACEMENT_GESTURE_SAMPLE_MILLIS)
        }
    }
    LaunchedEffect(qaHighlightDistance) {
        val distance = qaHighlightDistance ?: return@LaunchedEffect
        if (qaHighlightPrepared) return@LaunchedEffect
        qaHighlightPrepared = true
        val pose = hmd.dataFlow.mapNotNull { it.hmdPose }.first()
        val forward = pose.rotation.rotateVector(Vector3.BACK)
        val right = pose.rotation.rotateVector(Vector3(1f, 0f, 0f))
        val lateralOffset = (distance * .2f).coerceAtMost(.7f)
        val position = Vector3(
            pose.position.x + forward.x * distance + right.x * lateralOffset,
            pose.position.y + forward.y * distance + right.y * lateralOffset + 1.8f,
            pose.position.z + forward.z * distance + right.z * lateralOffset,
        )
        val rotation = cameraFacingRotation(forward)
        latestMarkers.filter { it.note == QA_MARKER_NOTE }.forEach { oldMarker ->
            oldMarker.anchorUuid?.let { anchors.remove(it) }
            repository.delete(oldMarker.id)
        }
        val uuid = anchors.create(position, rotation, "home-marker-qa-distance-target")
        if (uuid == null) {
            qaHighlightPrepared = false
            Log.e("HomeMarkersQA", "failed to create ${distance}m verification anchor")
            return@LaunchedEffect
        }
        val marker = HomeMarker(
            anchorUuid = uuid,
            name = "远距离测试目标",
            location = "客厅远端储物区",
            note = QA_MARKER_NOTE,
            color = MarkerColor.LIGHT_GRAY,
        )
        repository.save(marker, position)
        restoredPositions = restoredPositions + (marker.id to position)
        restoredRotations = restoredRotations + (marker.id to rotation)
        mode = Mode.FIND
        panelScreen = PanelScreen.FIND
        highlight = marker.id
        interactionMessage = "已生成 ${distance} 米远距离高亮验收点"
        Log.i("HomeMarkersQA", "highlight marker=${marker.id} distance=$distance anchor=$uuid")
    }
    LaunchedEffect(qaPlacementArmed) {
        if (!qaPlacementArmed || qaPlacementPrepared) return@LaunchedEffect
        qaPlacementPrepared = true
        mode = Mode.CREATE
        clearEditor()
        panelScreen = PanelScreen.HIDDEN
        placementPhase = MarkerPlacementPhase.ARMED
        showTransientHint("放置已开始：点击、捏合或触碰墙面/桌面")
        Log.i("HomeMarkersQA", "placement armed verification state prepared")
    }
    LaunchedEffect(qaEditorVisible) {
        if (!qaEditorVisible || qaEditorPrepared) return@LaunchedEffect
        qaEditorPrepared = true
        mode = Mode.CREATE
        clearEditor()
        draftPosition = Vector3(0f, 1.4f, -1.5f)
        draftRotation = EulerAngles()
        placementPhase = MarkerPlacementPhase.PLACING
        panelScreen = PanelScreen.EDITOR
        interactionMessage = "已选择表面，请填写物品信息后保存。"
        Log.i("HomeMarkersQA", "post-hit editor verification state prepared")
    }
    DisposableEffect(anchors) {
        val subscription = anchors.subscribe { event, anchor ->
            val marker = latestMarkers.firstOrNull { it.anchorUuid == anchor.anchorUUID.toString() } ?: return@subscribe
            if (event == "REMOVED") {
                restoredPositions = restoredPositions - marker.id
                restoredRotations = restoredRotations - marker.id
            } else {
                restoredPositions = restoredPositions + (marker.id to anchor.transform.position)
                restoredRotations = restoredRotations + (marker.id to anchor.transform.rotation)
            }
        }
        onDispose { subscription.cancel() }
    }
    LaunchedEffect(markers) {
        if (markers.isEmpty()) { restoredPositions = emptyMap(); return@LaunchedEffect }
        // Full-Stage-only all-anchor load; never substitute a stale Room position for a missing anchor.
        delay(1_000)
        val anchorsByUuid = anchors.loadAll()
        restoredPositions = markers.mapNotNull { marker -> anchorsByUuid[marker.anchorUuid]?.transform?.position?.let { marker.id to it } }.toMap()
        restoredRotations = markers.mapNotNull { marker -> anchorsByUuid[marker.anchorUuid]?.transform?.rotation?.let { marker.id to it } }.toMap()
        Log.i("HomeMarkersRestore", "room=${markers.size} anchors=${anchorsByUuid.size} restored=${restoredPositions.size}")
        markers.filterNot { it.id in restoredPositions }.forEach { marker ->
            Log.w("HomeMarkersRestore", "marker=${marker.id} anchor=${marker.anchorUuid} unavailable; hidden until anchor event")
        }
    }
    fun beginCreatePlacement() {
        mode = Mode.CREATE
        highlight = null
        clearEditor()
        placementPhase = MarkerPlacementPhase.ARMED
        panelScreen = PanelScreen.HIDDEN
        showTransientHint("点击、捏合或触碰已识别墙面/桌面")
        Log.i(PLACEMENT_TAG, "new marker placement armed; main panel hidden")
    }
    fun editMarker(marker: HomeMarker) {
        mode = Mode.CREATE
        placementPhase = MarkerPlacementPhase.IDLE
        editingId = marker.id
        relocatingId = null
        name = marker.name
        location = marker.location
        note = marker.note
        expiryInput = marker.expiry?.toString().orEmpty()
        markerColor = marker.color
        draftPosition = null
        draftRotation = EulerAngles()
        panelScreen = PanelScreen.EDITOR
        menuId = null
    }
    fun relocateMarker(marker: HomeMarker) {
        mode = Mode.CREATE
        highlight = marker.id
        placementPhase = MarkerPlacementPhase.ARMED
        editingId = null
        relocatingId = marker.id
        name = marker.name
        location = marker.location
        note = marker.note
        expiryInput = marker.expiry?.toString().orEmpty()
        markerColor = marker.color
        draftPosition = null
        draftRotation = EulerAngles()
        panelScreen = PanelScreen.HIDDEN
        menuId = null
        showTransientHint("请在新墙面或桌面上点击、捏合或触碰")
        Log.i(PLACEMENT_TAG, "relocation armed marker=${marker.id}; main panel hidden")
    }
    fun deleteMarker(marker: HomeMarker) {
        scope.launch {
            marker.anchorUuid?.let { anchors.remove(it) }
            repository.delete(marker.id)
            restoredPositions = restoredPositions - marker.id
            restoredRotations = restoredRotations - marker.id
            dragOffsets = dragOffsets - marker.id
            sceneEntities.remove(marker.id)?.let { entity -> runCatching { entity.destroy() } }
            menuId = null
            if (highlight == marker.id) highlight = null
            if (editingId == marker.id || relocatingId == marker.id) returnHome("已删除 ${marker.name}")
        }
    }
    SpatialView(attachments = {
        AttachmentPanel(id = CONSOLE_PANEL_ID) {
            when (panelScreen) {
                PanelScreen.HOME -> Box(Modifier.size(520.dp, 132.dp).clip(RoundedCornerShape(32.dp)).backgroundMaterial(true, Material.Regular)) {
                    HomeLauncher(onCreate = ::beginCreatePlacement, onFind = { mode = Mode.FIND; panelScreen = PanelScreen.FIND })
                }
                PanelScreen.FIND -> Box(Modifier.size(960.dp, 720.dp).clip(RoundedCornerShape(36.dp)).backgroundMaterial(true, Material.Regular)) {
                    FindPanel(
                        query = query,
                        onQuery = { query = it },
                        markers = markers,
                        restoredCount = restoredPositions.size,
                        highlight = highlight,
                        guidance = highlightedGuidance,
                        highlightedColor = highlightedColor,
                        onBack = { mode = Mode.CREATE; highlight = null; panelScreen = PanelScreen.HOME },
                        onLocate = { marker -> highlight = marker.id; interactionMessage = "已在空间中高亮：${marker.name}" },
                        onEdit = ::editMarker,
                        onRelocate = ::relocateMarker,
                        onDelete = ::deleteMarker,
                    )
                }
                else -> Spacer(Modifier.size(1.dp))
            }
        }
        AttachmentPanel(id = EDITOR_PANEL_ID) {
            if (panelScreen == PanelScreen.EDITOR) {
                Box(Modifier.size(880.dp, 720.dp).clip(RoundedCornerShape(36.dp)).backgroundMaterial(true, Material.Regular)) {
                    MarkerEditor(
                        editing = editingId != null,
                        relocating = relocatingId != null,
                        name = name,
                        onName = { name = it },
                        location = location,
                        onLocation = { location = it },
                        note = note,
                        onNote = { note = it },
                        expiryInput = expiryInput,
                        onExpiry = { expiryInput = it },
                        markerColor = markerColor,
                        onColor = { markerColor = it },
                        message = interactionMessage,
                        onSave = { scope.launch { saveEditor() } },
                        onCancel = { returnHome("已取消编辑。") },
                    )
                }
            } else Spacer(Modifier.size(1.dp))
        }
        AttachmentPanel(id = HINT_PANEL_ID) {
            if (hintVisible) PlacementHint(hintMessage) else Spacer(Modifier.size(1.dp))
        }
        markers.forEach { marker ->
            AttachmentPanel(id = marker.id) {
                SpatialMarkerCard(
                    marker = marker,
                    dimmed = mode == Mode.FIND && highlight != null && marker.id != highlight,
                    expanded = marker.id in expandedIds,
                    menuVisible = menuId == marker.id,
                    onTap = { expandedIds = if (marker.id in expandedIds) expandedIds - marker.id else expandedIds + marker.id },
                    onLongPress = { menuId = if (menuId == marker.id) null else marker.id },
                    onDrag = { dx, dy, dz ->
                        val old = dragOffsets[marker.id] ?: Vector3()
                        dragOffsets = dragOffsets + (marker.id to Vector3(old.x + dx * .001f, old.y - dy * .001f, old.z + dz * .001f))
                    },
                    onSavePosition = { scope.launch { saveDraggedPosition(marker) } },
                    onEdit = { editMarker(marker) },
                    onDelete = { deleteMarker(marker) },
                )
            }
        }
    }, initial = { content, attachments ->
        consoleAnchor.components.set(
            AnchorComponent(AnchorTarget.createCameraTarget()).apply {
                positionOffset = CONSOLE_CAMERA_OFFSET
            },
        )
        content.addEntity(consoleAnchor)
        listOf(CONSOLE_PANEL_ID, EDITOR_PANEL_ID, HINT_PANEL_ID).forEach { panelId ->
            attachments.entity(panelId)?.let { panel ->
                panel.components[TransformComponent::class.java]?.setPosition(
                    if (panelId == HINT_PANEL_ID) Vector3(0f, .42f, -.03f) else Vector3.ZERO,
                )
                panel.components.set(LookAtComponent())
                panel.components[LookAtComponent::class.java]?.apply {
                    setViewerAsTarget()
                    alignLocalUpToWorldUp = true
                }
                panel.enabled = when (panelId) {
                    CONSOLE_PANEL_ID -> panelScreen == PanelScreen.HOME || panelScreen == PanelScreen.FIND
                    EDITOR_PANEL_ID -> panelScreen == PanelScreen.EDITOR
                    else -> hintVisible
                }
                consoleAnchor.addChild(panel)
            } ?: Log.e(PANEL_TAG, "camera anchor created but attachment is unavailable: $panelId")
        }
        Log.i(PANEL_TAG, "camera anchor ready offset=$CONSOLE_CAMERA_OFFSET lookAt=VIEWER attachedPanels=3")
    }, update = { content, attachments ->
        attachments.entity(CONSOLE_PANEL_ID)?.enabled = panelScreen == PanelScreen.HOME || panelScreen == PanelScreen.FIND
        attachments.entity(EDITOR_PANEL_ID)?.enabled = panelScreen == PanelScreen.EDITOR
        attachments.entity(HINT_PANEL_ID)?.enabled = hintVisible
        markers.forEach { marker ->
            // A Room record only becomes visible after it has a recovered/created anchor pose.
            restoredPositions[marker.id]?.let { anchorPosition -> attachments.entity(marker.id)?.apply {
                val delta = dragOffsets[marker.id] ?: Vector3()
                val position = Vector3(anchorPosition.x + delta.x, anchorPosition.y + delta.y, anchorPosition.z + delta.z)
                components[TransformComponent::class.java]?.apply {
                    setPosition(position)
                    val cameraDirection = hmdPose?.let { pose -> Vector3(position.x - pose.position.x, 0f, position.z - pose.position.z) }
                    setEulerAngles(cameraDirection?.let(::cameraFacingRotation) ?: restoredRotations[marker.id] ?: EulerAngles())
                }
                content.addEntity(this)
                sceneEntities[marker.id] = this
            } }
        }
    })
}

@Composable private fun SpatialMarkerCard(
    marker: HomeMarker,
    dimmed: Boolean,
    expanded: Boolean,
    menuVisible: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float, Float, Float) -> Unit,
    onSavePosition: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val outerShape = RoundedCornerShape(18.dp)
    val textColor = markerTextColor(marker.color)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .size(if (expanded || menuVisible) 420.dp else 300.dp, if (expanded || menuVisible) 226.dp else 116.dp)
                .clip(outerShape)
                .spatialHoverEffect {
                    scale(if (it.isActive) 1.04f else 1f)
                    alpha(if (it.isActive) 1f else .94f)
                }
                .graphicsLayer(
                    alpha = MarkerHighlightPresentationPolicy.alpha(dimmed),
                )
                .pointerInput(marker.id) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) }
                .pointerInput(marker.id) {
                    detectSpatialDragGesture(context, targetedToEntity = TargetEntity.any()) { value ->
                        onDrag(value.dragAmount.x, value.dragAmount.y, value.dragAmount.z)
                    }
                }
                .background(markerBackgroundColor(marker.color))
                .border(3.dp, expiryBorder(marker.expiryState), outerShape),
        ) {
            Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(markerBackgroundColor(marker.color))
                    .border(
                        3.dp,
                        expiryBorder(marker.expiryState),
                        RoundedCornerShape(14.dp),
                    ).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(marker.name, style = PicoTheme.typography.titleMedium, color = textColor)
                Text(marker.location, color = textColor.copy(alpha = .82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(marker.note.ifBlank { expiry(marker) }, color = textColor.copy(alpha = .76f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (expanded) {
                    Text("位置：${marker.location}", color = textColor.copy(alpha = .82f))
                    Text("有效期：${expiry(marker)}", color = textColor.copy(alpha = .82f))
                }
                if (menuVisible) {
                    Text("长按菜单 · 拖拽后请保存位置", color = PicoTheme.colorScheme.labelSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = onSavePosition) { Text("保存位置") }
                        Button(onClick = onEdit) { Text("编辑") }
                        Button(onClick = onDelete) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable private fun markerBackgroundColor(color: MarkerColor): Color = when (color) {
    MarkerColor.LIGHT_BLUE -> Color(0xD9A9DDF5) // design-style: fixed-figma-color marker light blue
    MarkerColor.PALE_YELLOW -> Color(0xD9F7E7A9) // design-style: fixed-figma-color marker pale yellow
    MarkerColor.PALE_PINK -> Color(0xD9F3C4D8) // design-style: fixed-figma-color marker pale pink
    MarkerColor.PALE_GREEN -> Color(0xD9BFE6C8) // design-style: fixed-figma-color marker pale green
    MarkerColor.LIGHT_PURPLE -> Color(0xD9D7C8F2) // design-style: fixed-figma-color marker light purple
    MarkerColor.LIGHT_GRAY -> Color(0xD9CDD2D8) // design-style: fixed-figma-color marker light gray
}

private fun markerAccentColor(color: MarkerColor): Color = when (color) {
    MarkerColor.LIGHT_BLUE -> Color(0xFF4B9FC7) // design-style: fixed-figma-color marker light blue accent
    MarkerColor.PALE_YELLOW -> Color(0xFFC5A63C) // design-style: fixed-figma-color marker pale yellow accent
    MarkerColor.PALE_PINK -> Color(0xFFC7789C) // design-style: fixed-figma-color marker pale pink accent
    MarkerColor.PALE_GREEN -> Color(0xFF65A873) // design-style: fixed-figma-color marker pale green accent
    MarkerColor.LIGHT_PURPLE -> Color(0xFF8C72C4) // design-style: fixed-figma-color marker light purple accent
    MarkerColor.LIGHT_GRAY -> Color(0xFF7A838D) // design-style: fixed-figma-color marker light gray accent
}

private fun markerTextColor(color: MarkerColor): Color = when (color) {
    MarkerColor.LIGHT_BLUE -> Color(0xFF164E68) // design-style: fixed-figma-color marker readable blue text
    MarkerColor.PALE_YELLOW -> Color(0xFF5F4C0C) // design-style: fixed-figma-color marker readable yellow text
    MarkerColor.PALE_PINK -> Color(0xFF6B2947) // design-style: fixed-figma-color marker readable pink text
    MarkerColor.PALE_GREEN -> Color(0xFF245B31) // design-style: fixed-figma-color marker readable green text
    MarkerColor.LIGHT_PURPLE -> Color(0xFF493276) // design-style: fixed-figma-color marker readable purple text
    MarkerColor.LIGHT_GRAY -> Color(0xFF343A40) // design-style: fixed-figma-color marker readable gray text
}

@Composable private fun expiryBorder(state: ExpiryState): Color = when (state) {
    ExpiryState.NORMAL -> Color.Transparent
    ExpiryState.NEAR_EXPIRY -> PicoTheme.colorScheme.alert
    ExpiryState.EXPIRED -> PicoTheme.colorScheme.error
}

@Composable
private fun HomeLauncher(onCreate: () -> Unit, onFind: () -> Unit) =
    Row(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onCreate) { Text("＋ 创建标记") }
        Button(onClick = onFind) { Text("⌕ 查询标记") }
    }

@Composable
private fun FindPanel(
    query: String,
    onQuery: (String) -> Unit,
    markers: List<HomeMarker>,
    restoredCount: Int,
    highlight: String?,
    guidance: Pair<String, String>?,
    highlightedColor: MarkerColor?,
    onBack: () -> Unit,
    onLocate: (HomeMarker) -> Unit,
    onEdit: (HomeMarker) -> Unit,
    onRelocate: (HomeMarker) -> Unit,
    onDelete: (HomeMarker) -> Unit,
) = Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Button(onClick = onBack) { Text("← 返回") }
        Text("查询标记", style = PicoTheme.typography.titleLarge, color = PicoTheme.colorScheme.labelPrimary)
        Text("已恢复 $restoredCount / ${markers.size}", color = PicoTheme.colorScheme.labelSecondary)
    }
    Field("搜索物品、房间或备注", query, onQuery)
    guidance?.let { (targetName, direction) ->
        Text("⌖ $targetName：$direction", style = PicoTheme.typography.titleMedium, color = highlightedColor?.let(::markerAccentColor) ?: PicoTheme.colorScheme.alert)
    } ?: Text("选择“定位”后，对应空间标签会高亮", color = PicoTheme.colorScheme.labelSecondary)
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MarkerSearch.filter(markers, query).forEach { marker ->
            RowItem(marker, marker.id == highlight, { onLocate(marker) }, { onEdit(marker) }, { onRelocate(marker) }, { onDelete(marker) })
        }
    }
}

@Composable
private fun MarkerEditor(
    editing: Boolean,
    relocating: Boolean,
    name: String,
    onName: (String) -> Unit,
    location: String,
    onLocation: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    expiryInput: String,
    onExpiry: (String) -> Unit,
    markerColor: MarkerColor,
    onColor: (MarkerColor) -> Unit,
    message: String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) = Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text(
        when { relocating -> "重新锚定标记"; editing -> "编辑标记"; else -> "创建标记" },
        style = PicoTheme.typography.titleLarge,
        color = DIALOG_TEXT_COLOR,
    )
    Text(if (editing) "修改内容不会改变原空间位置。" else "表面已选定，请填写标签内容。", color = DIALOG_TEXT_COLOR)
    Field("物品名称", name, onName)
    Field("存放位置", location, onLocation)
    Field("使用备注", note, onNote)
    Field("保质期 YYYY-MM-DD（可空）", expiryInput, onExpiry)
    Button(onClick = { onColor(MarkerColor.entries[(markerColor.ordinal + 1) % MarkerColor.entries.size]) }) {
        Text("颜色：${markerColor.label}")
    }
    Text(message, color = DIALOG_TEXT_COLOR)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onSave) { Text(if (editing) "保存修改" else "创建标签") }
        Button(onClick = onCancel) { Text("取消") }
    }
}

@Composable
private fun PlacementHint(message: String) =
    Box(
        modifier = Modifier.size(620.dp, 96.dp).clip(RoundedCornerShape(24.dp)).backgroundMaterial(true, Material.Regular).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = PicoTheme.typography.titleMedium, color = DIALOG_TEXT_COLOR)
    }

@Composable
private fun Field(label: String, value: String, change: (String) -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = DIALOG_TEXT_COLOR)
        TextField(
            value = value,
            onValueChange = change,
            placeholder = { Text(label, color = DIALOG_TEXT_COLOR) },
            textStyle = PicoTheme.typography.bodyLarge.copy(color = DIALOG_TEXT_COLOR),
            modifier = Modifier.fillMaxWidth(),
        )
    }
@Composable private fun RowItem(marker: HomeMarker, selected: Boolean, locate: () -> Unit, edit: () -> Unit, relocate: () -> Unit, delete: () -> Unit) = Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (selected) PicoTheme.colorScheme.interaction else PicoTheme.colorScheme.fillSecondary).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(markerAccentColor(marker.color))); Column(Modifier.weight(1f)) { Text(marker.name, color = PicoTheme.colorScheme.labelPrimary); Text("${marker.location} · ${expiry(marker)}", color = PicoTheme.colorScheme.labelSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Button(onClick = locate) { Text("定位") }; Button(onClick = edit) { Text("编辑") }; Button(onClick = relocate) { Text("重锚") }; Button(onClick = delete) { Text("删除") } }
private fun expiry(m: HomeMarker) = when (m.expiryState) { ExpiryState.EXPIRED -> "已过期"; ExpiryState.NEAR_EXPIRY -> "临期 ${m.daysRemaining} 天"; ExpiryState.NORMAL -> m.expiry?.toString() ?: "无有效期" }
private fun parseExpiry(value: String): LocalDate? = value.trim().takeIf(String::isNotEmpty)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private suspend fun findSurfaceRayHit(origin: Vector3, direction: Vector3): Vector3? =
    PlaneTrackingManager.loadAllAnchors().asSequence()
        .filter { it.semantics == SemanticLabelType.WALL || it.semantics == SemanticLabelType.TABLE }
        .flatMap { plane ->
            val rotation = plane.transform.rotation.toQuat(); val center = plane.transform.position
            val vertices = plane.vertices.map { vertex -> rotation.rotateVector(vertex).let { Vector3(it.x + center.x, it.y + center.y, it.z + center.z) } }
            plane.indices.chunked(3).asSequence().mapNotNull { triangle -> if (triangle.size == 3 && triangle.all { it in vertices.indices }) rayTriangleDistance(origin, direction, vertices[triangle[0]], vertices[triangle[1]], vertices[triangle[2]]) else null }
        }.filter { it > 0f }.minOrNull()?.let { distance -> Vector3(origin.x + direction.x * distance, origin.y + direction.y * distance, origin.z + direction.z * distance) }

private fun cameraFacingRotation(direction: Vector3) = EulerAngles(0f, Math.toDegrees(kotlin.math.atan2(-direction.x, -direction.z).toDouble()).toFloat(), 0f)
private fun distanceMeters(from: Vector3, to: Vector3): Float {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dz = to.z - from.z
    return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
}
private fun subtract(left: Vector3, right: Vector3) = Vector3(left.x - right.x, left.y - right.y, left.z - right.z)
private fun normalized(vector: Vector3): Vector3 {
    val length = distanceMeters(Vector3(), vector)
    return if (length < .00001f) Vector3.BACK else Vector3(vector.x / length, vector.y / length, vector.z / length)
}
private fun rayTriangleDistance(origin: Vector3, direction: Vector3, a: Vector3, b: Vector3, c: Vector3): Float? {
    fun sub(l: Vector3, r: Vector3) = Vector3(l.x - r.x, l.y - r.y, l.z - r.z)
    fun dot(l: Vector3, r: Vector3) = l.x * r.x + l.y * r.y + l.z * r.z
    fun cross(l: Vector3, r: Vector3) = Vector3(l.y * r.z - l.z * r.y, l.z * r.x - l.x * r.z, l.x * r.y - l.y * r.x)
    val e1=sub(b,a); val e2=sub(c,a); val p=cross(direction,e2); val d=dot(e1,p); if (kotlin.math.abs(d)<.00001f) return null
    val inv=1f/d; val t=sub(origin,a); val u=dot(t,p)*inv; if (u !in 0f..1f) return null; val q=cross(t,e1); val v=dot(direction,q)*inv; if(v<0f||u+v>1f)return null; return dot(e2,q)*inv
}

private const val CONSOLE_PANEL_ID = "home-markers-console"
private const val EDITOR_PANEL_ID = "home-markers-editor"
private const val HINT_PANEL_ID = "home-markers-placement-hint"
private const val PANEL_TAG = "HomeMarkersPanel"
private const val PLACEMENT_TAG = "HomeMarkersPlacement"
private const val PLACEMENT_GESTURE_SAMPLE_MILLIS = 80L
private const val PINCH_RELEASE_METRES = 0.04f
private val CONSOLE_CAMERA_OFFSET = Vector3(0f, 0f, -0.9f)
