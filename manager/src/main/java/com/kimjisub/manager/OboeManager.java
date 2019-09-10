package com.kimjisub.manager;

public class OboeManager {
	static long mEngineHandle = 0;

	static {
		System.loadLibrary("oboe-manager");
	}

	static boolean create(){

		if (mEngineHandle == 0){
			mEngineHandle = native_createEngine();
		}
		return (mEngineHandle != 0);
	}

	static void delete(){
		if (mEngineHandle != 0){
			native_deleteEngine(mEngineHandle);
		}
		mEngineHandle = 0;
	}

	static void setToneOn(boolean isToneOn){
		if (mEngineHandle != 0) native_setToneOn(mEngineHandle, isToneOn);
	}

	static void setAudioApi(int audioApi){
		if (mEngineHandle != 0) native_setAudioApi(mEngineHandle, audioApi);
	}

	static void setAudioDeviceId(int deviceId){
		if (mEngineHandle != 0) native_setAudioDeviceId(mEngineHandle, deviceId);
	}

	static void setChannelCount(int channelCount) {
		if (mEngineHandle != 0) native_setChannelCount(mEngineHandle, channelCount);
	}

	static void setBufferSizeInBursts(int bufferSizeInBursts){
		if (mEngineHandle != 0) native_setBufferSizeInBursts(mEngineHandle, bufferSizeInBursts);
	}

	static double getCurrentOutputLatencyMillis(){
		if (mEngineHandle == 0) return 0;
		return native_getCurrentOutputLatencyMillis(mEngineHandle);
	}

	static boolean isLatencyDetectionSupported() {
		return mEngineHandle != 0 && native_isLatencyDetectionSupported(mEngineHandle);
	}

	// Native methods
	private static native long native_createEngine();
	private static native void native_deleteEngine(long engineHandle);
	private static native void native_setToneOn(long engineHandle, boolean isToneOn);
	private static native void native_setAudioApi(long engineHandle, int audioApi);
	private static native void native_setAudioDeviceId(long engineHandle, int deviceId);
	private static native void native_setChannelCount(long mEngineHandle, int channelCount);
	private static native void native_setBufferSizeInBursts(long engineHandle, int bufferSizeInBursts);
	private static native double native_getCurrentOutputLatencyMillis(long engineHandle);
	private static native boolean native_isLatencyDetectionSupported(long engineHandle);
}
