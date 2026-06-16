package com.limdale.llm.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.limdale.llm.LLMSettings
import com.limdale.llm.LLMStatus
import com.limdale.llm.android.repository.AndroidModelRepository
import com.limdale.llm.litertlm.LiteRtLLM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChatScreen() }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    ChatScreen()
}

@Composable
private fun ChatScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val llmLibrary = remember {
        LiteRtLLM(
            modelRepository = AndroidModelRepository(context)
        )
    }

    val llmState = llmLibrary.status.collectAsState()

    val messages = remember { mutableStateListOf<String>() }
    val inputState = rememberTextFieldState()
    val listState = rememberLazyListState()

    LaunchedEffect(llmLibrary) {
        scope.launch {
            withContext(Dispatchers.IO) {
                llmLibrary.initialize(
                    llmSettings = LLMSettings(
                        temperature = 5.0,
                        cacheDir = context.cacheDir.path
                    )
                )
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages) { Text(it, modifier = Modifier.fillMaxWidth()) }
        }
        if (llmState.value !is LLMStatus.Ready) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    when (llmState.value) {
                        is LLMStatus.Initializing -> "Initializing..."
                        is LLMStatus.Downloading -> "${(llmState.value as LLMStatus.Downloading).progress.toInt()}% - Downloading LLM"
                        is LLMStatus.Responding -> "LLM is responding..."
                        else -> ""
                    }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                state = inputState,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                enabled = llmState.value is LLMStatus.Ready,
                onClick = {
                    if (inputState.text.isNotBlank()) {
                        val input = inputState.text.toString()
                        messages.add("You: $input")
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val response = llmLibrary.prompt(input)
                                messages.add("LLM: $response\n")
                            }
                        }
                        inputState.clearText()
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}
