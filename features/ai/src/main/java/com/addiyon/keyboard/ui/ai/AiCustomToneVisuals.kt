package com.addiyon.keyboard.ui.ai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Castle
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.addiyon.keyboard.ai.CustomToneColor
import com.addiyon.keyboard.ai.CustomToneIcon
import com.addiyon.keyboard.ui.design.addiyonColors

internal fun customToneIcon(iconId: String): ImageVector = when (iconId) {
    CustomToneIcon.AUTO_AWESOME -> Icons.Outlined.AutoAwesome
    CustomToneIcon.FACE -> Icons.Outlined.Face
    CustomToneIcon.FAVORITE -> Icons.Outlined.Favorite
    CustomToneIcon.STAR -> Icons.Outlined.Star
    CustomToneIcon.BOLT -> Icons.Outlined.Bolt
    CustomToneIcon.PALETTE -> Icons.Outlined.Palette
    CustomToneIcon.MUSIC_NOTE -> Icons.Outlined.MusicNote
    CustomToneIcon.EMOJI_EMOTIONS -> Icons.Outlined.EmojiEmotions
    CustomToneIcon.SENTIMENT_SATISFIED -> Icons.Outlined.SentimentSatisfied
    CustomToneIcon.THUMB_UP -> Icons.Outlined.ThumbUp
    CustomToneIcon.WB_SUNNY -> Icons.Outlined.WbSunny
    CustomToneIcon.LOCAL_FIRE_DEPARTMENT -> Icons.Outlined.LocalFireDepartment
    CustomToneIcon.WATER_DROP -> Icons.Outlined.WaterDrop
    CustomToneIcon.ECO -> Icons.Outlined.Eco
    CustomToneIcon.PETS -> Icons.Outlined.Pets
    CustomToneIcon.SCHOOL -> Icons.Outlined.School
    CustomToneIcon.WORK_OUTLINE -> Icons.Outlined.WorkOutline
    CustomToneIcon.ACCOUNT_BALANCE -> Icons.Outlined.AccountBalance
    CustomToneIcon.SPELLCHECK -> Icons.Outlined.Spellcheck
    CustomToneIcon.SHORTEN -> Icons.AutoMirrored.Outlined.ShortText
    CustomToneIcon.SUMMARIZE -> Icons.AutoMirrored.Outlined.Subject
    CustomToneIcon.VERIFIED -> Icons.Outlined.Verified
    CustomToneIcon.DIAMOND -> Icons.Outlined.Diamond
    CustomToneIcon.ROCKET -> Icons.Outlined.Rocket
    CustomToneIcon.CASTLE -> Icons.Outlined.Castle
    CustomToneIcon.TERRAIN -> Icons.Outlined.Terrain
    CustomToneIcon.FLIGHT -> Icons.Outlined.Flight
    CustomToneIcon.COFFEE -> Icons.Outlined.Coffee
    CustomToneIcon.ICECREAM -> Icons.Outlined.Icecream
    CustomToneIcon.NIGHTLIGHT -> Icons.Outlined.Nightlight
    CustomToneIcon.QUESTION_ANSWER -> Icons.Outlined.QuestionAnswer
    CustomToneIcon.AUTO_STORIES -> Icons.Outlined.AutoStories
    else -> Icons.Outlined.AutoAwesome
}

@Composable
@ReadOnlyComposable
internal fun customToneColor(colorId: String): Color {
    val palette = MaterialTheme.addiyonColors.aiCustomToneColors
    return palette[colorId]
        ?: palette[CustomToneColor.Default]
        ?: MaterialTheme.colorScheme.primary
}
