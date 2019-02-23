package com.kimjisub.launchpad.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Environment;

import net.sf.jazzlib.ZipEntry;
import net.sf.jazzlib.ZipInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;

import static android.content.Context.MODE_PRIVATE;

public class FileManager {

	// ============================================================================================= Zip

	public static void unZipFile(String zipFileURL, String location) throws IOException {
		InputStream zipFile = new FileInputStream(zipFileURL);

		int size;
		byte[] buffer = new byte[1024];

		try {
			if (!location.endsWith("/")) {
				location += "/";
			}
			File f = new File(location);
			if (!f.isDirectory())
				f.mkdirs();
			ZipInputStream zin = new ZipInputStream(new BufferedInputStream(zipFile, 1024));
			try {
				ZipEntry ze;
				while ((ze = zin.getNextEntry()) != null) {
					String path = location + ze.getName();
					File unzipFile = new File(path);

					if (ze.isDirectory()) {
						if (!unzipFile.isDirectory())
							unzipFile.mkdirs();
					} else {
						File parentDir = unzipFile.getParentFile();
						if (null != parentDir) {
							if (!parentDir.isDirectory())
								parentDir.mkdirs();
						}

						FileOutputStream out = new FileOutputStream(unzipFile, false);
						BufferedOutputStream fout = new BufferedOutputStream(out, 1024);
						try {
							while ((size = zin.read(buffer, 0, 1024)) != -1) {
								fout.write(buffer, 0, size);
							}

							zin.closeEntry();
						} catch (Exception e) {
							e.printStackTrace();
						}
						fout.flush();
						fout.close();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			zin.close();

			removeDoubleFolder(location);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void removeDoubleFolder(String path) {
		try {
			File rootFolder = new File(path);

			if (rootFolder.isDirectory()) {
				File[] childFileList = rootFolder.listFiles();
				if (childFileList.length == 1) {
					File innerFolder = childFileList[0];
					if (innerFolder.isDirectory()) {
						moveDirectory(innerFolder, rootFolder);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// ============================================================================================= Tools

	public static File[] sortByTime(File[] files) {

		for (int i = 0; i < files.length - 1; i++) {
			for (int j = 0; j < files.length - (i + 1); j++) {
				if (files[j].lastModified() < files[j + 1].lastModified()) {
					File tmp = files[j + 1];
					files[j + 1] = files[j];
					files[j] = tmp;
				}
			}
		}

		return files;
	}

	public static File[] sortByName(File[] files) {

		Arrays.sort(files, new Comparator<Object>() {
			@Override
			public int compare(Object object1, Object object2) {
				return ((File) object1).getName().toLowerCase().compareTo(((File) object2).getName().toLowerCase());
			}
		});

		return files;
	}

	public static String makeNextPath(String path, String name, String extension) {
		String ret;
		String newName = convertFilename(name);
		for (int i = 1; ; i++) {
			if (i == 1)
				ret = path + "/" + newName + extension;
			else
				ret = path + "/" + newName + " (" + i + ")" + extension;

			if (!new File(ret).exists())
				break;
		}

		Log.test(path + "/" + newName + extension);
		Log.test(ret);
		return ret;
	}

	public static String convertFilename(String orgnStr) {
		String regExpr = "[|\\\\?*<\":>/]+";

		String tmpStr = orgnStr.replaceAll(regExpr, "");

		return tmpStr;

		//return tmpStr.replaceAll("[ ]", "_");
	}

	// ============================================================================================= Make, Move, Copy, Delete

	public static void moveDirectory(File F_source, File F_target) {
		Log.test(F_source.getPath() + " -> " + F_target.getPath());
		try {
			if(!F_target.isDirectory())
				F_target.mkdir();

			File[] sourceList = F_source.listFiles();
			for (File source : sourceList) {
				File target = new File(F_target.getAbsolutePath() + "/" + source.getName());
				if (source.isDirectory()) {
					Log.test("Dir  " + target.getPath());
					target.mkdir();
					moveDirectory(source, target);
				} else {
					Log.test("File " + target.getPath());
					FileInputStream fis = null;
					FileOutputStream fos = null;
					try {
						fis = new FileInputStream(source);
						fos = new FileOutputStream(target);
						byte[] b = new byte[4096];
						int cnt = 0;
						while ((cnt = fis.read(b)) != -1) {
							fos.write(b, 0, cnt);
						}
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						try {
							fis.close();
							fos.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}

			deleteDirectory(F_source.getPath());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void deleteDirectory(String path) {

		try {
			File file = new File(path);

			if (file.isDirectory()) {
				File[] childFileList = file.listFiles();
				for (File childFile : childFileList)
					deleteDirectory(childFile.getPath());
				file.delete();
			} else
				file.delete();


		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// ============================================================================================= Get Info

	public static void makeNomedia(String path) {
		File nomedia = new File(path + "/.nomedia");
		if (!nomedia.isFile()) {
			try {
				(new FileWriter(nomedia)).close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@SuppressLint("DefaultLocale")
	public static String byteToMB(float Byte) {
		return String.format("%.2f", Byte / 1024L / 1024L);
	}

	public static long getFolderSize(String a_path) {
		long totalMemory = 0;
		File file = new File(a_path);

		if (file.isFile()) {
			return file.length();
		} else if (file.isDirectory()) {
			File[] childFileList = file.listFiles();
			if (childFileList == null)
				return 0;

			for (File childFile : childFileList)
				totalMemory += getFolderSize(childFile.getAbsolutePath());

			return totalMemory;
		} else
			return 0;
	}

	public static String getAppUniPackStoragePath(Context context) {
		return context.getDir("UniPack", MODE_PRIVATE).getPath();
	}

	public static String getInternalStoragePath() {
		return Environment.getExternalStorageDirectory().getPath();
	}

	public static String getExternalSDCardPath() {
		HashSet<String> hs = getExternalMounts();
		for (String extSDCardPath : hs) {
			return extSDCardPath;
		}
		return null;
	}

	public static HashSet<String> getExternalMounts() {
		final HashSet<String> out = new HashSet<String>();
		String reg = "(?i).*media_rw.*(storage).*(sdcardfs).*rw.*";
		String s = "";
		try {
			final Process process = new ProcessBuilder().command("mount").redirectErrorStream(true).start();
			process.waitFor();
			final InputStream is = process.getInputStream();
			final byte[] buffer = new byte[1024];
			while (is.read(buffer) != -1) {
				s = s + new String(buffer);
			}
			is.close();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		final String[] lines = s.split("\n");
		for (String line : lines) {
			if (!line.toLowerCase(Locale.US).contains("asec")) {
				if (line.matches(reg)) {
					String[] parts = line.split(" ");
					for (String part : parts) {
						if (part.startsWith("/")) {
							if (!part.toLowerCase(Locale.US).contains("vold") && !part.toLowerCase(Locale.US).contains("/mnt/")) {
								out.add(part);
							}
						}
					}
				}
			}
		}

		return out;
	}

	// ============================================================================================= Etc

	public static int wavDuration(MediaPlayer mplayer, String URL) {
		try {
			mplayer.reset();
			mplayer.setDataSource(URL);
			mplayer.prepare();
			Integer duration = mplayer.getDuration();

			return duration;
		} catch (IOException e) {
			e.printStackTrace();
			return 10000;
		}
	}

}