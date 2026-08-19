package com.addiyon.keyboard.features.appshell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.suggestion.PersonalDictionary
import com.addiyon.keyboard.ui.design.AddiyonContentSection
import com.addiyon.keyboard.ui.design.AddiyonRadii
import com.addiyon.keyboard.ui.design.addiyonColors
import com.addiyon.keyboard.ui.settings.KeyboardPrefs

data class KeyboardPersonalDictionaryCopy(
    val title: String,
    val back: String,
    val empty: String,
    val clearAll: String,
    val delete: String,
    val wordSingular: String,
    val wordPlural: String
)

private fun languageDisplayName(languageId: String): String = when (languageId) {
    "am-ET" -> "አማርኛ"
    "en-US" -> "English"
    "om-ET" -> "Afaan Oromoo"
    else -> languageId
}

@Composable
fun KeyboardPersonalDictionaryScreen(
    copy: KeyboardPersonalDictionaryCopy,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    languageIds: List<String> = emptyList(),
) {
    val context = LocalContext.current
    var dictionary by remember {
        mutableStateOf(PersonalDictionary.decode(KeyboardPrefs.personalDictionary(context)))
    }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val hasMultipleTabs = languageIds.size > 1
    val currentLanguageId = if (hasMultipleTabs) {
        languageIds.getOrNull(selectedTabIndex) ?: languageIds.first()
    } else {
        languageIds.firstOrNull()
    }

    val words = remember(dictionary, currentLanguageId, hasMultipleTabs) {
        if (hasMultipleTabs && currentLanguageId != null) {
            dictionary.words(currentLanguageId).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        } else {
            dictionary.allWords().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        }
    }

    fun save() {
        KeyboardPrefs.setPersonalDictionary(context, dictionary.encode())
        dictionary = PersonalDictionary.decode(dictionary.encode())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            KeyboardPageTopBar(
                title = copy.title,
                onBack = onBack,
                backContentDescription = copy.back
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            if (hasMultipleTabs) {
                SecondaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AddiyonRadii.group)),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    languageIds.forEachIndexed { index, langId ->
                        val selected = selectedTabIndex == index
                        Tab(
                            selected = selected,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = languageDisplayName(langId),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            },
                            modifier = Modifier.testTag("personalDictionary.tab.$langId")
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (words.isEmpty()) {
                AddiyonContentSection(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = copy.empty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            if (hasMultipleTabs && currentLanguageId != null) {
                                dictionary.clear(currentLanguageId)
                            } else {
                                dictionary.clear()
                            }
                            save()
                        },
                        modifier = Modifier.testTag("personalDictionary.clearAll")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(text = copy.clearAll)
                    }
                }
                AddiyonContentSection(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                ) {
                    words.forEachIndexed { index, word ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = word,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            IconButton(
                                onClick = {
                                    if (hasMultipleTabs && currentLanguageId != null) {
                                        dictionary.remove(currentLanguageId, word)
                                    } else {
                                        dictionary.remove(word)
                                    }
                                    save()
                                },
                                modifier = Modifier.testTag("personalDictionary.delete.$word")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = copy.delete,
                                    tint = MaterialTheme.addiyonColors.icon
                                )
                            }
                        }
                        if (index < words.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "${words.size} ${if (words.size == 1) copy.wordSingular else copy.wordPlural}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
