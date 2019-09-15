package com.kimjisub.launchpad.adapter

import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.LinearLayout.LayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.kimjisub.design.PackView
import com.kimjisub.design.PackView.OnEventListener
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.activity.BaseActivity
import com.kimjisub.launchpad.network.fb.StoreVO
import com.kimjisub.manager.Log
import java.util.*

class StoreItem(var storeVO: StoreVO, var isDownloaded: Boolean = false) {
	var isDownloading: Boolean = false

	var packView: PackView? = null
	var isToggle = false
}

class StoreHolder(val packView: PackView) : RecyclerView.ViewHolder(packView) {
	var realPosition = -1
}

class StoreAdapter(private val context: BaseActivity, private val list: ArrayList<StoreItem>, private val eventListener: EventListener) : Adapter<StoreHolder>() {
	private var viewHolderCount = 0
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreHolder {
		Log.test("onCreateViewHolder: " + viewHolderCount++)
		val packView = PackView(parent.context)
		val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
		packView.layoutParams = lp
		return StoreHolder(packView)
	}

	override fun onBindViewHolder(holder: StoreHolder, position: Int) {
		Log.test("onBindViewHolder: $position")
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
		packView.animate = false
		packView.toggleColor = context.color(if (item.isDownloaded) color.green else color.red)
		packView.untoggleColor = context.color(if (item.isDownloaded) color.green else color.red)
		packView.title = item.storeVO.title!!
		packView.subtitle = item.storeVO.producerName!!
		packView.option1Name = context.lang(string.LED_)
		packView.option1 = item.storeVO.isLED
		packView.option2Name = context.lang(string.autoPlay_)
		packView.option2 = item.storeVO.isAutoPlay
		packView.showPlayImage(false)
		packView.setPlayText(context.lang(if (item.isDownloaded) string.downloaded else string.download))
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
		packView.animate = true
		val a: Animation? = AnimationUtils.loadAnimation(context, anim.pack_in)
		packView.animation = a
	}

	override fun onBindViewHolder(holder: StoreHolder, position: Int, payloads: List<Any>) {
		if (payloads.isEmpty()) {
			super.onBindViewHolder(holder, position, payloads)
			return
		}
		Log.test("onBindViewHolder payloads: $position")
		val item = list[holder.adapterPosition]
		val packView = holder.packView
		for (payload in payloads) {
			if (payload is String) {
				when (payload) {
					"update" -> {
						Log.test("update")
						packView.toggleColor = context.color(if (item.isDownloaded) color.green else color.red)
						packView.untoggleColor = context.color(if (item.isDownloaded) color.green else color.red)
						packView.title = item.storeVO.title!!
						packView.subtitle = item.storeVO.producerName!!
						packView.option1 = item.storeVO.isLED
						packView.option2 = item.storeVO.isAutoPlay
						packView.setPlayText(context.lang(if (item.isDownloaded) string.downloaded else string.download))
					}
				}/*String type = (String) payload;
				if (TextUtils.equals(type, "click") && holder instanceof TextHolder) {
					TextHolder textHolder = (TextHolder) holder;
					textHolder.mFavorite.setVisibility(View.VISIBLE);
					textHolder.mFavorite.setAlpha(0f);
					textHolder.mFavorite.setScaleX(0f);
					textHolder.mFavorite.setScaleY(0f);

					//animation
					textHolder.mFavorite.animate()
							.scaleX(1f)
							.scaleY(1f)
							.alpha(1f)
							.setInterpolator(new OvershootInterpolator())
							.setDuration(300);

				}*/
			}
		}
	}

	override fun getItemCount() = list.size

	////////////////////////////////////////////////////////////////////////////////////////////////


	interface EventListener {
		fun onViewClick(item: StoreItem, v: PackView)
		fun onViewLongClick(item: StoreItem, v: PackView)
		fun onPlayClick(item: StoreItem, v: PackView)
	}

}