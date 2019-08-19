package com.kimjisub.launchpad.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import com.kimjisub.design.PackViewSimple;
import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.activity.BaseActivity;

import java.util.ArrayList;

public class StoreAdapter extends RecyclerView.Adapter<StoreHolder> {

	BaseActivity context;
	ArrayList<StoreItem> list;

	public StoreAdapter(BaseActivity context, ArrayList<StoreItem> list, EventListener eventListener) {
		this.context = context;
		this.list = list;
		this.eventListener = eventListener;
	}

	@Override
	public StoreHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		PackViewSimple packViewSimple = new PackViewSimple(parent.getContext());
		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		packViewSimple.setLayoutParams(lp);
		StoreHolder unipackHolder = new StoreHolder(packViewSimple);

		return unipackHolder;
	}

	@Override
	public void onBindViewHolder(@NonNull StoreHolder unipackHolder, int position) {
		StoreItem item = list.get(position);
		PackViewSimple packViewSimple = unipackHolder.packViewSimple;


		// 이전 데이터에 매핑된 뷰를 제거합니다.
		try {
			list.get(unipackHolder.position).packViewSimple = null;
		} catch (Exception ignored) {
		}

		// 새롭게 할당될 데이터에 뷰를 할당하고 홀더에도 해당 포지션을 등록합니다.
		item.packViewSimple = packViewSimple;
		unipackHolder.position = position;

		////////////////////////////////////////////////////////////////////////////////////////////////

		packViewSimple
				.setFlagColor(context.color(item.isDownloaded ? R.color.green : R.color.red))
				.setTitle(item.fbStore.title)
				.setSubTitle(item.fbStore.producerName)
				.setOption1(context.lang(R.string.LED_), item.fbStore.isLED)
				.setOption2(context.lang(R.string.autoPlay_), item.fbStore.isAutoPlay)
				.setPlayImageShow(false)
				.setPlayText(context.lang(item.isDownloaded ? R.string.downloaded : R.string.download))
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
				});

		Animation a = AnimationUtils.loadAnimation(context, R.anim.pack_in);
		packViewSimple.setAnimation(a);
	}

	@Override
	public int getItemCount() {
		return list.size();
	}


	////////////////////////////////////////////////////////////////////////////////////////////////

	EventListener eventListener;

	public interface EventListener {
		public void onViewClick(StoreItem item, PackViewSimple v);

		public void onViewLongClick(StoreItem item, PackViewSimple v);

		public void onPlayClick(StoreItem item, PackViewSimple v);
	}
}