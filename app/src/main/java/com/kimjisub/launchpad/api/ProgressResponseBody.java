package com.kimjisub.launchpad.api;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

public class ProgressResponseBody extends ResponseBody {

	private final ResponseBody responseBody;
	private final OnAttachmentDownloadListener progressListener;
	private BufferedSource bufferedSource;
	public interface OnAttachmentDownloadListener {
		void onAttachmentDownloadedSuccess();
		void onAttachmentDownloadedError();
		void onAttachmentDownloadedFinished();
		void onAttachmentDownloadUpdate(int percent);
	}

	public ProgressResponseBody(ResponseBody responseBody, OnAttachmentDownloadListener progressListener) {
		this.responseBody = responseBody;
		this.progressListener = progressListener;
	}

	@Override public MediaType contentType() {
		return responseBody.contentType();
	}

	@Override public long contentLength() {
		return responseBody.contentLength();
	}

	@Override public BufferedSource source() {
		if (bufferedSource == null) {
			bufferedSource = Okio.buffer(source(responseBody.source()));
		}
		return bufferedSource;
	}

	private Source source(Source source) {
		return new ForwardingSource(source) {
			long totalBytesRead = 0L;

			@Override public long read(Buffer sink, long byteCount) throws IOException {
				long bytesRead = super.read(sink, byteCount);

				totalBytesRead += bytesRead != -1 ? bytesRead : 0;

				float percent = bytesRead == -1 ? 100f : (((float)totalBytesRead / (float) responseBody.contentLength()) * 100);

				if(progressListener != null)
					progressListener.onAttachmentDownloadUpdate((int)percent);

				return bytesRead;
			}
		};
	}
}