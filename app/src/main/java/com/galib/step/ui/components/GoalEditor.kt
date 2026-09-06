package com.galib.step.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import com.galib.step.util.Formatters

@Composable
fun GoalEditor(
    goal: Int,
    onGoalChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    keyboardTrigger: Boolean = false,
    labelResId: Int = com.galib.step.R.string.steps_per_day
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            var isEditing by remember { mutableStateOf(true) }
            val initialStr = goal.toString()
            var textValue by remember(goal) { mutableStateOf(TextFieldValue(initialStr, TextRange(initialStr.length))) }
            val focusRequester = remember { FocusRequester() }

            if (isEditing) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { newTextFieldValue ->
                        val newText = newTextFieldValue.text
                        if (newText.all { it.isDigit() } && newText.length <= 9) {
                            textValue = newTextFieldValue
                        }
                    },
                    textStyle = MaterialTheme.typography.displayMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            textValue.text.toIntOrNull()?.let { onGoalChange(it) }
                            isEditing = false
                        }
                    ),
                    visualTransformation = NumberCommaTransformation(),
                    modifier = Modifier.focusRequester(focusRequester)
                )
                val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                LaunchedEffect(keyboardTrigger) {
                    if (keyboardTrigger) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                }
            } else {
                Text(
                    text = Formatters.steps(goal.toLong()),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            val str = goal.toString()
                            textValue = TextFieldValue(str, TextRange(str.length))
                            isEditing = true
                        }
                )
            }
            Text(
                text = androidx.compose.ui.res.stringResource(labelResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }


    }
}

class NumberCommaTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val original = text.text
        if (original.isEmpty()) return androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)

        val out = StringBuilder()
        for (i in original.indices) {
            out.append(original[i])
            val distToEnd = original.length - i - 1
            if (distToEnd > 0 && distToEnd % 3 == 0) {
                out.append(",")
            }
        }
        val formatted = out.toString()

        val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var commas = 0
                for (i in 0 until offset) {
                    val distToEnd = original.length - i - 1
                    if (distToEnd > 0 && distToEnd % 3 == 0) commas++
                }
                return offset + commas
            }

            override fun transformedToOriginal(offset: Int): Int {
                var commas = 0
                for (i in 0 until offset) {
                    if (formatted.getOrNull(i) == ',') commas++
                }
                return offset - commas
            }
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(formatted), offsetMapping)
    }
}
