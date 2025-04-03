package dev.lucy.momentsintime

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.lucy.momentsintime.ui.theme.MyApplicationTheme
import java.time.LocalDate

class ParticipantInputActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ParticipantInputScreen(
                        onStartExperiment = { participantId ->
                            navigateToInstructions(participantId)
                        }
                    )
                }
            }
        }
    }
    
    private fun navigateToInstructions(participantId: Int) {
        val intent = Intent(this, InstructionActivity::class.java).apply {
            putExtra("PARTICIPANT_ID", participantId)
            putExtra("DATE", LocalDate.now().toString())
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantInputScreen(onStartExperiment: (Int) -> Unit) {
    var participantIdText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Brain Recording Experiment",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        OutlinedTextField(
            value = participantIdText,
            onValueChange = { 
                participantIdText = it
                isError = false
            },
            label = { Text("Participant ID") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = isError,
            supportingText = {
                if (isError) {
                    Text("Participant ID must be a positive number")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        
        Button(
            onClick = {
                val participantId = participantIdText.toIntOrNull()
                if (participantId != null && ExperimentConfig.isValidParticipantId(participantId)) {
                    onStartExperiment(participantId)
                } else {
                    isError = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Start Experiment")
        }
    }
}
