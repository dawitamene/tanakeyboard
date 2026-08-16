package com.addiyon.keyboard.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.addiyon.keyboard.ai.CustomTone
import com.addiyon.keyboard.ai.CustomToneColor
import com.addiyon.keyboard.ai.CustomToneIcon
import com.addiyon.keyboard.features.appshell.KeyboardPageTopBar
import com.addiyon.keyboard.ui.design.AddiyonBorders
import com.addiyon.keyboard.ui.design.AddiyonElevation
import com.addiyon.keyboard.ui.design.AddiyonInputField
import com.addiyon.keyboard.ui.design.AddiyonRadii
import com.addiyon.keyboard.ui.design.AddiyonSizes
import com.addiyon.keyboard.ui.design.AddiyonSpacing

const val AI_CUSTOM_TONE_TITLE_FIELD_TAG = "ai.custom.tone.title.field"
const val AI_CUSTOM_TONE_FIELD_TAG = "ai.custom.tone.field"
const val AI_CUSTOM_TONE_SAVE_TAG = "ai.custom.tone.save"
const val AI_CUSTOM_TONE_CANCEL_TAG = "ai.custom.tone.cancel"
const val AI_CUSTOM_TONE_LIST_TAG = "ai.custom.tone.list"
const val AI_CUSTOM_TONE_ICON_GRID_TAG = "ai.custom.tone.icon.grid"
const val CUSTOM_TONE_ICON_ROWS = 4
const val CUSTOM_TONE_ICON_COLUMNS = 8
const val CUSTOM_TONE_COLOR_POPUP_PER_ROW = 4

fun aiCustomToneItemTag(id: String): String = "ai.custom.tone.item.$id"
fun aiCustomToneEditTag(id: String): String = "ai.custom.tone.edit.$id"
fun aiCustomToneRemoveTag(id: String): String = "ai.custom.tone.remove.$id"
fun aiCustomToneIconTag(iconId: String): String = "ai.custom.tone.icon.$iconId"
fun aiCustomToneColorTag(colorId: String): String = "ai.custom.tone.color.$colorId"

