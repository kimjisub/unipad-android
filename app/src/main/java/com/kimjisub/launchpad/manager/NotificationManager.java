package com.kimjisub.launchpad.manager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.StringDef;

import com.kimjisub.launchpad.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;

public class NotificationManager {

	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
			Group.GENERAL,
			Group.NOTICE
	})
	public @interface Group {
		String GENERAL = "general";
		String NOTICE = "notice";
	}

	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
			Channel.DOWNLOAD,
			Channel.NEW_PACK,
			Channel.NEW_PACK
	})
	public @interface Channel {
		String DOWNLOAD = "download";
		String NOTICE = "notice";
		String NEW_PACK = "newPack";
	}

	public static void createChannel(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			android.app.NotificationManager manager = getManager(context);

			{
				NotificationChannelGroup GENERAL = new NotificationChannelGroup(Group.GENERAL, context.getString(R.string.general));
				manager.createNotificationChannelGroup(GENERAL);

				NotificationChannelGroup NOTICE = new NotificationChannelGroup(Group.NOTICE, context.getString(R.string.notice));
				manager.createNotificationChannelGroup(NOTICE);
			}

			{
				NotificationChannel DOWNLOAD = new NotificationChannel(
						Channel.DOWNLOAD,
						context.getString(R.string.download),
						IMPORTANCE_DEFAULT);
				DOWNLOAD.setDescription(context.getString(R.string.download) + " disc");
				DOWNLOAD.setGroup(Group.GENERAL);
				DOWNLOAD.setLightColor(Color.GREEN);
				DOWNLOAD.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
				manager.createNotificationChannel(DOWNLOAD);

				NotificationChannel NOTICE = new NotificationChannel(
						Channel.NOTICE,
						context.getString(R.string.notice),
						IMPORTANCE_DEFAULT);
				NOTICE.setDescription(context.getString(R.string.notice) + " disc");
				NOTICE.setGroup(Group.NOTICE);
				NOTICE.setLightColor(Color.BLUE);
				NOTICE.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
				manager.createNotificationChannel(NOTICE);

				NotificationChannel NEW_PACK = new NotificationChannel(
						Channel.NEW_PACK,
						context.getString(R.string.newUnipack),
						IMPORTANCE_DEFAULT);
				NEW_PACK.setDescription(context.getString(R.string.newUnipack) + " disc");
				NEW_PACK.setGroup(Group.NOTICE);
				NEW_PACK.setLightColor(Color.BLUE);
				NEW_PACK.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
				manager.createNotificationChannel(NEW_PACK);
			}

		}
	}

	public static android.app.NotificationManager getManager(Context context) {
		return (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	public static void deleteChannel(Context context, @Channel String channel) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			getManager(context).deleteNotificationChannel(channel);
	}

	public static void sendNotification(Context context, int id, @Channel String channel, String title, String body) {
		Notification.Builder builder = new Notification.Builder(context)
				.setContentTitle(title)
				.setContentText(body)
				.setSmallIcon(R.mipmap.ic_launcher)
				.setAutoCancel(true);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			builder.setChannelId(channel);

		getManager(context).notify(id, builder.build());
	}
}
