package com.fieldtrack.app.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.fieldtrack.app.core.components.*
import com.fieldtrack.app.core.designsystem.*

// ─── LoginScreen (FINAL — Indigo) ─────────────────────────────────────────────
// Matches Stitch "Employee Login" layout exactly.
// Uses indigo gradient on logo mark and CTA button.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LoginScreen(
    onLoginSuccess: (role: String) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var showPwd     by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess((uiState as LoginUiState.Success).role)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Subtle gradient top decoration (indigo)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.XXXL * 5)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            Color.Transparent,
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.MD, vertical = Spacing.XXL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.LG),
        ) {
            Spacer(modifier = Modifier.height(Spacing.XL))

            // ── Logo mark ─────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.SM),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(GradientPrimaryStart, GradientPrimaryEnd),
                                start  = Offset(0f, 0f),
                                end    = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                            ),
                            shape = FtShape.Card,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("FT", style = FtTypography.H2, color = Color.White)
                }
                Text(
                    text  = "FieldTrack",
                    style = FtTypography.H1,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text  = "Field Operations Platform",
                    style = FtTypography.BodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.SM))

            // ── Login card ────────────────────────────────────────────────────
            FtCard(modifier = Modifier.fillMaxWidth()) {
                Text("Sign In", style = FtTypography.H3, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Enter your credentials to continue",
                    style = FtTypography.BodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Spacing.MD))

                InputField(
                    value         = email,
                    onValueChange = { email = it; viewModel.resetError() },
                    label         = "Email Address",
                    placeholder   = "name@company.com",
                    leadingIcon   = {
                        Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction    = ImeAction.Next,
                    ),
                    isError = uiState is LoginUiState.Error,
                )

                Spacer(Modifier.height(Spacing.MD))

                InputField(
                    value         = password,
                    onValueChange = { password = it; viewModel.resetError() },
                    label         = "Password",
                    placeholder   = "Your password",
                    leadingIcon   = {
                        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon  = {
                        IconButton(onClick = { showPwd = !showPwd }) {
                            Icon(
                                if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPwd) "Hide password" else "Show password",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    visualTransformation = if (showPwd) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction    = ImeAction.Done,
                    ),
                    isError      = uiState is LoginUiState.Error,
                    errorMessage = (uiState as? LoginUiState.Error)?.message,
                )

                Spacer(Modifier.height(Spacing.LG))

                PrimaryButton(
                    text     = "Sign In",
                    onClick  = { viewModel.login(email, password) },
                    loading  = uiState is LoginUiState.Loading,
                    fullWidth = true,
                )
            }

            Text(
                text      = "Secure access powered by FieldTrack",
                style     = FtTypography.BodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
