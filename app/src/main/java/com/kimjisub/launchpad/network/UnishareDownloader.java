package com.kimjisub.launchpad.network;

import android.os.AsyncTask;

import com.kimjisub.launchpad.api.file.FileApi;
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO;
import com.kimjisub.launchpad.manager.Unipack;
import com.kimjisub.manager.FileManager;
import com.kimjisub.manager.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.ResponseBody;
import retrofit2.Call;

public class UnishareDownloader {
	UnishareVO unishare;
	File F_UniPackRootExt;
	UnishareDownloadListener listener;

	File zip;
	File folder;

	public interface UnishareDownloadListener {
		void onDownloadStart();

		void onGetFileSize(long fileSize);

		void onDownloading(long downloadedSize);

		void onDownloadEnd(File zip);

		void onAnalyzeStart();

		void onAnalyzeSuccess(Unipack unipack);

		void onAnalyzeFail(Unipack unipack);

		void onAnalyzeEnd(File folder);

		void onException(Throwable throwable);
	}

	public UnishareDownloader(UnishareVO unishare, File F_UniPackRootExt, UnishareDownloadListener listener) {
		this.unishare = unishare;
		this.F_UniPackRootExt = F_UniPackRootExt;
		this.listener = listener;

		zip = FileManager.makeNextPath(F_UniPackRootExt, unishare.title + " #" + unishare._id, ".zip");
		folder = FileManager.makeNextPath(F_UniPackRootExt, unishare.title + " #" + unishare._id, "/");

		(new DownloadThread()).execute();
	}

	class DownloadThread extends AsyncTask<String, String, String> {

		@Override
		protected String doInBackground(String... strings) {
			try {
				listener.onDownloadStart();
				Call<ResponseBody> call = FileApi.getService().unishare_download(unishare._id);
				ResponseBody responseBody = call.execute().body();
				InputStream in = responseBody.byteStream();

				listener.onGetFileSize(responseBody.contentLength());

				FileOutputStream out = new FileOutputStream(zip);
				byte[] buf = new byte[1024];
				long downloadSize = 0L;
				int n;
				int loop = 0;
				while (-1 != (n = in.read(buf))) {
					out.write(buf, 0, n);
					downloadSize += n != -1 ? n : 0;
					if (loop % 100 == 0)
						listener.onDownloading(downloadSize);
					loop++;
				}
				in.close();
				out.close();

				listener.onDownloadEnd(zip);


				listener.onAnalyzeStart();

				FileManager.unZipFile(zip.getPath(), folder.getPath());
				Unipack unipack = new Unipack(folder, true);
				if (unipack.CriticalError) {
					Log.err(unipack.ErrorDetail);

					listener.onAnalyzeFail(unipack);
					FileManager.deleteDirectory(folder);
				} else
					listener.onAnalyzeSuccess(unipack);

				listener.onAnalyzeEnd(folder);

			} catch (Exception e) {
				e.printStackTrace();
				listener.onException(e);
				FileManager.deleteDirectory(folder);
			}
			FileManager.deleteDirectory(zip);

			return null;
		}
	}
}