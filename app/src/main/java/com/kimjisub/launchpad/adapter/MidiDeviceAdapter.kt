package com.kimjisub.launchpad.adapter

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.databinding.ItemMidiDeviceBinding
import kotlin.reflect.KClass

data class MidiDeviceItem(val icon: Drawable?, val name: String, val driver: KClass<*>)

class MidiDeviceViewHolder(itemView: View, private val recyclerView: RecyclerView) :
	RecyclerView.ViewHolder(itemView) {

	val itemMidiDeviceView: ItemMidiDeviceBinding = ItemMidiDeviceBinding.bind(itemView)

	init {
		itemMidiDeviceView.root.setOnClickListener {
			recyclerView.smoothScrollToPosition(
				adapterPosition
			)
		}
	}
}

class MidiDeviceAdapter(private val data: List<MidiDeviceItem>) :
	RecyclerView.Adapter<MidiDeviceViewHolder>() {
	private var parentRecycler: RecyclerView? = null
	override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
		super.onAttachedToRecyclerView(recyclerView)
		parentRecycler = recyclerView
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: Int,
	): MidiDeviceViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val v: View = inflater.inflate(R.layout.item_midi_device, parent, false)
		return MidiDeviceViewHolder(v, parentRecycler!!)
	}

	override fun onBindViewHolder(holder: MidiDeviceViewHolder, position: Int) {
		val item = data[position]
		holder.itemMidiDeviceView.data = item
	}

	override fun getItemCount(): Int {
		return data.size
	}
}

//
//data class MidiDeviceItem(val icon: Drawable?, val name: String) : CarouselModel()
//
//class MidiDeviceAdapter : CarouselAdapter() {
//
//	private var vh: CarouselViewHolder? = null
//	var onClick: OnClick? = null
//
//	fun setOnClickListener(onClick: OnClick?) {
//		this.onClick = onClick
//	}
//
//	override fun getItemViewType(position: Int): Int {
//		return 0
//	}
//
//	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
//		val inflater = LayoutInflater.from(parent.context)
//		val v = inflater.inflate(R.layout.item_midi_device, parent, false)
//		vh = MyViewHolder(v)
//		return vh as MyViewHolder
//	}
//
//	override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
//
//		vh = holder
//		val model = getItems()[position] as MidiDeviceItem
//		(vh as MyViewHolder).itemMidiDeviceView.data = model
//	}
//
//	inner class MyViewHolder(itemView: View) : CarouselViewHolder(itemView) {
//		val itemMidiDeviceView : ItemMidiDeviceBinding = ItemMidiDeviceBinding.bind(itemView)
//	}
//
//	inner class EmptyMyViewHolder(itemView: View) : CarouselViewHolder(itemView) {
//		//var titleEmpty: TextView = itemView.item_empty_text
//	}
//
//	interface OnClick {
//		fun click(model: MidiDeviceItem)
//	}
//}