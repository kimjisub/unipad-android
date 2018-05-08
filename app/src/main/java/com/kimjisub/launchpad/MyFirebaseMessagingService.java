package com.kimjisub.launchpad;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import static com.kimjisub.launchpad.manage.Tools.logFirebase;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
	@Override
	public void onMessageReceived(RemoteMessage remoteMessage) {
		// TODO(developer): Handle FCM messages here.
		logFirebase("From: " + remoteMessage.getFrom());
		
		if (remoteMessage.getData().size() > 0) {
			logFirebase("Message data payload: " + remoteMessage.getData());
			
			if (/* Check if data needs to be processed by long running job */ true) {
				scheduleJob();
			} else {
				handleNow();
			}
			
		}
		
		if (remoteMessage.getNotification() != null) {
			logFirebase("Message Notification Body: " + remoteMessage.getNotification().getBody());
		}
	}
	
	private void scheduleJob() {
		/*FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(this));
		Job myJob = dispatcher.newJobBuilder()
			.setService(MyJobService.class)
			.setTag("my-job-tag")
			.build();
		dispatcher.schedule(myJob);*/
	}
	
	private void handleNow() {
		logFirebase("Short lived task is done.");
	}
	
	/*private void sendNotification(String messageBody) {
		Intent intent = new Intent(this, Mainctivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 *//* Request code *//*, intent,
			PendingIntent.FLAG_ONE_SHOT);
		
		String channelId = getString(R.string.default_notification_channel_id);
		Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
		NotificationCompat.Builder notificationBuilder =
			new NotificationCompat.Builder(this, channelId)
				.setSmallIcon(R.drawable.ic_stat_ic_notification)
				.setContentTitle("FCM Message")
				.setContentText(messageBody)
				.setAutoCancel(true)
				.setSound(defaultSoundUri)
				.setContentIntent(pendingIntent);
		
		NotificationManager notificationManager =
			(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(channelId,
				"Channel human readable title",
				NotificationManager.IMPORTANCE_DEFAULT);
			notificationManager.createNotificationChannel(channel);
		}
		
		notificationManager.notify(0 *//* ID of notification *//*, notificationBuilder.build());
	}*/
}