@Composable
fun AiCustomToneContent(
    customTones: List<CustomTone>,
    strings: AiUiStrings,
    onBack: () -> Unit,
    onSave: (title: String, instruction: String, icon: String, color: String) -> Unit,
    onUpdate: (id: String, title: String, instruction: String, icon: String, color: String) -> Unit,
    onRemove: (String) -> Unit
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var instruction by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(CustomToneIcon.Default) }
    var selectedColor by remember { mutableStateOf(CustomToneColor.Default) }
    var showColorPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val isEditing = editingId != null

    fun resetForm() {
        editingId = null
        title = ""
        instruction = ""
        selectedIcon = CustomToneIcon.Default
        selectedColor = CustomToneColor.Default
        showColorPicker = false
        error = null
    }

    fun onIconTap(iconId: String) {
        if (iconId == selectedIcon && showColorPicker) {
            showColorPicker = false
        } else {
            selectedIcon = iconId
            showColorPicker = true
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            KeyboardPageTopBar(
                title = strings.aiCustomToneTitle,
                onBack = onBack,
                backContentDescription = strings.back
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AddiyonSpacing.xl, vertical = AddiyonSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.sm)
        ) {
            Text(
                text = strings.aiCustomToneDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isEditing) strings.aiCustomToneEditHeading else strings.aiCustomToneNewHeading,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
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
                    CustomToneIcon.All.chunked(CUSTOM_TONE_ICON_COLUMNS).forEach { rowIcons ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.xxs)
                        ) {
                            rowIcons.forEach { iconId ->
                                val isSelected = selectedIcon == iconId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(AddiyonSizes.keyboardAction)
                                        .clip(RoundedCornerShape(AddiyonRadii.small))
                                        .background(
                                            if (isSelected) {
                                                customToneColor(selectedColor).copy(alpha = SELECTED_ICON_FILL_ALPHA)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
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
                                    if (isSelected && showColorPicker) {
                                        CustomToneColorPopup(
                                            selectedColor = selectedColor,
                                            strings = strings,
                                            onPick = { colorId ->
                                                selectedColor = colorId
                                                showColorPicker = false
                                            },
                                            onDismiss = { showColorPicker = false }
                                        )
                                    }
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
                    OutlinedButton(
                        onClick = { resetForm() },
                        modifier = Modifier
                            .weight(1f)
                            .height(AddiyonSizes.formControl)
                            .testTag(AI_CUSTOM_TONE_CANCEL_TAG),
                        shape = RoundedCornerShape(AddiyonRadii.pill)
                    ) {
                        Text(strings.aiCustomToneCancel)
                    }
                    Button(
                        onClick = { saveForm() },
                        modifier = Modifier
                            .weight(1f)
                            .height(AddiyonSizes.formControl)
                            .testTag(AI_CUSTOM_TONE_SAVE_TAG),
                        shape = RoundedCornerShape(AddiyonRadii.pill)
                    ) {
                        Text(strings.aiCustomToneSaveChanges)
                    }
                }
            } else {
                Button(
                    onClick = { saveForm() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AddiyonSizes.formControl)
                        .testTag(AI_CUSTOM_TONE_SAVE_TAG),
                    shape = RoundedCornerShape(AddiyonRadii.pill)
                ) {
                    Text(strings.aiCustomToneSave)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Text(
                text = strings.aiCustomToneListHeading,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AI_CUSTOM_TONE_LIST_TAG),
                verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.xs)
            ) {
                if (customTones.isEmpty()) {
                    Text(
                        text = strings.aiCustomToneEmpty,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    customTones.forEach { tone ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(aiCustomToneItemTag(tone.id)),
                            shape = RoundedCornerShape(AddiyonRadii.group),
                            color = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = AddiyonSpacing.md, end = AddiyonSpacing.xxs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = customToneIcon(tone.icon),
                                    contentDescription = null,
                                    tint = customToneColor(tone.color),
                                    modifier = Modifier
                                        .size(AddiyonSizes.iconMedium)
                                        .padding(end = AddiyonSpacing.xs)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = AddiyonSpacing.xs)
                                ) {
                                    Text(
                                        text = tone.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(AddiyonSpacing.xxs))
                                    Text(
                                        text = tone.instruction,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingId = tone.id
                                        title = tone.title
                                        instruction = tone.instruction
                                        selectedIcon = tone.icon
                                        selectedColor = tone.color
                                        showColorPicker = false
                                        error = null
                                    },
                                    modifier = Modifier
                                        .size(AddiyonSizes.minimumTouchTarget)
                                        .testTag(aiCustomToneEditTag(tone.id))
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = strings.aiCustomToneEdit,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onRemove(tone.id) },
                                    modifier = Modifier
                                        .size(AddiyonSizes.minimumTouchTarget)
                                        .testTag(aiCustomToneRemoveTag(tone.id))
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = strings.aiCustomToneRemove,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun CustomToneColorPopup(
    selectedColor: String,
    strings: AiUiStrings,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val popupLift = with(LocalDensity.current) {
        (AddiyonSizes.compact * (CUSTOM_TONE_COLOR_POPUP_PER_ROW * 2) +
            AddiyonSpacing.xs * 2 + AddiyonSpacing.xxs + AddiyonSpacing.xs).roundToPx()
    }
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, -popupLift),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        Surface(
            shape = RoundedCornerShape(AddiyonRadii.medium),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AddiyonElevation.overlay
        ) {
            Column(
                modifier = Modifier.padding(AddiyonSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.xxs)
            ) {
                CustomToneColor.All.chunked(CUSTOM_TONE_COLOR_POPUP_PER_ROW).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.xxs)) {
                        rowColors.forEach { colorId ->
                            val color = customToneColor(colorId)
                            val selected = colorId == selectedColor
                            Surface(
                                onClick = { onPick(colorId) },
                                modifier = Modifier
                                    .size(AddiyonSizes.compact)
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
                                            tint = MaterialTheme.colorScheme.onSurface,
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
    else -> strings.aiCustomToneColorTeal
}

private const val SELECTED_ICON_FILL_ALPHA = 0.16f
