package com.kimjisub.design.panel

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.databinding.DataBindingUtil
import com.kimjisub.design.R.layout
import com.kimjisub.design.R.styleable
import com.kimjisub.design.databinding.PanelMainTotalBinding
import com.kimjisub.design.databinding.PanelStorePackBinding
import kotlinx.android.synthetic.main.panel_main_pack.view.*
import kotlinx.android.synthetic.main.panel_store_total.view.*
import java.text.DecimalFormat

class StorePackPanel
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

	val b: PanelStorePackBinding = DataBindingUtil.inflate(LayoutInflater.from(context),layout.panel_store_pack,this,false)

	init {
		/*val v = LayoutInflater.from(context)
			.inflate(layout.panel_store_pack, this, true)
		b = PanelStorePackBinding.bind(v)*/

		attrs?.let {
			val typedArray = context.obtainStyledAttributes(it, styleable.StorePackPanel, defStyleAttr, 0)

			b.TVTitle.isSelected = true
			b.TVSubtitle.isSelected = true
			b.path.isSelected = true
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
		TV_title.text = title
	}

	fun setSubtitle(subtitle: String) {
		TV_subtitle.text = subtitle
	}

	fun setDownloadCount(downloadCount: String) {
		TV_downloadedCount.text = downloadCount
	}

	fun setDownloadCount(downloadCount: Long) {
		val downloadCountFormatted: String = numberFormatter.format(downloadCount)
		setDownloadCount(downloadCountFormatted)
	}

	fun updateTitle(title: String) {
		if (TV_title.text != title) {
			TV_title.alpha = 0f
			setTitle(title)
			TV_title.animate().alpha(1f).setDuration(500).start()
		}
	}

	fun updateSubtitle(subtitle: String) {
		if (TV_subtitle.text != subtitle) {
			TV_subtitle.alpha = 0f
			setSubtitle(subtitle)
			TV_subtitle.animate().alphaBy(0f).alpha(1f).setDuration(500).start()
		}
	}

	fun updateDownloadCount(downloadCount: Long) {
		val downloadCountFormatted: String = numberFormatter.format(downloadCount)
		if (TV_downloadedCount.text != downloadCountFormatted) {
			TV_downloadedCount.alpha = 0f
			setDownloadCount(downloadCountFormatted)
			TV_downloadedCount.animate().alphaBy(0f).alpha(1f).setDuration(500).start()
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////


	var onEventListener: OnEventListener? = null

	interface OnEventListener {
		fun onYoutubeClick(v: View)
		fun onWebsiteClick(v: View)
	}
}