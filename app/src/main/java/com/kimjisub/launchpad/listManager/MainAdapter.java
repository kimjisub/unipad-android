package com.kimjisub.launchpad.listManager;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import com.kimjisub.launchpad.MainActivity;
import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.db.vo.UnipackVO;
import com.kimjisub.unipad.designkit.PackViewSimple;

public class MainAdapter extends RecyclerView.Adapter<MainHolder> {

	private MainActivity context;

	public MainAdapter(MainActivity context) {
		this.context = context;
	}

	@Override
	public MainHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		PackViewSimple packViewSimple = new PackViewSimple(parent.getContext());
		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		int left = context.dpToPx(16);
		int top = 0;
		int right = context.dpToPx(16);
		int bottom = context.dpToPx(10);
		lp.setMargins(left, top, right, bottom);
		packViewSimple.setLayoutParams(lp);
		MainHolder mainHolder = new MainHolder(packViewSimple);

		return mainHolder;
	}

	@Override
	public void onBindViewHolder(@NonNull MainHolder mainHolder, int position) {
		MainItem item = context.I_list.get(position);
		PackViewSimple packViewSimple = mainHolder.packViewSimple;

		if (mainHolder.position != -1)
			try {
				context.I_list.get(mainHolder.position).packViewSimple = null;
			} catch (Exception e) {
			}
		item.packViewSimple = packViewSimple;
		mainHolder.position = position;

		UnipackVO unipackVO = context.DB_unipack.getOrCreateByPath(item.unipack.F_project.getName());

		String title = item.unipack.title;
		String subTitle = item.unipack.producerName;

		if (item.unipack.CriticalError) {
			item.flagColor = context.color(R.color.red);
			title = context.lang(R.string.errOccur);
			subTitle = item.path;
		} else
			item.flagColor = context.color(R.color.skyblue);

		if (unipackVO.bookmark)
			item.flagColor = context.color(R.color.orange);


		packViewSimple
				.cancelAllAnimation()
				.setFlagColor(item.flagColor)
				.setTitle(title)
				.setSubTitle(subTitle)
				.setOption1(context.lang(R.string.LED_), item.unipack.isKeyLED)
				.setOption2(context.lang(R.string.autoPlay_), item.unipack.isAutoPlay)
				.setOnEventListener(new PackViewSimple.OnEventListener() {
					@Override
					public void onViewClick(PackViewSimple v) {
						if (!item.isMoving)
							context.togglePlay(item);
					}

					@Override
					public void onViewLongClick(PackViewSimple v) {
					}

					@Override
					public void onPlayClick(PackViewSimple v) {
						if (!item.isMoving)
							context.pressPlay(item);
					}
				})
				.setToggle(item.isToggle, context.color(R.color.red), item.flagColor);


		Animation a = AnimationUtils.loadAnimation(context, R.anim.pack_in);
		if(item.isNew)
			a = AnimationUtils.loadAnimation(context, R.anim.pack_new_in);
		item.isNew = false;
		packViewSimple.setAnimation(a);
	}

	@Override
	public int getItemCount() {
		return context.I_list.size();
	}


}