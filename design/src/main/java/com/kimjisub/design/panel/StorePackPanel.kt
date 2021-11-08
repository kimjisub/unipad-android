package com.kimjisub.design.panel

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.databinding.ObservableField
import com.kimjisub.design.R.styleable
import com.kimjisub.design.databinding.PanelStorePackBinding
import java.text.DecimalFormat

class StorePackPanel
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {
	val b: PanelStorePackBinding =
		PanelStorePackBinding.inflate(LayoutInflater.from(context), this, true)
	val data = Data()

	class Data {
		val title: ObservableField<String> = ObservableField()
		val subtitle: ObservableField<String> = ObservableField()
		val downloadCount: ObservableField<String> = ObservableField()
		val path: ObservableField<String> = ObservableField()
	}

	init {
		attrs?.let {
			val typedArray =
				context.obtainStyledAttributes(it, styleable.StorePackPanel, defStyleAttr, 0)

			//	b.TVTitle.isSelected = true
			//	b.TVSubtitle.isSelected = true
			//	b.path.isSelected = true
			b.youtube.setOnClickListener { v: View ->
				onEventListener?.onYoutubeClick(v)
			}
			b.website.setOnClickListener { v: View ->
				onEventListener?.onWebsiteClick(v)
			}

			typedArray.recycle()
		}
	}

	private val numberFormatter = DecimalFormat("###,###")

	////////////////////////////////////////////////////////////////////////////////////////////////


	fun setTitle(title: String) {
		b.title.text = title
	}

	fun setSubtitle(subtitle: String) {
		b.subtitle.text = subtitle
	}

	fun setDownloadCount(downloadCount: String) {
		b.downloadCount.text = downloadCount
	}

	fun setDownloadCount(downloadCount: Long) {
		val downloadCountFormatted: String = numberFormatter.format(downloadCount)
		setDownloadCount(downloadCountFormatted)
	}

	fun updateTitle(title: String) {
		if (b.title.text != title) {
			b.title.alpha = 0f
			setTitle(title)
			b.title.animate().alpha(1f).setDuration(500).start()
		}
	}

	fun updateSubtitle(subtitle: String) {
		if (b.subtitle.text != subtitle) {
			b.subtitle.alpha = 0f
			setSubtitle(subtitle)
			b.subtitle.animate().alphaBy(0f).alpha(1f).setDuration(500).start()
		}
	}

	fun updateDownloadCount(downloadCount: Long) {
		val downloadCountFormatted: String = numberFormatter.format(downloadCount)
		if (b.downloadCount.text != downloadCountFormatted) {
			b.downloadCount.alpha = 0f
			setDownloadCount(downloadCountFormatted)
			b.downloadCount.animate().alphaBy(0f).alpha(1f).setDuration(500).start()
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////


	var onEventListener: OnEventListener? = null

	interface OnEventListener {
		fun onYoutubeClick(v: View)
		fun onWebsiteClick(v: View)
	}
}