package com.kimjisub.design.panel

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.databinding.BindingMethod
import androidx.databinding.BindingMethods
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableField
import com.kimjisub.design.R.*
import com.kimjisub.design.databinding.PanelMainPackBinding
import kotlinx.android.synthetic.main.panel_main_pack.view.*

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

	private val b: PanelMainPackBinding = DataBindingUtil.inflate(LayoutInflater.from(context), layout.panel_main_pack, this, true)
	val data: Data = Data()

	init {
		/*LayoutInflater.from(context)
			.inflate(layout.panel_store_pack, this, true)
		b = PanelMainPackBinding.bind(this)*/

		b.data = data

		attrs?.let {
			val typedArray = context.obtainStyledAttributes(it, styleable.StorePackPanel, defStyleAttr, 0)

			TV_title.isSelected = true
			TV_subtitle.isSelected = true
			IV_path.isSelected = true
			IV_star.setOnClickListener { v: View ->
				onEventListener?.onStarClick(v)
			}
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

	class Data {
		val star: ObservableField<Boolean> = ObservableField(false)
		val bookmark: ObservableField<Boolean> = ObservableField(false)
		val storage:ObservableField<Boolean> = ObservableField(false)
		val moving: ObservableField<Boolean> = ObservableField(false)

		val title: ObservableField<String> = ObservableField()
		val subtitle: ObservableField<String> = ObservableField()

		val scale : ObservableField<String> = ObservableField()
		val chainCount: ObservableField<String> = ObservableField()
		val soundCount : ObservableField<String> = ObservableField()
		val ledCount: ObservableField<String> = ObservableField()
		val fileSize: ObservableField<String> = ObservableField()
		val openCount: ObservableField<String> = ObservableField()
		val padTouchCount: ObservableField<String> = ObservableField()

		val websiteExist: ObservableField<Boolean> = ObservableField(false)

		val path: ObservableField<String> = ObservableField()
	}

	////////////////////////////////////////////////////////////////////////////////////////////////


	fun setStar(star: Boolean) {
		IV_star.setImageResource(if (star) drawable.ic_star_24dp else drawable.ic_star_border_24dp)
	}

	fun setBookmark(bookmark: Boolean) {
		IV_star.setImageResource(if (bookmark) drawable.ic_bookmark_24dp else drawable.ic_bookmark_border_24dp)
	}

	fun setStorage(external: Boolean) {
		IV_storage.setImageResource(if (external) drawable.ic_public_24dp else drawable.ic_lock_24dp)
		IV_storage.isClickable = true
	}

	fun setStorageMoving() {
		IV_storage.setImageResource(drawable.ic_copy_24dp)
		IV_storage.isClickable = false
	}


	////////////////////////////////////////////////////////////////////////////////////////////////


	internal var onEventListener: OnEventListener? = null

	interface OnEventListener {
		fun onStarClick(v: View)
		fun onBookmarkClick(v: View)
		fun onEditClick(v: View)
		fun onStorageClick(v: View)
		fun onYoutubeClick(v: View)
		fun onWebsiteClick(v: View)
		fun onFuncClick(v: View)
		fun onDeleteClick(v: View)
	}

	fun setOnEventListener(onEventListener: OnEventListener?) {
		this.onEventListener = onEventListener
	}
}

