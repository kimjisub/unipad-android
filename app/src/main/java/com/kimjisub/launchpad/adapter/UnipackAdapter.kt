package com.kimjisub.launchpad.adapter

import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.LinearLayout.LayoutParams
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.kimjisub.design.PackView
import com.kimjisub.design.PackView.OnEventListener
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.activity.BaseActivity
import com.kimjisub.launchpad.manager.Unipack
import java.util.*

data class UnipackItem(
		var unipack: Unipack,
		var path: String,
		var bookmark: Boolean,
		var isNew: Boolean
) {
	var packView: PackView? = null
	var flagColor: Int = 0
	var toggle: Boolean = false
	var moving: Boolean = false
}

class UnipackHolder(internal var packView: PackView) : RecyclerView.ViewHolder(packView) {
	var realPosition = -1
}

class UnipackAdapter(private val list: ArrayList<UnipackItem>, private val eventListener: EventListener) : Adapter<UnipackHolder>() {
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UnipackHolder {
		val packView = PackView(parent.context)
		val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
		packView.layoutParams = lp
		return UnipackHolder(packView)
	}

	override fun onBindViewHolder(holder: UnipackHolder, position: Int) {
		val context= holder.packView.context

		val item = list[holder.adapterPosition]
		val packView = holder.packView

		// 이전 데이터에 매핑된 뷰를 제거합니다.


		try {
			list[holder.realPosition].packView = null
		} catch (ignored: Exception) {
		}

		// 새롭게 할당될 데이터에 뷰를 할당하고 홀더에도 해당 포지션을 등록합니다.

		item.packView = packView
		holder.realPosition = holder.adapterPosition
		////////////////////////////////////////////////////////////////////////////////////////////////

		var title: String? = item.unipack.title
		var subtitle: String? = item.unipack.producerName
		if (item.unipack.CriticalError) {
			item.flagColor = ContextCompat.getColor(context, color.red)
			title = context.getString(string.errOccur)
			subtitle = item.path
		} else item.flagColor = ContextCompat.getColor(context,color.skyblue)
		if (item.bookmark) item.flagColor = ContextCompat.getColor(context, color.orange)
		packView.animate = false
		packView.toggleColor = ContextCompat.getColor(context,color.red)
		packView.untoggleColor = item.flagColor
		packView.title = title!!
		packView.subtitle = subtitle!!
		packView.option1Name = context.getString(string.LED_)
		packView.option1 = item.unipack.isKeyLED
		packView.option2Name = context.getString(string.autoPlay_)
		packView.option2 = item.unipack.isAutoPlay
		packView.setOnEventListener(object : OnEventListener {
			override fun onViewClick(v: PackView) {
				eventListener.onViewClick(item, v)
			}

			override fun onViewLongClick(v: PackView) {
				eventListener.onViewLongClick(item, v)
			}

			override fun onPlayClick(v: PackView) {
				eventListener.onPlayClick(item, v)
			}
		})
		packView.toggle(item.toggle)
		packView.animate = true
		var a: Animation? = AnimationUtils.loadAnimation(context, anim.pack_in)
		if (item.isNew) a = AnimationUtils.loadAnimation(context, anim.pack_new_in)
		item.isNew = false
		packView.animation = a
	}

	override fun getItemCount(): Int {
		return list.size
	}


	////////////////////////////////////////////////////////////////////////////////////////////////


	interface EventListener {
		fun onViewClick(item: UnipackItem, v: PackView)
		fun onViewLongClick(item: UnipackItem, v: PackView)
		fun onPlayClick(item: UnipackItem, v: PackView)
	}

}