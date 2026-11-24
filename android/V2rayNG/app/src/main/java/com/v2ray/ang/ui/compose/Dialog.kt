package com.v2ray.ang.ui.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

@Composable
fun ConfirmDialog(
    title: String? = null,
    message: String,
    confirmText: String = stringResource(R.string.action_ok),
    dismissText: String? = stringResource(R.string.action_cancel),
    confirmIconRes: Int? = null,
    destructiveConfirm: Boolean = false,
    messageTextAlign: TextAlign = TextAlign.Start,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmFocusRequester = remember { FocusRequester() }
    val dismissFocusRequester = remember { FocusRequester() }

    LaunchedEffect(dismissText) {
        if (dismissText != null) dismissFocusRequester.requestFocus()
        else confirmFocusRequester.requestFocus()
    }

    GlassDialog(onDismissRequest = onDismiss) {
        if (title != null) GlassDialogHeader(title)
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = glassMenuItemInkColor(),
            textAlign = messageTextAlign
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (dismissText != null) {
                GlassDialogPillButton(
                    text = dismissText,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).focusRequester(dismissFocusRequester)
                )
            }
            GlassDialogPillButton(
                text = confirmText,
                onClick = { onConfirm(); onDismiss() },
                modifier = Modifier.weight(1f).focusRequester(confirmFocusRequester),
                primary = !destructiveConfirm,
                destructive = destructiveConfirm,
                leadingIcon = confirmIconRes
            )
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    itemName: String? = null,
) {
    ConfirmDialog(
        message = if (itemName.isNullOrBlank()) message else "$message\n\n$itemName",
        messageTextAlign = TextAlign.Center,
        confirmText = stringResource(R.string.action_delete),
        confirmIconRes = R.drawable.ic_delete_24dp,
        destructiveConfirm = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

data class InputField(
    val label: String,
    val value: String,
    val singleLine: Boolean = true,
    val visualTransformation: VisualTransformation = VisualTransformation.None
)

@Composable
fun InputDialog(
    title: String,
    fields: List<InputField>,
    onFieldChange: (Int, String) -> Unit,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassDialog(onDismissRequest = onDismiss) {
        GlassDialogHeader(title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            fields.forEachIndexed { index, field ->
                OutlinedTextField(
                    value = field.value,
                    onValueChange = { onFieldChange(index, it) },
                    label = { Text(field.label) },
                    singleLine = field.singleLine,
                    maxLines = if (field.singleLine) 1 else 5,
                    visualTransformation = field.visualTransformation,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = GlassMenuTokens.action,
                        selectionColors = TextSelectionColors(
                            handleColor = GlassMenuTokens.action,
                            backgroundColor = GlassMenuTokens.action.copy(alpha = 0.4f)
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassDialogPillButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            GlassDialogPillButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                primary = true
            )
        }
    }
}

@Composable
fun QRCodeDialog(
    bitmap: Bitmap?,
    onDismiss: () -> Unit
) {
    if (bitmap == null) return
    GlassDialog(onDismissRequest = onDismiss) {
        GlassDialogHeader(stringResource(R.string.acc_qr_code))
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.acc_qr_code),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        }
        Spacer(Modifier.height(18.dp))
        GlassDialogPillButton(
            text = stringResource(R.string.action_close),
            onClick = onDismiss
        )
    }
}

/**
 * When showRadio is true, displays RadioButton (single selection mode);
 * otherwise, plain clickable list mode.
 * The selectedOption parameter is used to highlight the selected item only when showRadio is true.
 */
@Composable
fun <T> SelectListDialog(
    options: List<T>,
    optionText: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    selectedOption: T? = null,
    showRadio: Boolean = false
) {
    GlassDialog(onDismissRequest = onDismiss) {
        if (title != null) GlassDialogHeader(title)
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(options) { option ->
                val isSelected = option == selectedOption
                val rowModifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (showRadio) Modifier.selectable(
                            selected = isSelected,
                            onClick = { onSelected(option) },
                            role = Role.RadioButton
                        ) else Modifier.clickable { onSelected(option) }
                    )
                    .background(
                        if (isSelected) GlassTints.periwinkle.copy(alpha = 0.75f) else Color.Transparent,
                        RoundedCornerShape(GlassMenuTokens.actionRowRadius)
                    )
                    .padding(vertical = 10.dp, horizontal = 12.dp)
                Row(
                    modifier = rowModifier,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showRadio) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = optionText(option),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = glassMenuItemInkColor(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassDialogPillButton(
            text = stringResource(R.string.action_cancel),
            onClick = onDismiss
        )
    }
}
