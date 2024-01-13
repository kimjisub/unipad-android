package com.kimjisub.launchpad.adapter

import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.LinearLayout.LayoutParams
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.kimjisub.design.view.PackView
import com.kimjisub.design.view.PackView.OnEventListener
import com.kimjisub.launchpad.R.anim
import com.kimjisub.launchpad.R.color
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.db.ent.Unipack
import com.kimjisub.launchpad.db.util.observeOnce
import com.kimjisub.launchpad.unipack.UniPack
import java.util.*

data class UniPackItem(
	var unipack: UniPack,
	val unipackENT: LiveData<Unipack>,
	/*var isNew: Boolean,*/
) {
	//var packView: PackView? = null
	var flagColor: Int = 0
	var toggle: Boolean = false

	var togglea: ((toggle: Boolean) -> Unit)? = null
	var playClick: (() -> Unit)? = null
}

class UniPackHolder(
	var packView: PackView,
) : RecyclerView.ViewHolder(packView)

class UniPackAdapter(var list: ArrayList<UniPackItem>, private val eventListener: EventListener) :
	Adapter<UniPackHolder>() {
	var i = 0
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UniPackHolder {
		val packView = PackView(parent.context)
		val lp =
			LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
		packView.layoutParams = lp
		return UniPackHolder(packView)
	}

	override fun onBindViewHolder(holder: UniPackHolder, position: Int) {
		val context = holder.packView.context

		val item = list[position]
		val packView = holder.packView

		item.togglea = {
			packView.toggle(it)
		}
		item.playClick = {
			packView.onPlayClick()
		}

		////////////////////////////////////////////////////////////////////////////////////////////////

		var viewingTitle: String = item.unipack.title
		var viewingSubtitle: String = item.unipack.producerName
		if (item.unipack.criticalError) {
			item.flagColor = ContextCompat.getColor(context, color.red)
			viewingTitle = context.getString(string.errOccur)
			viewingSubtitle = item.unipack.getPathString()
		} else
			item.flagColor = ContextCompat.getColor(context, color.skyblue)

		packView.apply {
			animate = false
			toggleColor = ContextCompat.getColor(context, color.red)
			untoggleColor = item.flagColor
			title = viewingTitle
			subtitle = viewingSubtitle
			option1Name = context.getString(string.led).uppercase(Locale.getDefault()) + " ●"
			option1 = item.unipack.keyLedExist
			option2Name = context.getString(string.autoPlay).uppercase(Locale.getDefault()) + " ●"
			option2 = item.unipack.autoPlayExist
			bookmark = false

			item.unipackENT.observeOnce {
				bookmark = it.bookmark
			}

			setOnEventListener(object : OnEventListener {
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
			toggle(item.toggle)
			animate = true
			var a: Animation? = AnimationUtils.loadAnimation(context, anim.pack_in)
			// if (item.isNew) a = AnimationUtils.loadAnimation(context, anim.pack_new_in)
			// item.isNew = false
			animation = a
		}


	}

	override fun getItemCount() = list.size

	interface EventListener {
		fun onViewClick(item: UniPackItem, v: PackView)
		fun onViewLongClick(item: UniPackItem, v: PackView)
		fun onPlayClick(item: UniPackItem, v: PackView)
	}
}