package com.v2ray.ang.ui.compose.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.components.AuroraBackdrop
import com.v2ray.ang.ui.compose.components.GlassPanel
import com.v2ray.ang.ui.compose.components.liquidGlass
import com.v2ray.ang.ui.compose.theme.ConnectionTone
import com.v2ray.ang.ui.compose.theme.FirenetColors

/**
 * صفحه‌ی ورود.
 *
 * همان زبان بصری صفحه‌ی اصلی را دارد تا گذر از ورود به صفحه‌ی اصلی، پرش
 * بصری نداشته باشد. خطاها زیر همان فیلدی نشان داده می‌شوند که کاربر باید
 * اصلاحش کند، نه در یک پیام گذرا که ممکن است از دست برود.
 */
@Composable
fun LoginScreen(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    loading: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val canSubmit = username.isNotBlank() && password.isNotBlank() && !loading

    AuroraBackdrop(tone = ConnectionTone.Idle, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            Box(
                modifier = Modifier
                    .size(84.dp)
                    .liquidGlass(shape = CircleShape, tint = FirenetColors.Accent, intensity = 1.2f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "F",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = FirenetColors.Accent
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = FirenetColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = FirenetColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp)
            )

            Spacer(Modifier.height(30.dp))

            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text(stringResource(R.string.login_username)) },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        singleLine = true,
                        enabled = !loading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = fieldColors(),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.login_password)) },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        trailingIcon = {
                            val icon = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility
                            Icon(
                                imageVector = icon,
                                contentDescription = stringResource(R.string.login_toggle_password),
                                modifier = Modifier.clickable { passwordVisible = !passwordVisible }
                            )
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        singleLine = true,
                        enabled = !loading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            if (canSubmit) onSubmit()
                        }),
                        colors = fieldColors(),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            text = error.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = FirenetColors.Blocked
                        )
                    }

                    PrimaryButton(
                        text = stringResource(R.string.login_submit),
                        enabled = canSubmit,
                        loading = loading,
                        onClick = {
                            keyboard?.hide()
                            onSubmit()
                        }
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = stringResource(R.string.login_footer),
                style = MaterialTheme.typography.labelSmall,
                color = FirenetColors.TextDisabled,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        FirenetColors.Accent.copy(alpha = alpha),
                        FirenetColors.AccentSoft.copy(alpha = alpha)
                    )
                )
            )
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.4.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FirenetColors.TextPrimary,
    unfocusedTextColor = FirenetColors.TextPrimary,
    disabledTextColor = FirenetColors.TextDisabled,
    focusedBorderColor = FirenetColors.Accent,
    unfocusedBorderColor = FirenetColors.GlassStroke,
    disabledBorderColor = FirenetColors.GlassStrokeSoft,
    focusedLabelColor = FirenetColors.AccentSoft,
    unfocusedLabelColor = FirenetColors.TextTertiary,
    focusedLeadingIconColor = FirenetColors.AccentSoft,
    unfocusedLeadingIconColor = FirenetColors.TextTertiary,
    focusedTrailingIconColor = FirenetColors.AccentSoft,
    unfocusedTrailingIconColor = FirenetColors.TextTertiary,
    cursorColor = FirenetColors.Accent,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent
)
