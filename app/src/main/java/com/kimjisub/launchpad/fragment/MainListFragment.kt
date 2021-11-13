package com.kimjisub.launchpad.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.kimjisub.design.extra.getVirtualIndexFormSorted
import com.kimjisub.design.view.PackView
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.FBStoreActivity
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.adapter.UniPackAdapter
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.databinding.FragmentMainListBinding
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.viewmodel.MainTotalPanelViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.activities.start
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList

class MainListFragment : BaseFragment() {
	private var _b: FragmentMainListBinding? = null
	private val b get() = _b!!

	// List Management
	private var unipackList: ArrayList<UniPackItem> = ArrayList()
	private var lastPlayIndex = -1
	private var listRefreshing = false

	private val adapter: UniPackAdapter by lazy {
		val adapter = UniPackAdapter(unipackList, object : UniPackAdapter.EventListener {
			override fun onViewClick(item: UniPackItem, v: PackView) {
				togglePlay(item)
			}

			override fun onViewLongClick(item: UniPackItem, v: PackView) {}

			override fun onPlayClick(item: UniPackItem, v: PackView) {
				pressPlay(item)
			}
		})
		adapter.registerAdapterDataObserver(object : AdapterDataObserver() {
			override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
				super.onItemRangeInserted(positionStart, itemCount)
				showErrItem(adapter.itemCount == 0)
			}

			override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
				super.onItemRangeRemoved(positionStart, itemCount)
				showErrItem(adapter.itemCount == 0)
			}

			override fun onChanged() {
				super.onChanged()
				showErrItem(adapter.itemCount == 0)
			}

			fun showErrItem(visibility: Boolean) {
				b.errItem.visibility = if (visibility) View.VISIBLE else View.GONE

			}
		})

		adapter
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		_b = FragmentMainListBinding.inflate(inflater, container, false)
		b.apply {
			lifecycleOwner = this@MainListFragment
		}

		val divider = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
		val drawable = ResourcesCompat.getDrawable(resources, R.drawable.border_divider, null)!!
		divider.setDrawable(drawable)

		b.recyclerView.addItemDecoration(divider)
		b.recyclerView.setHasFixedSize(false)
		b.recyclerView.layoutManager = LinearLayoutManager(context)
		b.recyclerView.adapter = adapter


		b.swipeRefreshLayout.setOnRefreshListener { this.update() }


		b.errItem.setOnClickListener { requireContext().start<FBStoreActivity>() }

		return b.root
	}

	var sort: Pair<MainTotalPanelViewModel.SortMethod, Boolean>? = null

	@SuppressLint("StaticFieldLeak")
	fun update() {
		Log.test("update")

		val firstUpdate = unipackList.size == 0

		lastPlayIndex = -1
		if (listRefreshing) return
		b.swipeRefreshLayout.isRefreshing = true
		listRefreshing = true


		CoroutineScope(Dispatchers.IO).launch {
			var newList = ws.getUnipacks()
			val newAdded = ArrayList<UniPackItem>()
			val newRemoved = ArrayList(unipackList)


			val comparator: Comparator<UniPackItem> = if (sort != null)
				Comparator { a, b ->
					sort!!.first.comparator.compare(a, b) * if (p.sortOrder) 1 else -1
				} else
				Comparator { _, _ -> 0 }


			try {
				newList = newList.sortedWith(comparator)

				for (item: UniPackItem in newList) {
					var index = -1
					for ((i, item2: UniPackItem) in newRemoved.withIndex()) {
						if ((item2.unipack == item.unipack)) {
							index = i
							break
						}
					}
					if (index != -1)
						newRemoved.removeAt(index)
					else
						newAdded.add(0, item)
				}

			} catch (e: Exception) {
				e.printStackTrace()
			}

			for (added: UniPackItem in newAdded) {
				Log.test("added: ${added.unipack.title}")
				val i = unipackList.getVirtualIndexFormSorted(comparator, added)


				Log.test("virtualIndex: $i")


				withContext(Dispatchers.Main) {
					unipackList.add(i, added)
					if (!firstUpdate)
						adapter.notifyItemInserted(i)


					/*added.unipackENT.observe(viewLifecycleOwner) {
						val index = unipackList.indexOf(added)
						adapter.notifyItemChanged(index)
					}*/
				}
			}

			for (removed: UniPackItem in newRemoved) {
				for ((i, item: UniPackItem) in unipackList.withIndex()) {
					if ((item.unipack == removed.unipack)) {


						withContext(Dispatchers.Main) {
							if (item == selected)
								togglePlay(null)

							unipackList.removeAt(i)
							adapter.notifyItemRemoved(i)
							// todo 삭제됐을 때 observing 어떻게될까?
							// removed.unipackENT.removeObservers(viewLifecycleOwner)
							// removed.unipackENT.removeObserver(removed.unipackENTObserver!!)
						}
						break
					}
				}
			}

			var changed = false
			for ((to, target: UniPackItem) in newList.withIndex()) {
				val from = adapter.list.indexOfFirst { it.unipack == target.unipack }

				if (from != -1 && from != to) {
					Collections.swap(adapter.list, from, to)
					changed = true
				}
			}
			Log.test("changed: $changed")

			withContext(Dispatchers.Main) {
				if (changed || adapter.list.size == newAdded.size)
					adapter.notifyDataSetChanged()

				// todo 만약 added가 1이라면 그 팩으로 스크롤하기
				if (newAdded.size > 0) b.recyclerView.smoothScrollToPosition(0)
				b.swipeRefreshLayout.isRefreshing = false
				listRefreshing = false
			}
		}

		callbacks?.onListUpdated()
	}


	private fun togglePlay(i: Int) {
		togglePlay(unipackList[i])
	}

	fun togglePlay(target: UniPackItem?) {
		try {
			for ((i, item: UniPackItem) in unipackList.withIndex()) {
				//val packView = item.packView
				if (target != null && (item.unipack == target.unipack)) {

					lastPlayIndex = i
					item.toggle = !item.toggle
					item.togglea?.invoke(item.toggle)
				} else if (item.toggle) {
					item.toggle = false
					item.togglea?.invoke(item.toggle)
				}
			}
			selected = if (target == null) null else if (target.toggle) target else null

		} catch (e: ConcurrentModificationException) {
			e.printStackTrace()
		}
	}

	fun pressPlay(item: UniPackItem) {
		requireContext().start<PlayActivity> {
			putExtra("path", item.unipack.getPathString())
		}

		callbacks?.onRequestAds()

	}

	/*private var selectedIndex: Int = -1
		set(value) {
			if(value != field) {
				field = value
				callbacks?.onListSelectedChange(field)
			}
		}
	val selected: UniPackItem?
		get() {
			if (selectedIndex == -1)
				return null
			return unipackList[selectedIndex]
		}*/

	var selected: UniPackItem? = null
		set(value) {
			if (value != field) {
				field = value
				callbacks?.onListSelectedChange(field)
			}
		}


	fun haveNow(): Boolean {
		return 0 <= lastPlayIndex && lastPlayIndex <= unipackList.size - 1
	}

	fun haveNext(): Boolean {
		return lastPlayIndex < unipackList.size - 1
	}

	fun havePrev(): Boolean {
		return 0 < lastPlayIndex
	}

	fun next() {
		if (haveNext()) {
			togglePlay(lastPlayIndex + 1)
			smoothScrollToPosition(lastPlayIndex)
		} // else showSelectLPUI()
	}

	fun prev() {
		if (haveNext()) {
			togglePlay(lastPlayIndex - 1)
			smoothScrollToPosition(lastPlayIndex)
		} // else showSelectLPUI()
	}

	fun currentClick() {
		if (haveNow()) unipackList[lastPlayIndex].playClick?.invoke()
	}

	fun smoothScrollToPosition(index: Int) {
		b.recyclerView.smoothScrollToPosition(index)
	}

	fun deselect(): Boolean {
		return if (selected != null) {
			togglePlay(null)
			true
		} else false
	}


	private var callbacks: Callbacks? = null

	interface Callbacks {
		fun onListSelectedChange(item: UniPackItem?)
		fun onListUpdated()
		fun onRequestAds()
	}

	override fun onAttach(context: Context) {
		super.onAttach(context)
		callbacks = context as Callbacks?
	}

	override fun onDetach() {
		super.onDetach()
		callbacks = null
	}
}