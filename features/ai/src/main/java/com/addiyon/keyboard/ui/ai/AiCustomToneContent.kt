package com.addiyon.keyboard.ui.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.addiyon.keyboard.ai.AiToneTab
import com.addiyon.keyboard.ai.CustomTone
import com.addiyon.keyboard.ai.CustomToneColor
import com.addiyon.keyboard.ai.CustomToneIcon
import com.addiyon.keyboard.ai.ToneOrderItem
import com.addiyon.keyboard.ai.orderedToneSequence
import com.addiyon.keyboard.features.appshell.KeyboardPageTopBar
import com.addiyon.keyboard.ui.design.AddiyonBorders
import com.addiyon.keyboard.ui.design.AddiyonButton
import com.addiyon.keyboard.ui.design.AddiyonElevation
import com.addiyon.keyboard.ui.design.AddiyonInputField
import com.addiyon.keyboard.ui.design.AddiyonMotion
import com.addiyon.keyboard.ui.design.AddiyonOutlinedButton
import com.addiyon.keyboard.ui.design.AddiyonRadii
import com.addiyon.keyboard.ui.design.AddiyonSizes
import com.addiyon.keyboard.ui.design.AddiyonSpacing
import com.addiyon.keyboard.ui.design.addiyonColors

const val AI_CUSTOM_TONE_TITLE_FIELD_TAG = "ai.custom.tone.title.field"
const val AI_CUSTOM_TONE_FIELD_TAG = "ai.custom.tone.field"
const val AI_CUSTOM_TONE_SAVE_TAG = "ai.custom.tone.save"
const val AI_CUSTOM_TONE_CANCEL_TAG = "ai.custom.tone.cancel"
const val AI_CUSTOM_TONE_LIST_TAG = "ai.custom.tone.list"
const val AI_CUSTOM_TONE_ICON_GRID_TAG = "ai.custom.tone.icon.grid"
const val AI_CUSTOM_TONE_ADD_BUTTON_TAG = "ai.custom.tone.add.button"
const val AI_CUSTOM_TONE_CONFIRM_DELETE_TAG = "ai.custom.tone.confirm.delete"
const val AI_CUSTOM_TONE_CANCEL_DELETE_TAG = "ai.custom.tone.cancel.delete"
const val CUSTOM_TONE_ICON_ROWS = 4
const val CUSTOM_TONE_ICON_COLUMNS = 8

fun aiCustomToneItemTag(id: String): String = "ai.custom.tone.item.$id"
fun aiCustomToneEditTag(id: String): String = "ai.custom.tone.edit.$id"
fun aiCustomToneRemoveTag(id: String): String = "ai.custom.tone.remove.$id"
fun aiCustomToneIconTag(iconId: String): String = "ai.custom.tone.icon.$iconId"
fun aiCustomToneColorTag(colorId: String): String = "ai.custom.tone.color.$colorId"

