package com.kimjisub.design.panel

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.databinding.ObservableField
import com.kimjisub.design.R.styleable
import com.kimjisub.design.databinding.PanelMainPackBinding
import java.util.*

class MainPackPanel
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {
	private val b: PanelMainPackBinding =
		PanelMainPackBinding.inflate(LayoutInflater.from(context), this, true)
	val data = Data()

	class Data {
		// Static
		val bookmark: ObservableField<Boolean> = ObservableField()
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
		b.data = data

		attrs?.let {
			val typedArray =
				context.obtainStyledAttributes(it, styleable.StorePackPanel, defStyleAttr, 0)

			b.title.isSelected = true
			b.subtitle.isSelected = true
			b.path.isSelected = true
			b.bookmark.setOnClickListener { v: View ->
				onEventListener?.onBookmarkClick(v)
			}
			b.btnEdit.setOnClickListener { v: View ->
				onEventListener?.onEditClick(v)
			}
			b.btnYoutube.setOnClickListener { v: View ->
				onEventListener?.onYoutubeClick(v)
			}
			b.btnWebsite.setOnClickListener { v: View ->
				onEventListener?.onWebsiteClick(v)
			}
			b.btnFunc.setOnClickListener { v: View ->
				onEventListener?.onFuncClick(v)
			}
			b.btnDelete.setOnClickListener { v: View ->
				onEventListener?.onDeleteClick(v)
			}
			b.path.setOnClickListener { v: View ->
				onEventListener?.onPathClick(v)
			}

			typedArray.recycle()
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////


	var onEventListener: OnEventListener? = null

	interface OnEventListener {
		fun onBookmarkClick(v: View)
		fun onEditClick(v: View)
		fun onYoutubeClick(v: View)
		fun onWebsiteClick(v: View)
		fun onFuncClick(v: View)
		fun onDeleteClick(v: View)
		fun onPathClick(v: View)
	}
}

