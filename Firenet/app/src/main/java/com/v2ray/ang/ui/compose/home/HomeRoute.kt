package com.v2ray.ang.ui.compose.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.theme.FirenetColors
import kotlinx.coroutines.launch

/**
 * پوسته‌ی صفحه‌ی اصلی: کشوی کناری، فهرست سرورها، گفت‌وگوها و نوار پیام.
 *
 * جدا نگه‌داشتن این لایه از [HomeScreen] باعث می‌شود خود صفحه‌ی اصلی هیچ وابستگی
 * به ناوبری نداشته باشد و بتوان آن را مستقل پیش‌نمایش گرفت.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    state: HomeUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectServer: (String) -> Unit,
    onSelectAutoLocation: () -> Unit,
    onTestAllServers: () -> Unit,
    onPing: () -> Unit,
    onToggleKillSwitch: () -> Unit,
    onMenuAction: (MenuAction) -> Unit,
    onMessageShown: () -> Unit,
    onOpenUpdate: (String) -> Unit,
    onDismissOptionalUpdate: () -> Unit,
    onRetryAccount: () -> Unit,
    onSignOutConfirmed: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showServerSheet by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message.text, duration = SnackbarDuration.Short)
        onMessageShown()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.Transparent,
                drawerContentColor = FirenetColors.TextPrimary
            ) {
                SideMenu(
                    state = state,
                    onToggleKillSwitch = onToggleKillSwitch,
                    onAction = { action ->
                        scope.launch { drawerState.close() }
                        if (action == MenuAction.Logout) {
                            showLogoutDialog = true
                        } else {
                            onMenuAction(action)
                        }
                    }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeScreen(
                state = state,
                onMenuClick = { scope.launch { drawerState.open() } },
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onServerClick = { showServerSheet = true },
                onPingClick = onPing
            )

            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp, start = 20.dp, end = 20.dp)
            ) { data ->
                Snackbar(
                    containerColor = FirenetColors.BackdropBottom,
                    contentColor = FirenetColors.TextPrimary,
                    shape = MaterialTheme.shapes.large
                ) { Text(data.visuals.message) }
            }
        }
    }

    if (showServerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showServerSheet = false },
            sheetState = sheetState,
            containerColor = FirenetColors.BackdropMid,
            contentColor = FirenetColors.TextPrimary
        ) {
            ServerPickerSheet(
                servers = state.servers,
                lastUpdated = state.lastUpdated,
                onRefreshConfigs = { viewModel.refreshConfigsManually() },
                onSelect = { guid ->
                    onSelectServer(guid)
                    scope.launch {
                        sheetState.hide()
                        showServerSheet = false
                    }
                },
                onTestAll = onTestAllServers,
                autoSelected = state.autoLocation,
                autoSearching = state.autoSearching,
                onSelectAuto = {
                    onSelectAutoLocation()
                    scope.launch {
                        sheetState.hide()
                        showServerSheet = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showLogoutDialog) {
        FirenetDialog(
            title = stringResource(R.string.menu_logout_confirm),
            message = stringResource(R.string.menu_logout_message),
            confirmLabel = stringResource(R.string.menu_logout),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                showLogoutDialog = false
                onSignOutConfirmed()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    if (state.accountSuspended) {
        FirenetDialog(
            title = stringResource(R.string.account_suspended_title),
            message = stringResource(R.string.account_suspended_message),
            confirmLabel = stringResource(R.string.account_retry),
            dismissLabel = stringResource(R.string.menu_logout),
            cancelable = false,
            onConfirm = onRetryAccount,
            onDismiss = onSignOutConfirmed
        )
    }

    // به‌روزرسانی اجباری: راهی برای بستن ندارد، چون نسخه‌ی فعلی دیگر پشتیبانی نمی‌شود.
    state.forcedUpdateUrl?.let { url ->
        FirenetDialog(
            title = stringResource(R.string.update_title),
            message = stringResource(R.string.update_message),
            confirmLabel = stringResource(R.string.update_now),
            dismissLabel = null,
            cancelable = false,
            onConfirm = { onOpenUpdate(url) },
            onDismiss = {}
        )
    }

    state.optionalUpdateUrl?.let { url ->
        FirenetDialog(
            title = stringResource(R.string.update_title),
            message = stringResource(R.string.update_message),
            confirmLabel = stringResource(R.string.update_now),
            dismissLabel = stringResource(R.string.update_later),
            onConfirm = {
                onDismissOptionalUpdate()
                onOpenUpdate(url)
            },
            onDismiss = onDismissOptionalUpdate
        )
    }
}

/** گفت‌وگوی استاندارد برنامه با ظاهر یکسان در همه‌ی صفحات. */
@Composable
fun FirenetDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelable: Boolean = true
) {
    AlertDialog(
        onDismissRequest = { if (cancelable) onDismiss() },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = FirenetColors.Accent)
            }
        },
        dismissButton = dismissLabel?.let {
            {
                TextButton(onClick = onDismiss) {
                    Text(it, color = FirenetColors.TextTertiary)
                }
            }
        },
        containerColor = FirenetColors.BackdropBottom,
        titleContentColor = FirenetColors.TextPrimary,
        textContentColor = FirenetColors.TextSecondary,
        properties = DialogProperties(
            dismissOnBackPress = cancelable,
            dismissOnClickOutside = cancelable
        )
    )
}