@Composable
fun AiCustomToneContent(
    customTones: List<CustomTone>,
    strings: AiUiStrings,
    toneOrder: List<String> = emptyList(),
    onBack: () -> Unit,
    onSave: (title: String, instruction: String, icon: String, color: String) -> Unit,
    onUpdate: (id: String, title: String, instruction: String, icon: String, color: String) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (List<String>) -> Unit = {}
) {
    var showForm by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var instruction by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(CustomToneIcon.Default) }
    var selectedColor by remember { mutableStateOf(CustomToneColor.Default) }
    var showColorPicker by remember { mutableStateOf(false) }
    var pickerAnchorIcon by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val isEditing = editingId != null
    var toneToDelete by remember { mutableStateOf<CustomTone?>(null) }

    var items by remember(customTones, toneOrder) {
        mutableStateOf(orderedToneSequence(toneOrder, customTones))
    }
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        items = items.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun resetForm() {
        editingId = null
        title = ""
        instruction = ""
        selectedIcon = CustomToneIcon.Default
        selectedColor = CustomToneColor.Default
        showColorPicker = false
        pickerAnchorIcon = null
        error = null
        showForm = false
    }

    fun openAddForm() {
        editingId = null
        title = ""
        instruction = ""
        selectedIcon = CustomToneIcon.Default
        selectedColor = CustomToneColor.Default
        showColorPicker = false
        pickerAnchorIcon = null
        error = null
        showForm = true
    }

    fun openEditForm(tone: CustomTone) {
        editingId = tone.id
        title = tone.title
        instruction = tone.instruction
        selectedIcon = tone.icon
        selectedColor = tone.color
        showColorPicker = false
        pickerAnchorIcon = null
        error = null
        showForm = true
    }

    fun onIconTap(iconId: String) {
        if (iconId == selectedIcon && showColorPicker && pickerAnchorIcon == iconId) {
            showColorPicker = false
            pickerAnchorIcon = null
        } else {
            selectedIcon = iconId
            showColorPicker = true
            pickerAnchorIcon = iconId
        }
    }

    fun saveForm() {
        val cleanTitle = title.trim()
        val cleanInstruction = instruction.trim()
        if (cleanTitle.isEmpty() || cleanInstruction.isEmpty()) {
            error = strings.aiCustomToneError
            return
        }
        val id = editingId
        if (id != null) {
            onUpdate(id, cleanTitle, cleanInstruction, selectedIcon, selectedColor)
        } else {
            onSave(cleanTitle, cleanInstruction, selectedIcon, selectedColor)
        }
        resetForm()
    }

    val handleBack: () -> Unit = {
        if (showForm) {
            resetForm()
        } else {
            onBack()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            KeyboardPageTopBar(
                title = if (showForm) {
                    if (isEditing) strings.aiCustomToneEditHeading else strings.aiAddNewInstructionTitle
                } else {
                    strings.aiCustomToneTitle
                },
                onBack = handleBack,
                backContentDescription = strings.back
            )
        },
        floatingActionButton = {
            if (!showForm) {
                FloatingActionButton(
                    onClick = { openAddForm() },
                    shape = RoundedCornerShape(AddiyonRadii.medium),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag(AI_CUSTOM_TONE_ADD_BUTTON_TAG)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = strings.aiAddCustomTone,
                        modifier = Modifier.size(AddiyonSizes.iconMedium)
                    )
                }
            }
        }
    ) { innerPadding ->
        if (showForm) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AddiyonSpacing.xl, vertical = AddiyonSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.sm)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.xxs)) {
                    Text(
                        text = strings.aiCustomToneTitleLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AddiyonInputField(
                        value = title,
                        onValueChange = {
                            title = it
                            error = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(AI_CUSTOM_TONE_TITLE_FIELD_TAG),
                        placeholder = strings.aiCustomToneTitleFieldPlaceholder,
                        singleLine = true
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.xxs)) {
                    Text(
                        text = strings.aiCustomToneInstructionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AddiyonInputField(
                        value = instruction,
                        onValueChange = {
                            instruction = it
                            error = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(AI_CUSTOM_TONE_FIELD_TAG),
                        placeholder = strings.aiCustomToneFieldPlaceholder,
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                        isError = error != null
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.xxs)) {
                    Text(
                        text = strings.aiCustomToneIconLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(AI_CUSTOM_TONE_ICON_GRID_TAG),
                        verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.xxs)
                    ) {
                        CustomToneIcon.All.chunked(CUSTOM_TONE_ICON_COLUMNS).forEachIndexed { rowIndex, rowIcons ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.xxs)
                            ) {
                                rowIcons.forEachIndexed { columnInRow, iconId ->
                                    val globalIndex = rowIndex * CUSTOM_TONE_ICON_COLUMNS + columnInRow
                                    val column = globalIndex % CUSTOM_TONE_ICON_COLUMNS
                                    val isRightHalf = column >= CUSTOM_TONE_ICON_COLUMNS / 2
                                    val isSelected = selectedIcon == iconId
                                    val showPickerForThis = isSelected && showColorPicker && pickerAnchorIcon == iconId
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(2.dp)
                                                .background(
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                    shape = CircleShape
                                                )
                                                .clip(CircleShape)
                                                .clickable { onIconTap(iconId) }
                                                .semantics {
                                                    contentDescription = customToneIconName(iconId, strings)
                                                }
                                                .testTag(aiCustomToneIconTag(iconId)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = customToneIcon(iconId),
                                                contentDescription = null,
                                                tint = if (isSelected) {
                                                    customToneColor(selectedColor)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(AddiyonSizes.iconMedium)
                                            )
                                        }
                                        if (showPickerForThis) {
                                            CustomToneColorPopup(
                                                selectedColor = selectedColor,
                                                showOnLeft = isRightHalf,
                                                strings = strings,
                                                onPick = { colorId ->
                                                    selectedColor = colorId
                                                    showColorPicker = false
                                                    pickerAnchorIcon = null
                                                },
                                                onDismiss = {
                                                    showColorPicker = false
                                                    pickerAnchorIcon = null
                                                }
                                            )
                                        }
                                    }
                                }
                                if (rowIcons.size < CUSTOM_TONE_ICON_COLUMNS) {
                                    repeat(CUSTOM_TONE_ICON_COLUMNS - rowIcons.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.xs)
                    ) {
                        AddiyonOutlinedButton(
                            onClick = { resetForm() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(AI_CUSTOM_TONE_CANCEL_TAG)
                        ) {
                            Text(strings.aiCustomToneCancel)
                        }
                        AddiyonButton(
                            onClick = { saveForm() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(AI_CUSTOM_TONE_SAVE_TAG)
                        ) {
                            Text(strings.aiCustomToneSaveChanges)
                        }
                    }
                } else {
                    AddiyonButton(
                        onClick = { saveForm() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(AI_CUSTOM_TONE_SAVE_TAG)
                    ) {
                        Text(strings.aiCustomToneSave)
                    }
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = AddiyonSpacing.xl, vertical = AddiyonSpacing.md)
                    .testTag(AI_CUSTOM_TONE_LIST_TAG),
                verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.xs)
            ) {
                if (items.isEmpty()) {
                    item {
                        Text(
                            text = strings.aiCustomToneEmpty,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(
                        items = items,
                        key = { it.orderId }
                    ) { item ->
                        ReorderableItem(
                            state = reorderableLazyListState,
                            key = item.orderId
                        ) { isDragging ->
                            val isBuiltIn = item is ToneOrderItem.BuiltIn
                            val label = when (item) {
                                is ToneOrderItem.BuiltIn -> when (item.tab) {
                                    AiToneTab.Humanize -> strings.aiToneHumanize
                                    AiToneTab.Professional -> strings.aiToneProfessional
                                    AiToneTab.Casual -> strings.aiToneCasual
                                    AiToneTab.Formal -> strings.aiToneFormal
                                    AiToneTab.Friendly -> strings.aiToneFriendly
                                    AiToneTab.FixGrammar -> strings.aiToneFixGrammar
                                    AiToneTab.Shorten -> strings.aiToneShorten
                                    AiToneTab.Summarize -> strings.aiToneSummarize
                                }
                                is ToneOrderItem.Custom -> item.tone.title
                            }
                            val iconColor = when (item) {
                                is ToneOrderItem.BuiltIn -> when (item.tab) {
                                    AiToneTab.Humanize -> MaterialTheme.addiyonColors.aiToneIcons.humanize
                                    AiToneTab.Professional -> MaterialTheme.addiyonColors.aiToneIcons.professional
                                    AiToneTab.Casual -> MaterialTheme.addiyonColors.aiToneIcons.casual
                                    AiToneTab.Formal -> MaterialTheme.addiyonColors.aiToneIcons.formal
                                    AiToneTab.Friendly -> MaterialTheme.addiyonColors.aiToneIcons.friendly
                                    AiToneTab.FixGrammar -> MaterialTheme.addiyonColors.aiToneIcons.fixGrammar
                                    AiToneTab.Shorten -> MaterialTheme.addiyonColors.aiToneIcons.shorten
                                    AiToneTab.Summarize -> MaterialTheme.addiyonColors.aiToneIcons.summarize
                                }
                                is ToneOrderItem.Custom -> {
                                    customToneColor(item.tone.color)
                                }
                            }
                            val badge = if (isBuiltIn) {
                                strings.aiReorderInstructionsBuiltInBadge
                            } else {
                                strings.aiReorderInstructionsCustomBadge
                            }

                            val itemTag = when (item) {
                                is ToneOrderItem.BuiltIn -> "ai.custom.tone.item.${item.tab.name}"
                                is ToneOrderItem.Custom -> aiCustomToneItemTag(item.tone.id)
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(AddiyonSizes.appHeader)
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            onReorder(items.map { it.orderId })
                                        }
                                    )
                                    .testTag(itemTag),
                                shape = RoundedCornerShape(AddiyonRadii.group),
                                color = MaterialTheme.addiyonColors.cardBackground,
                                shadowElevation = if (isDragging) AddiyonElevation.overlay else AddiyonElevation.none,
                                tonalElevation = AddiyonElevation.none,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = AddiyonSpacing.md, end = AddiyonSpacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(iconColor.copy(alpha = 0.12f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (item) {
                                                is ToneOrderItem.BuiltIn -> when (item.tab) {
                                                    AiToneTab.Humanize -> Icons.Outlined.Face
                                                    AiToneTab.Professional -> Icons.Outlined.WorkOutline
                                                    AiToneTab.Casual -> Icons.Outlined.SentimentSatisfied
                                                    AiToneTab.Formal -> Icons.Outlined.AccountBalance
                                                    AiToneTab.Friendly -> Icons.Outlined.FavoriteBorder
                                                    AiToneTab.FixGrammar -> Icons.Outlined.Spellcheck
                                                    AiToneTab.Shorten -> Icons.AutoMirrored.Outlined.ShortText
                                                    AiToneTab.Summarize -> Icons.AutoMirrored.Outlined.Subject
                                                }
                                                is ToneOrderItem.Custom -> customToneIcon(item.tone.icon)
                                            },
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(AddiyonSizes.iconMedium)
                                        )
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = AddiyonSpacing.md),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.xs)
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (item is ToneOrderItem.Custom) {
                                            Spacer(Modifier.height(AddiyonSpacing.xxs))
                                            Text(
                                                text = item.tone.instruction,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (item is ToneOrderItem.Custom) {
                                        IconButton(
                                            onClick = { openEditForm(item.tone) },
                                            modifier = Modifier
                                                .size(AddiyonSizes.compact)
                                                .testTag(aiCustomToneEditTag(item.tone.id))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = strings.aiCustomToneEdit,
                                                tint = MaterialTheme.addiyonColors.icon,
                                                modifier = Modifier.size(AddiyonSizes.iconSmall)
                                            )
                                        }
                                        IconButton(
                                            onClick = { toneToDelete = item.tone },
                                            modifier = Modifier
                                                .size(AddiyonSizes.compact)
                                                .testTag(aiCustomToneRemoveTag(item.tone.id))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = strings.aiCustomToneRemove,
                                                tint = MaterialTheme.addiyonColors.icon,
                                                modifier = Modifier.size(AddiyonSizes.iconSmall)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier
                                            .size(AddiyonSizes.compact)
                                            .draggableHandle(
                                                onDragStarted = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragStopped = {
                                                    onReorder(items.map { it.orderId })
                                                }
                                            )
                                            .testTag("ai.custom.tone.reorder.${item.orderId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = strings.aiReorderInstructionsTitle,
                                            tint = MaterialTheme.addiyonColors.iconMuted,
                                            modifier = Modifier.size(AddiyonSizes.iconSmall)
                                        )
                                    }
                                }
                            }
                    }
                }
            }
        }
    }
}

    toneToDelete?.let { tone ->
        AlertDialog(
            onDismissRequest = { toneToDelete = null },
            title = {
                Text(
                    text = strings.aiCustomToneRemove,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Delete \"${tone.title}\"? This instruction will be permanently removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                AddiyonButton(
                    onClick = {
                        onRemove(tone.id)
                        toneToDelete = null
                    },
                    modifier = Modifier.testTag(AI_CUSTOM_TONE_CONFIRM_DELETE_TAG)
                ) {
                    Text(strings.aiCustomToneRemove)
                }
            },
            dismissButton = {
                AddiyonOutlinedButton(
                    onClick = { toneToDelete = null },
                    modifier = Modifier.testTag(AI_CUSTOM_TONE_CANCEL_DELETE_TAG)
                ) {
                    Text(strings.aiCustomToneCancel)
                }
            },
            shape = RoundedCornerShape(AddiyonRadii.large),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CustomToneColorPopup(
    selectedColor: String,
    showOnLeft: Boolean,
    strings: AiUiStrings,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val popupWidth = 220.dp
    val offsetX = with(density) {
        val w = popupWidth.toPx()
        val gap = 4.dp.toPx()
        val iconHalf = 20.dp.toPx()
        val halfW = w / 2
        val delta = halfW + gap + iconHalf
        if (showOnLeft) -delta.toInt() else delta.toInt()
    }
    val transformOrigin = if (showOnLeft) TransformOrigin(1f, 0.5f) else TransformOrigin(0f, 0.5f)
    Popup(
        alignment = Alignment.Center,
        offset = IntOffset(offsetX, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(
                transformOrigin = transformOrigin,
                animationSpec = tween(durationMillis = AddiyonMotion.standard)
            ) + fadeIn(animationSpec = tween(durationMillis = AddiyonMotion.standard)),
            exit = scaleOut(
                transformOrigin = transformOrigin,
                animationSpec = tween(durationMillis = AddiyonMotion.fast)
            ) + fadeOut(animationSpec = tween(durationMillis = AddiyonMotion.fast))
        ) {
            Surface(
                shape = RoundedCornerShape(AddiyonRadii.pill),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = AddiyonElevation.overlay
            ) {
                Row(
                    modifier = Modifier
                        .width(popupWidth)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = AddiyonSpacing.xs, vertical = AddiyonSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomToneColor.All.forEach { colorId ->
                        val color = customToneColor(colorId)
                        val selected = colorId == selectedColor
                        val checkTint = if (color.luminance() > 0.5f) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                        Surface(
                            onClick = { onPick(colorId) },
                            modifier = Modifier
                                .size(32.dp)
                                .semantics {
                                    contentDescription = customToneColorName(colorId, strings)
                                }
                                .testTag(aiCustomToneColorTag(colorId)),
                            shape = CircleShape,
                            color = color,
                            border = if (selected) {
                                BorderStroke(AddiyonBorders.selectedTone, MaterialTheme.colorScheme.onSurface)
                            } else {
                                null
                            }
                        ) {
                            if (selected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = checkTint,
                                        modifier = Modifier.size(AddiyonSizes.iconSmall)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun customToneIconName(iconId: String, strings: AiUiStrings): String = when (iconId) {
    CustomToneIcon.AUTO_AWESOME -> strings.aiCustomToneIconAutoAwesome
    CustomToneIcon.FACE -> strings.aiCustomToneIconFace
    CustomToneIcon.FAVORITE -> strings.aiCustomToneIconFavorite
    CustomToneIcon.STAR -> strings.aiCustomToneIconStar
    CustomToneIcon.BOLT -> strings.aiCustomToneIconBolt
    CustomToneIcon.PALETTE -> strings.aiCustomToneIconPalette
    CustomToneIcon.MUSIC_NOTE -> strings.aiCustomToneIconMusicNote
    CustomToneIcon.EMOJI_EMOTIONS -> strings.aiCustomToneIconEmojiEmotions
    CustomToneIcon.SENTIMENT_SATISFIED -> strings.aiCustomToneIconSentimentSatisfied
    CustomToneIcon.THUMB_UP -> strings.aiCustomToneIconThumbUp
    CustomToneIcon.WB_SUNNY -> strings.aiCustomToneIconWbSunny
    CustomToneIcon.LOCAL_FIRE_DEPARTMENT -> strings.aiCustomToneIconLocalFireDepartment
    CustomToneIcon.WATER_DROP -> strings.aiCustomToneIconWaterDrop
    CustomToneIcon.ECO -> strings.aiCustomToneIconEco
    CustomToneIcon.PETS -> strings.aiCustomToneIconPets
    CustomToneIcon.SCHOOL -> strings.aiCustomToneIconSchool
    CustomToneIcon.WORK_OUTLINE -> strings.aiCustomToneIconWorkOutline
    CustomToneIcon.ACCOUNT_BALANCE -> strings.aiCustomToneIconAccountBalance
    CustomToneIcon.SPELLCHECK -> strings.aiCustomToneIconSpellcheck
    CustomToneIcon.SHORTEN -> strings.aiCustomToneIconShorten
    CustomToneIcon.SUMMARIZE -> strings.aiCustomToneIconSummarize
    CustomToneIcon.VERIFIED -> strings.aiCustomToneIconVerified
    CustomToneIcon.DIAMOND -> strings.aiCustomToneIconDiamond
    CustomToneIcon.ROCKET -> strings.aiCustomToneIconRocket
    CustomToneIcon.CASTLE -> strings.aiCustomToneIconCastle
    CustomToneIcon.TERRAIN -> strings.aiCustomToneIconTerrain
    CustomToneIcon.FLIGHT -> strings.aiCustomToneIconFlight
    CustomToneIcon.COFFEE -> strings.aiCustomToneIconCoffee
    CustomToneIcon.ICECREAM -> strings.aiCustomToneIconIcecream
    CustomToneIcon.NIGHTLIGHT -> strings.aiCustomToneIconNightlight
    CustomToneIcon.QUESTION_ANSWER -> strings.aiCustomToneIconQuestionAnswer
    CustomToneIcon.AUTO_STORIES -> strings.aiCustomToneIconAutoStories
    else -> strings.aiCustomToneIconAutoAwesome
}

private fun customToneColorName(colorId: String, strings: AiUiStrings): String = when (colorId) {
    CustomToneColor.TEAL -> strings.aiCustomToneColorTeal
    CustomToneColor.INDIGO -> strings.aiCustomToneColorIndigo
    CustomToneColor.ORANGE -> strings.aiCustomToneColorOrange
    CustomToneColor.PURPLE -> strings.aiCustomToneColorPurple
    CustomToneColor.GREEN -> strings.aiCustomToneColorGreen
    CustomToneColor.ROSE -> strings.aiCustomToneColorRose
    CustomToneColor.BLUE -> strings.aiCustomToneColorBlue
    CustomToneColor.AMBER -> strings.aiCustomToneColorAmber
    CustomToneColor.CYAN -> strings.aiCustomToneColorCyan
    CustomToneColor.LIME -> strings.aiCustomToneColorLime
    CustomToneColor.PINK -> strings.aiCustomToneColorPink
    CustomToneColor.RED -> strings.aiCustomToneColorRed
    CustomToneColor.YELLOW -> strings.aiCustomToneColorYellow
    CustomToneColor.BROWN -> strings.aiCustomToneColorBrown
    CustomToneColor.GREY -> strings.aiCustomToneColorGrey
    CustomToneColor.DEEP_PURPLE -> strings.aiCustomToneColorDeepPurple
    else -> strings.aiCustomToneColorTeal
}

private const val SELECTED_ICON_FILL_ALPHA = 0.16f
