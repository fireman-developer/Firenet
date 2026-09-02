package com.v2ray.ang.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * «آیا این صفحه همین حالا جلوی چشم کاربر است؟»
 *
 * `rememberInfiniteTransition` با رفتن برنامه به پس‌زمینه متوقف نمی‌شود؛ تا وقتی
 * ترکیب زنده باشد در هر فریم مقدار تازه می‌دهد و رسم دوباره راه می‌افتد. برای
 * صفحه‌ای که ساعت‌ها باز می‌ماند، این تفاوت بین مصرف صفر و مصرف دائمی پردازنده
 * و باتری است.
 *
 * هر انیمیشن بی‌پایانی در این برنامه باید به این مقدار گره بخورد.
 */
@Composable
fun rememberIsResumed(): State<Boolean> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            state.value = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return state
}
