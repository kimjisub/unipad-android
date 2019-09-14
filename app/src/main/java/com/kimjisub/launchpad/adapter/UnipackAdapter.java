package com.kimjisub.launchpad.adapter;

import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kimjisub.design.PackView;
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
		PackView packView = new PackView(parent.getContext());
		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		packView.setLayoutParams(lp);

		return new UnipackHolder(packView);
	}

	@Override
	public void onBindViewHolder(@NonNull UnipackHolder holder, int position) {
		Log.test("onBindViewHolder: " + position);
		UnipackItem item = list.get(holder.getAdapterPosition());
		PackView packView = holder.packView;

		// 이전 데이터에 매핑된 뷰를 제거합니다.
		try {
			list.get(holder.position).packView = null;
		} catch (Exception ignored) {
		}

		// 새롭게 할당될 데이터에 뷰를 할당하고 홀더에도 해당 포지션을 등록합니다.
		item.packView = packView;
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



		packView.setAnimate(false);
		packView.setToggleColor(context.color(R.color.red));
		packView.setUntoggleColor(item.flagColor);
		packView.setTitle(title);
		packView.setSubtitle(subTitle);
		packView.setOption1Name(context.lang(R.string.LED_));
		packView.setOption1(item.unipack.isKeyLED);
		packView.setOption2Name(context.lang(R.string.autoPlay_));
		packView.setOption2(item.unipack.isAutoPlay);
		packView.setOnEventListener(new PackView.OnEventListener() {
					@Override
					public void onViewClick(PackView v) {
						eventListener.onViewClick(item, v);
					}

					@Override
					public void onViewLongClick(PackView v) {
						eventListener.onViewLongClick(item, v);
					}

					@Override
					public void onPlayClick(PackView v) {
						eventListener.onPlayClick(item, v);
					}
				});
		packView.toggle(item.isToggle);
		packView.setAnimate(true);


		Animation a = AnimationUtils.loadAnimation(context, R.anim.pack_in);
		if (item.isNew)
			a = AnimationUtils.loadAnimation(context, R.anim.pack_new_in);
		item.isNew = false;
		packView.setAnimation(a);
	}

	@Override
	public int getItemCount() {
		return list.size();
	}


	////////////////////////////////////////////////////////////////////////////////////////////////

	private EventListener eventListener;

	public interface EventListener {
		void onViewClick(UnipackItem item, PackView v);

		void onViewLongClick(UnipackItem item, PackView v);

		void onPlayClick(UnipackItem item, PackView v);
	}
}