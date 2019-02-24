package com.kimjisub.launchpad.listManager;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.kimjisub.launchpad.MainActivity;
import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.db.vo.UnipackVO;
import com.kimjisub.unipad.designkit.PackViewSimple;

public class MainAdapter extends RecyclerView.Adapter<MainAdapter.UniPackHolder> {

	private MainActivity context;

	public MainAdapter(MainActivity context) {
		this.context = context;
	}

	@Override
	public UniPackHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		PackViewSimple packViewSimple = new PackViewSimple(parent.getContext());
		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		int left = context.dpToPx(16);
		int top = 0;
		int right = context.dpToPx(16);
		int bottom = context.dpToPx(10);
		lp.setMargins(left, top, right, bottom);
		packViewSimple.setLayoutParams(lp);
		UniPackHolder uniPackHolder = new UniPackHolder(packViewSimple);

		return uniPackHolder;
	}

	@Override
	public void onBindViewHolder(@NonNull UniPackHolder uniPackHolder, int position) {
		MainItem item = context.I_list.get(position);
		PackViewSimple packViewSimple = uniPackHolder.packViewSimple;

		if (uniPackHolder.position != -1)
			try {
				context.I_list.get(uniPackHolder.position).packViewSimple = null;
			} catch (Exception e) {
			}
		item.packViewSimple = packViewSimple;
		uniPackHolder.position = position;

		UnipackVO unipackVO = context.DB_unipack.getOrCreateByPath(item.path);

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
						context.togglePlay(item.path);
					}

					@Override
					public void onViewLongClick(PackViewSimple v) {
					}

					@Override
					public void onPlayClick(PackViewSimple v) {
						context.pressPlay(item.path);
					}
				})
				.setToggle(item.toggle, context.color(R.color.red), item.flagColor);
	}

	@Override
	public int getItemCount() {
		return context.I_list.size();
	}

	public static class UniPackHolder extends RecyclerView.ViewHolder {
		PackViewSimple packViewSimple;
		int position = -1;

		UniPackHolder(PackViewSimple view) {
			super(view);
			packViewSimple = view;
		}
	}
}