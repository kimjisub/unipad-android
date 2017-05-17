package com.kimjisub.launchpad;


import net.sf.jazzlib.ZipEntry;
import net.sf.jazzlib.ZipInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;


/**
 * Created by rlawl on 2016-02-05.
 * ReCreated by rlawl on 2016-04-23.
 */


public class 파일 {
	static void unZipFile(String zipFile, String location) throws IOException {
		unZipFile(new FileInputStream(zipFile), location);
	}
	
	static void unZipFile(InputStream zipFile, String location) throws IOException {
		int size;
		byte[] buffer = new byte[1024];
		
		try {
			if (!location.endsWith("/")) {
				location += "/";
			}
			File f = new File(location);
			if (!f.isDirectory()) {
				f.mkdirs();
			}
			ZipInputStream zin = new ZipInputStream(new BufferedInputStream(zipFile, 1024));
			try {
				ZipEntry ze = null;
				while ((ze = zin.getNextEntry()) != null) {
					String path = location + ze.getName();
					File unzipFile = new File(path);
					
					if (ze.isDirectory()) {
						if (!unzipFile.isDirectory()) {
							unzipFile.mkdirs();
						}
					} else {
						// check for and create parent directories if they don't exist
						File parentDir = unzipFile.getParentFile();
						if (null != parentDir) {
							if (!parentDir.isDirectory()) {
								parentDir.mkdirs();
							}
						}
						
						// unzip the file
						FileOutputStream out = new FileOutputStream(unzipFile, false);
						BufferedOutputStream fout = new BufferedOutputStream(out, 1024);
						try {
							while ((size = zin.read(buffer, 0, 1024)) != -1) {
								fout.write(buffer, 0, size);
							}
							
							zin.closeEntry();
						} finally {
							fout.flush();
							fout.close();
						}
					}
				}
			} finally {
				zin.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static String 랜덤문자(int length) {
		StringBuffer buffer = new StringBuffer();
		Random random = new Random();
		
		String chars[] = "a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z".split(",");
		
		for (int i = 0; i < length; i++) {
			buffer.append(chars[random.nextInt(chars.length)]);
		}
		return buffer.toString();
	}
	
	
	static void 파일삭제(String path) {
		File file = new File(path);
		file.delete();
	}
	
	static void 폴더삭제(String path) {
		try {
			File file = new File(path);
			File[] childFileList = file.listFiles();
			for (File childFile : childFileList) {
				if (childFile.isDirectory()) {
					폴더삭제(childFile.getAbsolutePath());
				} else {
					childFile.delete();
				}
			}
			file.delete();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static public long 폴더크기(String a_path) {
		long totalMemory = 0;
		File file = new File(a_path);
		File[] childFileList = file.listFiles();
		
		if (childFileList == null) {
			return 0;
		}
		
		for (File childFile : childFileList) {
			if (childFile.isDirectory()) {
				totalMemory += 폴더크기(childFile.getAbsolutePath());
			} else {
				totalMemory += childFile.length();
			}
		}
		return totalMemory;
	}
	
	
	static File[] 시간별정렬(File[] 파일들) {
		
		int 개수 = 파일들.length;
		
		for (int i = 0; i < 개수 - 1; i++) {
			for (int j = 0; j < 개수 - (i + 1); j++) {
				if (파일들[j].lastModified() < 파일들[j + 1].lastModified()) {
					File tmp = 파일들[j + 1];
					파일들[j + 1] = 파일들[j];
					파일들[j] = tmp;
				}
			}
		}
		
		return 파일들;
	}
	
	static File[] 이름별정렬(File[] files) {
		
		Arrays.sort(files, new Comparator<Object>() {
			@Override
			public int compare(Object object1, Object object2) {
				return ((File) object1).getName().compareTo(((File) object2).getName());
			}
		});
		
		return files;
	}
	
	static boolean isSDCardAvalable() {
		String SDCard = 파일.SDCard.getExternalSDCardPath();
		
		if ((SDCard == null) || (SDCard.length() == 0))
			return false;
		return true;
	}
	
	public static class SDCard {
		public static String getExternalSDCardPath() {
			HashSet<String> hs = getExternalMounts();
			for (String extSDCardPath : hs) {
				return extSDCardPath;
			}
			return null;
		}
		
		public static HashSet<String> getExternalMounts() {
			final HashSet<String> out = new HashSet<String>();
			//String reg = "(?i).*vold.*(vfat|ntfs|exfat|fat32|ext3|ext4).*rw.*";
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

// parse output
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
	}
	
	
	//출처: http://indienote.tistory.com/31 [인디노트]

	/*//버퍼 사이즈를 정한다. 한번에 1024byte를 읽어온다.
	private static final byte[] buf = new byte[1024];


	public static void createZipFile(String targetPath, String zipPath) throws Exception {
		createZipFile(targetPath, zipPath, false);
	}


	public static void createZipFile(String targetPath, String zipPath, boolean isDirCre) throws Exception {

		File fTargetPath = new File(targetPath);
		File[] files = null;

		//targetPath가 디렉토리일경우...
		if (fTargetPath.isDirectory()) {
			files = fTargetPath.listFiles();
		}
		//targetPath가 파일일경우
		else {
			files = new File[1];
			files[0] = fTargetPath;
		}


		File path = new File(zipPath);
		File dir = null;
		dir = new File(path.getParent());
		if (isDirCre) {
			//디렉토리가 없을경우 생성
			dir.mkdirs();
		}


		//ZIP파일의 output Stream
		ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(path));

		//zip파일 압축
		makeZipFile(files, zipOut, "");

		//stream을 닫음으로서 zip파일 생성
		zipOut.close();


	}


	public static void createZipFile(String[] targetFiles, String zipPath) throws Exception {
		createZipFile(targetFiles, zipPath, false);
	}


	public static void createZipFile(String[] targetFiles, String zipPath, boolean isDirCre) throws Exception {

		File[] files = new File[targetFiles.length];
		for (int i = 0; i < files.length; i++) {
			files[i] = new File(targetFiles[i]);
		}

		File path = new File(zipPath);
		File dir = null;
		dir = new File(path.getParent());
		if (isDirCre) {
			//디렉토리가 없을경우 생성
			dir.mkdirs();
		}


		//ZIP파일의 output Stream
		ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(path));

		//zip파일 압축
		makeZipFile(files, zipOut, "");

		//stream을 닫음으로서 zip파일 생성
		zipOut.close();


	}


	//zip파일로 압축
	private static void makeZipFile(File[] files, ZipOutputStream zipOut, String targetDir) throws Exception {

		//디렉토리 내의 파일들을 읽어서 zip Entry로 추가합니다.
		for (int i = 0; i < files.length; i++) {

			File compPath = new File(files[i].getPath());

			//목록에서 디렉토리가 존재할경우 재귀호출하여 하위디렉토리까지 압축한다.(머리아픔... 생각많이함.. ㅠ.ㅠ)
			if (compPath.isDirectory()) {
				File[] subFiles = compPath.listFiles();
				makeZipFile(subFiles, zipOut, targetDir + compPath.getName() + "/");
				continue;
			}

			FileInputStream in = new FileInputStream(compPath);

			// ZIP OutputStream에 ZIP entry를 추가
			// ZIP파일 내의 압축될 파일이 저장되는 경로이다...(주의!!!)
			zipOut.putNextEntry(new ZipEntry(targetDir + "/" + files[i].getName()));

			// 파일을 Zip에 쓴다.
			int data;

			while ((data = in.read(buf)) > 0) {
				zipOut.write(buf, 0, data);
			}

			//하나의 파일을 압축하였다.
			zipOut.closeEntry();
			in.close();

		}

	}



	public static void unZipFile(String targetZip, String completeDir) throws Exception {
		unZipFile(targetZip, completeDir, false);
	}
	public static void unZipFile(String targetZip, String completeDir, boolean isDirCre) throws Exception {

		ZipInputStream in = null;

		try {

			File fCompleteDir = null;
			fCompleteDir = new File(completeDir);
			if (isDirCre) {
				//디렉토리가 없을경우 생성
				fCompleteDir.mkdirs();
			}

			//zip파일의 input stream을 읽어들인다.
			in = new ZipInputStream(new FileInputStream(targetZip));
			ZipEntry entry = null;

			//input stream내의 압축된 파일들을 하나씩 읽어온다.
			while ((entry = in.getNextEntry()) != null) {


				//zip파일의 구조와 동일하게 가기위해 로컬의 디렉토리구조를 만든다.(entry.isDirectory() 안먹음.. 젠장~!)
				String entryName = entry.getName();
				if (entry.getName().lastIndexOf('/') > 0) {
					String mkDirNm = entryName.substring(0, entryName.lastIndexOf('/'));
					new File(completeDir + mkDirNm).mkdirs();
				}

				//해제할 각각 파일의 output stream을 생성
				FileOutputStream out = new FileOutputStream(completeDir + entry.getName());

				int bytes_read;
				while ((bytes_read = in.read(buf)) != -1)
					out.write(buf, 0, bytes_read);

				//하나의 파일이 압축해제되었다.
				out.close();
			}

		} catch (Exception e) {
			throw new Exception(e);
		}
		in.close();

	}


	public static byte[] compressToZip(byte[] src) throws Exception {

		byte[] retSrc = null;
		ByteArrayOutputStream baos = null;

		try {

			//ZIP파일의 output Stream
			ByteArrayInputStream bais = new ByteArrayInputStream(src);
			baos = new ByteArrayOutputStream();
			ZipOutputStream zos = new ZipOutputStream(baos);

			zos.putNextEntry(new ZipEntry("temp.tmp"));

			int bytes_read = 0;
			//전달받은 src를 압축하여 파일에다 쓴다.
			while ((bytes_read = bais.read(buf)) != -1) {
				zos.write(buf, 0, bytes_read);
			}
			bais.close();
			zos.close();

			//스트림을 닫은후 byte배열을 얻어온다...(닫기전에 수행하면 정상적으로 얻어오지 못한다.)
			retSrc = baos.toByteArray();

		} catch (Exception e) {
			throw new Exception(e);
		} finally {
			baos.close();
		}

		return retSrc;

	}


	//압축된 byte배열을 받아서 zipPath위치에 zip파일을 생성한다.
	private static void makeZipFile(byte[] src, String zipPath) throws Exception {

		FileOutputStream fos = null;
		ByteArrayInputStream bais = null;

		try {
			fos = new FileOutputStream(zipPath);
			bais = new ByteArrayInputStream(src);

			int bytes_read = 0;
			while ((bytes_read = bais.read(buf)) != -1) {
				fos.write(buf, 0, bytes_read);
			}

		} catch (Exception e) {
			throw new Exception(e);
		} finally {
			fos.close();
			bais.close();
		}
	}


	public static byte[] unZip(byte[] src) throws Exception {

		byte[] retSrc = null;
		ByteArrayOutputStream baos = null;
		ZipInputStream zis = null;
		int bytes_read = 0;

		try {
			zis = new ZipInputStream(new ByteArrayInputStream(src));
			baos = new ByteArrayOutputStream();

			zis.getNextEntry();        //entry는 하나밖에 없음을 보장한다.

			while ((bytes_read = zis.read(buf)) != -1) {
				baos.write(buf, 0, bytes_read);
			}

			retSrc = baos.toByteArray();

		} catch (Exception e) {
			throw new Exception(e);
		} finally {
			baos.close();
			zis.close();
		}

		return retSrc;
	}


	public static byte[] compressToZip(String src) throws Exception {
		return compressToZip(src.getBytes("UTF-8"));
	}


	public static void srcToZipFile(byte[] src, String zipPath) throws Exception {
		byte[] retSrc = null;

		//압축한다.
		retSrc = compressToZip(src);

		//파일로 만든다.
		makeZipFile(retSrc, zipPath);
	}


	public static void srcToZipFile(String src, String zipPath) throws Exception {
		byte[] retSrc = null;

		//압축한다.
		retSrc = compressToZip(src.getBytes("UTF-8"));

		//파일로 만든다.
		makeZipFile(retSrc, zipPath);
	}


	public static byte[] zipFileToSrc(String zipPath) throws Exception {
		byte[] retSrc = null;

		return retSrc;
	}*/
}