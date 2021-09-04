package com.kimjisub.design.view

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import com.kimjisub.design.R.*
import com.kimjisub.design.databinding.ViewPackBinding
import com.kimjisub.manager.UIManager

class PackView
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {
	private val b: ViewPackBinding = ViewPackBinding.bind(this)


	init {
		// set listener
		b.touchSpace.setOnClickListener { onViewClick() }
		b.touchSpace.setOnLongClickListener {
			onViewLongClick()
			true
		}
	}


	private val green = ContextCompat.getColor(context, color.green)
	private val pink = ContextCompat.getColor(context, color.pink)


	private var flagColor: Int = 0
	private val PX_flag_default = UIManager.dpToPx(context, 10f)
	private val PX_flag_enable = UIManager.dpToPx(context, 100f)

	private var isToggled = false

	private var onEventListener: OnEventListener? = null
	private var flagAnimator: ValueAnimator? = null
	private var toggleAnimator: Animation? = null

	var animate: Boolean = false
		set(value) {
			field = value
			if (!field) {
				if (flagAnimator != null) {
					flagAnimator?.cancel()
					animateFlagColor(if (isToggled) toggleColor else untoggleColor)
				}
				if (toggleAnimator != null) {
					toggleAnimator?.cancel()
					toggle(isToggled)
				}
			}
		}

	private fun getAttrs(attrs: AttributeSet) {
		val typedArray: TypedArray = context.obtainStyledAttributes(attrs, styleable.PackView)
		setTypeArray(typedArray)
	}

	private fun getAttrs(attrs: AttributeSet, defStyle: Int) {
		val typedArray: TypedArray =
			context.obtainStyledAttributes(attrs, styleable.PackView, defStyle, 0)
		setTypeArray(typedArray)
	}

	private fun setTypeArray(typedArray: TypedArray) {

		/*int color = typedArray.getResourceId(R.styleable.PackView_flagColor, R.drawable.border_play_blue);
		//setFlagColor(color);

		String title = typedArray.getString(R.styleable.PackView_title);
		//setTitle(title);

		String subtitle = typedArray.getString(R.styleable.PackView_subtitle);
		//setSubtitle(subtitle);

		Boolean led = typedArray.getBoolean(R.styleable.PackView_led, false);
		//setLED(LED);

		Boolean autoPlay = typedArray.getBoolean(R.styleable.PackView_AutoPlay, false);
		//setAutoPlay(autoPlay);

		String size = typedArray.getString(R.styleable.PackView_size);
		//setSize(size);

		String chain = typedArray.getString(R.styleable.PackView_chain);
		//setChain(chain);

		int capacity = typedArray.getInteger(R.styleable.PackView_capacity, 0);
		//setCapacity(capacity);

		Boolean optionVisibility = typedArray.getBoolean(R.styleable.PackView_optionVisibility, true);
		//setOptionVisibility(optionVisibility);*/


		typedArray.recycle()
	}

	var title: String
		get() = b.title.text.toString()
		set(value) {
			b.title.text = value
		}

	var subtitle: String
		get() = b.subtitle.text.toString()
		set(value) {
			b.subtitle.text = value
		}

	var option1Name: String
		get() = b.option1.text.toString()
		set(value) {
			b.option1.text = value
		}

	var option2Name: String
		get() = b.option2.text.toString()
		set(value) {
			b.option2.text = value
		}

	var option1: Boolean = false
		set(value) {
			field = value
			b.option1.setTextColor(if (field) green else pink)
		}

	var option2: Boolean = false
		set(value) {
			field = value
			b.option2.setTextColor(if (field) green else pink)
		}

	var toggleColor: Int = 0
		set(value) {
			field = value
			if (isToggled)
				animateFlagColor(value)
		}

	var untoggleColor: Int = 0
		set(value) {
			field = value
			if (!isToggled)
				animateFlagColor(value)
		}

	var bookmark: Boolean = false
		set(value) {
			field = value
			b.bookmarkFront.visibility = if (field) View.VISIBLE else View.INVISIBLE
			b.bookmarkBack.visibility = if (field) View.VISIBLE else View.INVISIBLE
		}


	fun showPlayImage(bool: Boolean) {
		b.playImage.visibility = if (bool) View.VISIBLE else View.GONE
	}

	fun setPlayText(str: String?) {
		b.playText.text = str
	}


	//============================================================================================== Flag


	private fun animateFlagColor(colorNext: Int) {
		if (animate) {
			val colorPrev = flagColor

			val flagBackground =
				ContextCompat.getDrawable(context, drawable.border_all_round) as GradientDrawable
			flagAnimator = ObjectAnimator.ofObject(ArgbEvaluator(), colorPrev, colorNext)
			flagAnimator?.duration = 500
			flagAnimator?.addUpdateListener { valueAnimator: ValueAnimator ->
				flagColor = valueAnimator.animatedValue as Int
				flagBackground.setColor(flagColor)
				b.flag.background = flagBackground
			}
			flagAnimator?.start()
		} else {
			val flagBackground =
				ContextCompat.getDrawable(context, drawable.border_all_round) as GradientDrawable
			flagColor = colorNext
			flagBackground.setColor(colorNext)
			b.flag.background = flagBackground
		}
	}

	private fun animateFlagSize(target: Int) {
		if (animate) {
			val start = b.flagSize.layoutParams.width
			val change = target - start

			val params: ViewGroup.LayoutParams = b.flagSize.layoutParams
			toggleAnimator = object : Animation() {
				override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
					params.width = start + (change * interpolatedTime).toInt()
					b.flagSize.layoutParams = params
				}
			}
			toggleAnimator?.duration = 500
			b.flagSize.startAnimation(toggleAnimator)
		} else {
			val params: ViewGroup.LayoutParams = b.flagSize.layoutParams
			params.width = target
			b.flagSize.layoutParams = params
		}
	}


	//============================================================================================== Toggle

	fun toggle(): PackView {
		toggle(!isToggled)
		return this
	}

	fun toggle(bool: Boolean) {
		if (isToggled != bool) {
			if (bool) {
				animateFlagSize(PX_flag_enable)
				animateFlagColor(toggleColor)

				b.playBtn.setOnClickListener { onPlayClick() }
			} else {
				animateFlagSize(PX_flag_default)
				animateFlagColor(untoggleColor)

				b.playBtn.setOnClickListener(null)
				b.playBtn.isClickable = false
			}
			isToggled = bool
		}
	}

	//============================================================================================== Listener

	fun setOnEventListener(listener: OnEventListener) {
		onEventListener = listener
	}

	fun onViewClick() {
		onEventListener?.onViewClick(this)
	}

	fun onViewLongClick() {
		onEventListener?.onViewLongClick(this)
	}

	fun onPlayClick() {
		if (isToggled) onEventListener?.onPlayClick(this)
	}

	interface OnEventListener {
		fun onViewClick(v: PackView)
		fun onViewLongClick(v: PackView)
		fun onPlayClick(v: PackView)
	}
}