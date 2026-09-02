package com.v2ray.ang.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.firebase.messaging.FirebaseMessaging
import com.v2ray.ang.R
import com.v2ray.ang.data.auth.AuthRepository
import com.v2ray.ang.data.auth.TokenStore
import com.v2ray.ang.net.ApiClient
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.ui.compose.login.LoginScreen
import com.v2ray.ang.ui.compose.theme.FirenetTheme

/**
 * صفحه‌ی ورود.
 *
 * وضعیت فرم آن‌قدر کوچک است که نگه‌داشتنش در خود اکتیویتی ساده‌تر از ساختن یک
 * ViewModel جداگانه است؛ اما چون ورود یک عملیات شبکه‌ای است، وضعیت «در حال
 * ارسال» به‌روشنی جدا نگه داشته شده تا کاربر نتواند دو بار درخواست بفرستد.
 */
class LoginActivity : AppCompatActivity() {

    private val repo by lazy { AuthRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!TokenStore.token(this).isNullOrEmpty()) {
            goToMain()
            return
        }

        val sessionExpired = intent?.getBooleanExtra(MainActivity.EXTRA_SESSION_EXPIRED, false) == true

        setContent {
            FirenetTheme {
                var username by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var loading by remember { mutableStateOf(false) }
                var error by remember {
                    mutableStateOf(
                        if (sessionExpired) getString(R.string.session_expired) else null
                    )
                }

                LoginScreen(
                    username = username,
                    password = password,
                    onUsernameChange = { username = it; error = null },
                    onPasswordChange = { password = it; error = null },
                    loading = loading,
                    error = error,
                    onSubmit = {
                        loading = true
                        error = null
                        submit(
                            username = username.trim(),
                            password = password,
                            onError = { message ->
                                loading = false
                                error = message
                            }
                        )
                    }
                )
            }
        }
    }

    /**
     * ورود و سپس اعتبارسنجی توکن با یک درخواست وضعیت.
     *
     * دلیل درخواست دوم این است که توکنی که سرور می‌دهد ممکن است بلافاصله به
     * دلایل دیگری (مثلاً مسدود بودن حساب) غیرقابل استفاده باشد؛ بهتر است این را
     * همین‌جا بفهمیم تا کاربر با صفحه‌ی اصلیِ خالی روبه‌رو نشود.
     */
    private fun submit(username: String, password: String, onError: (String) -> Unit) {
        repo.login(username, password) { result ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.fold(
                    onSuccess = { token -> verifyAndEnter(token, username, onError) },
                    onFailure = { onError(translateLoginError(it.message)) }
                )
            }
        }
    }

    private fun verifyAndEnter(token: String, username: String, onError: (String) -> Unit) {
        repo.status(token) { result ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.isFailure) {
                    onError(getString(R.string.session_expired))
                    return@runOnUiThread
                }

                TokenStore.save(this, token, username)

                // ثبت توکن پوش در پس‌زمینه؛ ورود کاربر منتظر آن نمی‌ماند.
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        ApiClient.postUpdateFcmToken(token, task.result) { }
                    }
                    goToMain()
                }
            }
        }
    }

    /** پیام خام سرور را به جمله‌ای تبدیل می‌کند که کاربر بداند باید چه کار کند. */
    private fun translateLoginError(raw: String?): String {
        val message = raw.orEmpty()
        return when {
            message.contains("Invalid credentials", true) -> getString(R.string.login_error_credentials)
            message.contains("Maximum concurrent sessions", true) -> getString(R.string.login_error_sessions)
            message.isBlank() -> getString(R.string.login_error_network)
            else -> message
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
