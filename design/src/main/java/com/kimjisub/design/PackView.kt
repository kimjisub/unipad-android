package com.kimjisub.design

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.RelativeLayout
import com.kimjisub.design.R.*
import com.kimjisub.manager.UIManager
import kotlinx.android.synthetic.main.packview.view.*

class PackView : RelativeLayout {

	@JvmOverloads
	constructor(
			context: Context,
			attrs: AttributeSet? = null,
			defStyleAttr: Int = 0)
			: super(context, attrs, defStyleAttr)

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(
			context: Context,
			attrs: AttributeSet?,
			defStyleAttr: Int,
			defStyleRes: Int)
			: super(context, attrs, defStyleAttr, defStyleRes)

	init {
		LayoutInflater.from(context)
				.inflate(layout.packview, this, true)

		// set listener
		LL_touchView.setOnClickListener { onViewClick() }
		LL_touchView.setOnLongClickListener {
			onViewLongClick()
			true
		}
	}


	private val green = resources.getColor(color.green)
	private val pink = resources.getColor(color.pink)


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
		val typedArray: TypedArray = getContext().obtainStyledAttributes(attrs, styleable.PackView)
		setTypeArray(typedArray)
	}

	private fun getAttrs(attrs: AttributeSet, defStyle: Int) {
		val typedArray: TypedArray = context.obtainStyledAttributes(attrs, styleable.PackView, defStyle, 0)
		setTypeArray(typedArray)
	}

	private fun setTypeArray(typedArray: TypedArray) {

		/*int color = typedArray.getResourceId(R.styleable.PackView_flagColor, R.drawable.border_play_blue);
		//setFlagColor(color);

		String title = typedArray.getString(R.styleable.PackView_title);
		//setTitle(title);

		String subtitle = typedArray.getString(R.styleable.PackView_subtitle);
		//setSubtitle(subtitle);

		Boolean LED = typedArray.getBoolean(R.styleable.PackView_LED, false);
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
		get() = TV_title.text.toString()
		set(value) {
			TV_title.text = value
		}

	var subtitle: String
		get() = TV_subtitle.text.toString()
		set(value) {
			TV_subtitle.text = value
		}

	var option1Name: String
		get() = TV_option1.text.toString()
		set(value) {
			TV_option1.text = value
		}

	var option2Name: String
		get() = TV_option1.text.toString()
		set(value) {
			TV_option2.text = value
		}

	var option1: Boolean = false
		set(value) {
			field = value
			TV_option1.setTextColor(if (field) green else pink)
		}

	var option2: Boolean = false
		set(value) {
			field = value
			TV_option2.setTextColor(if (field) green else pink)
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


	fun showPlayImage(bool: Boolean) {
		IV_playImg!!.visibility = if (bool) View.VISIBLE else View.GONE
	}

	fun setPlayText(str: String?) {
		TV_playText!!.text = str
	}


	//============================================================================================== Flag


	private fun animateFlagColor(colorNext: Int) {
		if (animate) {
			val colorPrev = flagColor

			val flagBackground = resources.getDrawable(drawable.border_all_round) as GradientDrawable
			flagAnimator = ObjectAnimator.ofObject(ArgbEvaluator(), colorPrev, colorNext)
			flagAnimator?.duration = 500
			flagAnimator?.addUpdateListener { valueAnimator: ValueAnimator ->
				flagColor = valueAnimator.animatedValue as Int
				flagBackground.setColor(flagColor)
				RL_flag!!.background = flagBackground
			}
			flagAnimator?.start()
		} else {
			val flagBackground = resources.getDrawable(drawable.border_all_round) as GradientDrawable
			flagColor = colorNext
			flagBackground.setColor(colorNext)
			RL_flag!!.background = flagBackground
		}
	}

	private fun animateFlagSize(target: Int) {
		if (animate) {
			val start = RL_flagSize.layoutParams.width
			val change = target - start

			val params: ViewGroup.LayoutParams = RL_flagSize.layoutParams
			toggleAnimator = object : Animation() {
				override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
					params.width = start + (change * interpolatedTime).toInt()
					RL_flagSize.layoutParams = params
				}
			}
			toggleAnimator?.duration = 500
			RL_flagSize.startAnimation(toggleAnimator)
		} else {
			val params: ViewGroup.LayoutParams = RL_flagSize.layoutParams
			params.width = target
			RL_flagSize.layoutParams = params
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

				RL_playBtn!!.setOnClickListener { onPlayClick() }
			} else {
				animateFlagSize(PX_flag_default)
				animateFlagColor(untoggleColor)

				RL_playBtn!!.setOnClickListener(null)
				RL_playBtn!!.isClickable = false
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