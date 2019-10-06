package com.kimjisub.launchpad.adapter

import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.LinearLayout.LayoutParams
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.kimjisub.design.PackView
import com.kimjisub.design.PackView.OnEventListener
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.db.ent.UnipackENT
import com.kimjisub.launchpad.unipack.Unipack
import com.kimjisub.manager.Log

data class UnipackItem(
	var unipack: Unipack,
	val unipackENT: LiveData<UnipackENT>,
	var isNew: Boolean
) {
	var unipackENTObserverAdapter: Observer<UnipackENT>? = null
	var unipackENTObserverBookmark: Observer<UnipackENT>? = null

	var packView: PackView? = null
	var flagColor: Int = 0
	var toggle: Boolean = false
	var moving: Boolean = false
}

class UnipackHolder(
	var packView: PackView
) : RecyclerView.ViewHolder(packView), LifecycleOwner {

	var realPosition = -1


	private val lifecycleRegistry = LifecycleRegistry(this)

	init {
		lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
	}

	fun onAppear() {
		lifecycleRegistry.currentState = Lifecycle.State.STARTED
	}


	fun onDisappear() {
		lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
	}


	override fun getLifecycle(): Lifecycle = lifecycleRegistry
}

class UnipackAdapter(private val list: ArrayList<UnipackItem>, private val eventListener: EventListener) : Adapter<UnipackHolder>() {
	var i =0
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UnipackHolder {
		val packView = PackView(parent.context)
		packView.debug = "${i++}"
		val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
		packView.layoutParams = lp
		return UnipackHolder(packView)
	}

	override fun onBindViewHolder(holder: UnipackHolder, position: Int) {
		Log.test("onBindViewHolder: $position")
		val context = holder.packView.context

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

		var viewingTitle: String = item.unipack.title!!
		var viewingSubtitle: String = item.unipack.producerName!!
		if (item.unipack.criticalError) {
			item.flagColor = ContextCompat.getColor(context, color.red)
			viewingTitle = context.getString(string.errOccur)
			viewingSubtitle = item.unipack.F_project.path
		} else
			item.flagColor = ContextCompat.getColor(context, color.skyblue)

		packView.apply {
			animate = false
			toggleColor = ContextCompat.getColor(context, color.red)
			untoggleColor = item.flagColor
			title = viewingTitle
			subtitle = viewingSubtitle
			option1Name = context.getString(string.LED_)
			option1 = item.unipack.keyLEDExist
			option2Name = context.getString(string.autoPlay_)
			option2 = item.unipack.autoPlayExist
			bookmark = false

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
			if (item.isNew) a = AnimationUtils.loadAnimation(context, anim.pack_new_in)
			item.isNew = false
			animation = a
		}

		Log.test("set ${item.unipack.title}")
		item.unipackENT.observeForever {
			item.packView?.apply {
				Log.test("bookmark: ${item.unipack.title}: ${it!!.bookmark}")
				packView.debug = "${item.unipack.title}"
				bookmark = it!!.bookmark

			}
		}
		/*item.unipackENT.observe(holder, Observer {
			packView.apply {
				Log.test("bookmark: ${item.unipack.title}: ${it!!.bookmark}")
				packView.debug = "${item.unipack.title}"
				bookmark = it!!.bookmark

			}
		})*/
	}

	override fun onViewAttachedToWindow(holder: UnipackHolder) {
		super.onViewAttachedToWindow(holder)
		Log.test("attached: ${holder.realPosition}")
		holder.onAppear()
	}

	override fun onViewDetachedFromWindow(holder: UnipackHolder) {
		super.onViewDetachedFromWindow(holder)
		Log.test("detached: ${holder.realPosition}")
		holder.onDisappear()
	}



	override fun getItemCount() = list.size

	interface EventListener {
		fun onViewClick(item: UnipackItem, v: PackView)
		fun onViewLongClick(item: UnipackItem, v: PackView)
		fun onPlayClick(item: UnipackItem, v: PackView)
	}

}