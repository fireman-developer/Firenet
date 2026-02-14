package com.v2ray.ang.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainRecyclerAdapter(val activity: MainActivity) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2 // نوع جدید برای فضای خالی پایین لیست
    }

    private var mActivity: MainActivity = activity
    var isRunning = false

    // تعداد آیتم‌ها + 1 (برای فوتر)
    override fun getItemCount(): Int {
        val size = mActivity.mainViewModel.serversCache.size
        return if (size > 0) size + 1 else 0
    }

    // تشخیص نوع آیتم (اگر آخرین پوزیشن بود، فوتر است)
    override fun getItemViewType(position: Int): Int {
        return if (position == mActivity.mainViewModel.serversCache.size) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            // لاجیک اصلی فقط برای آیتم‌های سرور اجرا می‌شود
            // با استفاده از getOrNull از کرش احتمالی جلوگیری می‌کنیم
            val serverConfig = mActivity.mainViewModel.serversCache.getOrNull(position) ?: return
            
            val guid = serverConfig.guid
            val config = serverConfig.profile
            
            holder.itemMainBinding.tvName.text = config?.remarks ?: "Unknown"

            val isSelected = (guid == MmkvManager.getSelectServer())

            if (isSelected) {
                // Active State
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.drawable.bg_glass_input)
                holder.itemMainBinding.layoutIndicator.backgroundTintList = 
                    ColorStateList.valueOf(ContextCompat.getColor(mActivity, R.color.colorAccent))
                
                holder.itemMainBinding.ivStatusIcon.setImageResource(R.drawable.ic_server_active)
                holder.itemMainBinding.ivStatusIcon.setColorFilter(Color.WHITE)

                holder.itemMainBinding.tvName.maxLines = 2
                holder.itemMainBinding.tvName.setTextColor(ContextCompat.getColor(mActivity, R.color.colorAccent))
                holder.itemMainBinding.tvName.ellipsize = null
            } else {
                // Idle State
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.drawable.bg_glass_input)
                holder.itemMainBinding.layoutIndicator.backgroundTintList = 
                    ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))

                holder.itemMainBinding.ivStatusIcon.setImageResource(R.drawable.ic_server_idle)
                holder.itemMainBinding.ivStatusIcon.setColorFilter(Color.LTGRAY)

                holder.itemMainBinding.tvName.maxLines = 1
                holder.itemMainBinding.tvName.setTextColor(Color.WHITE)
                holder.itemMainBinding.tvName.ellipsize = TextUtils.TruncateAt.END
            }

            holder.itemView.setOnClickListener {
                setSelectServer(guid)
            }
        }
        // برای Footer نیازی به bind کردن نیست چون یک ویوی خالی است
    }

    private fun setSelectServer(guid: String) {
        val selected = MmkvManager.getSelectServer()
        if (guid != selected) {
            MmkvManager.setSelectServer(guid)
            mActivity.mainViewModel.reloadServerList() // ریلود کردن لیست برای آپدیت UI
            
            // اگر سرویس ران است، ریستارت کن
            if (isRunning) {
                mActivity.lifecycleScope.launch {
                    V2RayServiceManager.stopVService(mActivity)
                    try {
                        delay(500)
                        V2RayServiceManager.startVService(mActivity)
                    } catch (e: Exception) {
                        Log.e(AppConfig.TAG, "Failed to restart V2Ray service", e)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return if (viewType == VIEW_TYPE_FOOTER) {
            // ساخت یک ویوی خالی به عنوان فوتر
            val view = View(parent.context)
            // ارتفاع 96dp (یا بیشتر) برای اینکه آخرین آیتم زیر دکمه شناور نرود
            val height = (parent.context.resources.displayMetrics.density * 96).toInt()
            view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            BaseViewHolder(view)
        } else {
            // ساخت ویوی آیتم اصلی
            MainViewHolder(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
        BaseViewHolder(itemMainBinding.root)
}