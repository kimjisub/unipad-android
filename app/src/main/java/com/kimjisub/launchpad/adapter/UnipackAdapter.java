package com.kimjisub.launchpad.adapter;

import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kimjisub.design.PackViewSimple;
import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.activity.BaseActivity;
import com.kimjisub.manager.Log;

import java.util.ArrayList;

public class UnipackAdapter extends RecyclerView.Adapter<UnipackHolder> {

	private BaseActivity context;
	private ArrayList<UnipackItem> list;

	public UnipackAdapter(BaseActivity context, ArrayList<UnipackItem> list, EventListener eventListener) {
		this.context = context;
		this.list = list;
		this.eventListener = eventListener;
	}

	int viewHolderCount = 0;
	@NonNull
	@Override
	public UnipackHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		Log.test("onCreateViewHolder: " + viewHolderCount++);
		PackViewSimple packViewSimple = new PackViewSimple(parent.getContext());
		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		packViewSimple.setLayoutParams(lp);

		return new UnipackHolder(packViewSimple);
	}

	@Override
	public void onBindViewHolder(@NonNull UnipackHolder holder, int position) {
		Log.test("onBindViewHolder: " + position);
		UnipackItem item = list.get(holder.getAdapterPosition());
		PackViewSimple packViewSimple = holder.packViewSimple;

		// 이전 데이터에 매핑된 뷰를 제거합니다.
		try {
			list.get(holder.position).packViewSimple = null;
		} catch (Exception ignored) {
		}

		// 새롭게 할당될 데이터에 뷰를 할당하고 홀더에도 해당 포지션을 등록합니다.
		item.packViewSimple = packViewSimple;
		holder.position = holder.getAdapterPosition();

		////////////////////////////////////////////////////////////////////////////////////////////////

		String title = item.unipack.title;
		String subTitle = item.unipack.producerName;

		if (item.unipack.CriticalError) {
			item.flagColor = context.color(R.color.red);
			title = context.lang(R.string.errOccur);
			subTitle = item.path;
		} else
			item.flagColor = context.color(R.color.skyblue);

		if (item.bookmark)
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
						eventListener.onViewClick(item, v);
					}

					@Override
					public void onViewLongClick(PackViewSimple v) {
						eventListener.onViewLongClick(item, v);
					}

					@Override
					public void onPlayClick(PackViewSimple v) {
						eventListener.onPlayClick(item, v);
					}
				})
				.setToggle(item.isToggle, context.color(R.color.red), item.flagColor);


		Animation a = AnimationUtils.loadAnimation(context, R.anim.pack_in);
		if (item.isNew)
			a = AnimationUtils.loadAnimation(context, R.anim.pack_new_in);
		item.isNew = false;
		packViewSimple.setAnimation(a);
	}

	@Override
	public int getItemCount() {
		return list.size();
	}


	////////////////////////////////////////////////////////////////////////////////////////////////

	private EventListener eventListener;

	public interface EventListener {
		void onViewClick(UnipackItem item, PackViewSimple v);

		void onViewLongClick(UnipackItem item, PackViewSimple v);

		void onPlayClick(UnipackItem item, PackViewSimple v);
	}
}