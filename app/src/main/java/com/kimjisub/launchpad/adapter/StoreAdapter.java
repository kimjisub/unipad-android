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
import java.util.List;

public class StoreAdapter extends RecyclerView.Adapter<StoreHolder> {

	private BaseActivity context;
	private ArrayList<StoreItem> list;

	public StoreAdapter(BaseActivity context, ArrayList<StoreItem> list, EventListener eventListener) {
		this.context = context;
		this.list = list;
		this.eventListener = eventListener;
	}

	int viewHolderCount = 0;

	@NonNull
	@Override
	public StoreHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		Log.test("onCreateViewHolder: " + viewHolderCount++);
		PackViewSimple packViewSimple = new PackViewSimple(parent.getContext());
		final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		packViewSimple.setLayoutParams(lp);

		return new StoreHolder(packViewSimple);
	}

	@Override
	public void onBindViewHolder(@NonNull StoreHolder holder, int position) {
		Log.test("onBindViewHolder: " + position);
		StoreItem item = list.get(holder.getAdapterPosition());
		PackViewSimple packViewSimple = holder.packViewSimple;


		// 이전 데이터에 매핑된 뷰를 제거합니다.
		try {
			list.get(holder.position).setPackViewSimple(null);
		} catch (Exception ignored) {
		}

		// 새롭게 할당될 데이터에 뷰를 할당하고 홀더에도 해당 포지션을 등록합니다.
		item.setPackViewSimple(packViewSimple);
		holder.position = holder.getAdapterPosition();

		////////////////////////////////////////////////////////////////////////////////////////////////

		packViewSimple
				.setFlagColor(context.color(item.isDownloaded() ? R.color.green : R.color.red))
				.setTitle(item.getStoreVO().getTitle())
				.setSubTitle(item.getStoreVO().getProducerName())
				.setOption1(context.lang(R.string.LED_), item.getStoreVO().isLED())
				.setOption2(context.lang(R.string.autoPlay_), item.getStoreVO().isAutoPlay())
				.setPlayImageShow(false)
				.setPlayText(context.lang(item.isDownloaded() ? R.string.downloaded : R.string.download))
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
	public void onBindViewHolder(@NonNull StoreHolder holder, int position, @NonNull List<Object> payloads) {
		if (payloads.isEmpty()) {
			super.onBindViewHolder(holder, position, payloads);
			return;
		}
		Log.test("onBindViewHolder payloads: " + position);

		StoreItem item = list.get(holder.getAdapterPosition());
		PackViewSimple packViewSimple = holder.packViewSimple;

		for (Object payload : payloads) {
			if (payload instanceof String) {
				switch ((String) payload) {
					case "update":
						Log.test("update");
						packViewSimple
								.setFlagColor(context.color(item.isDownloaded() ? R.color.green : R.color.red))
								.setTitle(item.getStoreVO().getTitle())
								.setSubTitle(item.getStoreVO().getProducerName())
								.setOption1(context.lang(R.string.LED_), item.getStoreVO().isLED())
								.setOption2(context.lang(R.string.autoPlay_), item.getStoreVO().isAutoPlay())
								.setPlayText(context.lang(item.isDownloaded() ? R.string.downloaded : R.string.download));
						break;
				}
				/*String type = (String) payload;
				if (TextUtils.equals(type, "click") && holder instanceof TextHolder) {
					TextHolder textHolder = (TextHolder) holder;
					textHolder.mFavorite.setVisibility(View.VISIBLE);
					textHolder.mFavorite.setAlpha(0f);
					textHolder.mFavorite.setScaleX(0f);
					textHolder.mFavorite.setScaleY(0f);

					//animation
					textHolder.mFavorite.animate()
							.scaleX(1f)
							.scaleY(1f)
							.alpha(1f)
							.setInterpolator(new OvershootInterpolator())
							.setDuration(300);

				}*/
			}
		}
	}

	@Override
	public int getItemCount() {
		return list.size();
	}

	////////////////////////////////////////////////////////////////////////////////////////////////

	private EventListener eventListener;

	public interface EventListener {
		void onViewClick(StoreItem item, PackViewSimple v);

		void onViewLongClick(StoreItem item, PackViewSimple v);

		void onPlayClick(StoreItem item, PackViewSimple v);
	}
}