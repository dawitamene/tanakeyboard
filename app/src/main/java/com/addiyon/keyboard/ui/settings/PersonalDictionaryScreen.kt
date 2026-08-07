package com.addiyon.keyboard.ui.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.suggestion.PersonalDictionary
import com.addiyon.keyboard.ui.AppPageTopBar
import com.addiyon.keyboard.ui.i18n.LocalAppStrings

@Composable
fun PersonalDictionaryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var dictionary by remember {
        mutableStateOf(PersonalDictionary.decode(KeyboardPrefs.personalDictionary(context)))
    }
    var words by remember {
        mutableStateOf(dictionary.allWords().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it }))
    }

    fun save() {
        KeyboardPrefs.setPersonalDictionary(context, dictionary.encode())
        words = dictionary.allWords().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppPageTopBar(
                title = strings.personalDictionaryTitle,
                onBack = onBack,
                backContentDescription = strings.back
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
            if (words.isEmpty()) {
                GroupCard(modifier = Modifier.widthIn(max = 720.dp)) {
                    Text(
                        text = strings.personalDictionaryEmpty,
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
                            dictionary.clear()
                            save()
                        },
                        modifier = Modifier.testTag("personalDictionary.clearAll")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(text = strings.personalDictionaryClearAll)
                    }
                }
                GroupCard(modifier = Modifier.widthIn(max = 720.dp)) {
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
                                    dictionary.remove(word)
                                    save()
                                },
                                modifier = Modifier.testTag("personalDictionary.delete.$word")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = strings.delete,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    text = "${words.size} ${if (words.size == 1) "word" else "words"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
