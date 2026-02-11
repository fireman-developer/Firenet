package com.v2ray.ang.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemServerSelectionBinding
import com.v2ray.ang.dto.ServersCache
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.CountryUtils

class ServerSelectionAdapter(
    private val servers: List<ServersCache>,
    private val currentGuid: String,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<ServerSelectionAdapter.ViewHolder>() {

    // رنگ‌ها
    private val ACCENT_COLOR = Color.parseColor("#00E5FF") // آبی فیروزه‌ای
    private val WHITE_COLOR = Color.WHITE

    inner class ViewHolder(private val binding: ItemServerSelectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ServersCache) {
            val config = MmkvManager.decodeServerConfig(item.guid)
            val remark = config?.remarks ?: "Unknown"

            // 1. تنظیم عکس پرچم
            val flagResId = CountryUtils.getFlagResId(binding.root.context, remark)
            binding.ivFlag.setImageResource(flagResId)

            // 2. تنظیم متن
            binding.tvRemark.text = remark

            // 3. مدیریت وضعیت انتخاب (Selection)
            val isSelected = item.guid == currentGuid

            if (isSelected) {
                // --- حالت انتخاب شده (Active) ---
                
                // اعمال بک‌گراند جدید با بوردر رنگی
                binding.mainContent.setBackgroundResource(com.v2ray.ang.R.drawable.bg_server_selected)
                
                // نمایش تیک سبز/آبی
                binding.ivSelectedCheck.visibility = View.VISIBLE
                
                // تغییر رنگ متن به رنگ Accent (اختیاری)
                binding.tvRemark.setTextColor(ACCENT_COLOR)
                
                // شفافیت کامل
                binding.root.alpha = 1.0f
                
            } else {
                // --- حالت انتخاب نشده (Inactive) ---
                
                // بازگشت به بک‌گراند شیشه‌ای ساده
                binding.mainContent.setBackgroundResource(com.v2ray.ang.R.drawable.bg_server_inactive)
                
                // مخفی کردن تیک
                binding.ivSelectedCheck.visibility = View.GONE
                
                // رنگ متن سفید
                binding.tvRemark.setTextColor(WHITE_COLOR)
                
                // کمی شفاف‌تر
                binding.root.alpha = 0.8f
            }

            // هندل کردن کلیک روی لایه داخلی (mainContent)
            binding.mainContent.setOnClickListener {
                onItemClick(item.guid)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemServerSelectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(servers[position])
    }

    override fun getItemCount(): Int = servers.size
}