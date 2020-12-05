package com.kimjisub.design.panel

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableField
import com.kimjisub.design.R.layout
import com.kimjisub.design.R.styleable
import com.kimjisub.design.databinding.PanelMainPackBinding
import kotlinx.android.synthetic.main.panel_main_pack.view.*
import java.util.*

class MainPackPanel
@JvmOverloads
constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

	/*@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(
		context: Context,
		attrs: AttributeSet?,
		defStyleAttr: Int,
		defStyleRes: Int)
			: super(context, attrs, defStyleAttr, defStyleRes)*/

	private val b: PanelMainPackBinding =
		DataBindingUtil.inflate(LayoutInflater.from(context), layout.panel_main_pack, this, true)
	val data = Data()

	class Data {
		// Static
		val bookmark: ObservableField<Boolean> = ObservableField()
		val storage: ObservableField<Boolean> = ObservableField()
		val moving: ObservableField<Boolean> = ObservableField()
		val title: ObservableField<String> = ObservableField()
		val subtitle: ObservableField<String> = ObservableField()
		val padSize: ObservableField<String> = ObservableField()
		val chainCount: ObservableField<String> = ObservableField()
		val soundCount: ObservableField<String> = ObservableField()
		val ledCount: ObservableField<String> = ObservableField()
		val fileSize: ObservableField<String> = ObservableField()
		val playCount: ObservableField<String> = ObservableField()
		val downloadedDate: ObservableField<Date> = ObservableField()
		val lastPlayed: ObservableField<Date> = ObservableField()
		val websiteExist: ObservableField<Boolean> = ObservableField()
		val path: ObservableField<String> = ObservableField()
	}

	init {
		/*LayoutInflater.from(context)
			.inflate(layout.panel_store_pack, this, true)
		b = PanelMainPackBinding.bind(this)*/

		b.data = data

		attrs?.let {
			val typedArray =
				context.obtainStyledAttributes(it, styleable.StorePackPanel, defStyleAttr, 0)

			TV_title.isSelected = true
			TV_subtitle.isSelected = true
			IV_path.isSelected = true
			IV_bookmark.setOnClickListener { v: View ->
				onEventListener?.onBookmarkClick(v)
			}
			IV_edit.setOnClickListener { v: View ->
				onEventListener?.onEditClick(v)
			}
			IV_storage.setOnClickListener { v: View ->
				onEventListener?.onStorageClick(v)
			}
			IV_youtube.setOnClickListener { v: View ->
				onEventListener?.onYoutubeClick(v)
			}
			IV_website.setOnClickListener { v: View ->
				onEventListener?.onWebsiteClick(v)
			}
			IV_func.setOnClickListener { v: View ->
				onEventListener?.onFuncClick(v)
			}
			IV_delete.setOnClickListener { v: View ->
				onEventListener?.onDeleteClick(v)
			}

			typedArray.recycle()
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////


	var onEventListener: OnEventListener? = null

	interface OnEventListener {
		fun onBookmarkClick(v: View)
		fun onEditClick(v: View)
		fun onStorageClick(v: View)
		fun onYoutubeClick(v: View)
		fun onWebsiteClick(v: View)
		fun onFuncClick(v: View)
		fun onDeleteClick(v: View)
	}
}

