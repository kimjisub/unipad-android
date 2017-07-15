package com.kimjisub.launchpad.manage;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Created by rlawl on 2016-02-02.
 * ReCreated by rlawl on 2016-04-23.
 */

public class Unipack {
	public String 에러내용 = null;
	public boolean 치명적인에러 = false;
	
	public String 제목 = null;
	public String 제작자 = null;
	public int 가로축 = 0;
	public int 세로축 = 0;
	public int 체인 = 0;
	public boolean 정사각형버튼 = true;
	
	public boolean info여부 = false;
	public boolean sounds여부 = false;
	public boolean keySound여부 = false;
	public boolean keyLED여부 = false;
	public boolean autoPlay여부 = false;
	
	//===========================================================================================================================================================================
	
	public ArrayList<소리요소>[][][] 소리 = null;
	
	public static class 소리요소 {
		public String 노래경로 = null;
		public int 아이디 = -1;
		public int 반복 = -1;
		public int 번호;
		
		public 소리요소(String 노래경로, int 반복) {
			this.노래경로 = 노래경로;
			this.반복 = 반복;
		}
		
		public 소리요소() {
		}
	}
	
	//===========================================================================================================================================================================
	
	public ArrayList<LED이벤트>[][][] LED = null;
	
	public static class LED이벤트 {
		public ArrayList<요소> LED;
		public int 반복;
		public int 번호;
		
		public LED이벤트(ArrayList<요소> LED, int 반복, int 번호) {
			this.LED = LED;
			this.반복 = 반복;
			this.번호 = 번호;
		}
		
		public static class 요소 {
			public static final int 켜기 = 1;
			public static final int 끄기 = 2;
			public static final int 딜레이 = 3;
			
			public int 기능 = 0;
			public int x;
			public int y;
			public int color = -1;
			public int velo = 119;
			public int delay = -1;
			
			public 요소(int x, int y, int 색코드, int 벨로시티) {
				this.기능 = 켜기;
				this.x = x;
				this.y = y;
				this.color = 색코드;
				this.velo = 벨로시티;
			}
			
			public 요소(int x, int y) {
				this.기능 = 끄기;
				this.x = x;
				this.y = y;
			}
			
			public 요소(int d) {
				this.기능 = 딜레이;
				this.delay = d;
			}
		}
	}
	
	//===========================================================================================================================================================================
	
	public ArrayList<자동재생요소> 자동재생 = null;
	
	public class 자동재생요소 {
		public static final int 켜기 = 1;
		public static final int 끄기 = 2;
		public static final int 체인 = 3;
		public static final int 딜레이 = 4;
		
		public int 기능 = 0;
		public int 체인기록 = 0;
		public int 번호 = 0;
		public int x;
		public int y;
		public int c;
		public int d;
		
		public 자동재생요소(int x, int y, int 체인기록, int 번호) {
			this.기능 = 켜기;
			
			this.x = x;
			this.y = y;
			this.체인기록 = 체인기록;
			this.번호 = 번호;
		}
		
		public 자동재생요소(int x, int y, int 체인기록) {
			this.기능 = 끄기;
			
			this.x = x;
			this.y = y;
			this.체인기록 = 체인기록;
		}
		
		public 자동재생요소(int 값) {
			this.기능 = 체인;
			
			this.c = 값;
		}
		
		public 자동재생요소(int 값, int 체인기록) {
			this.기능 = 딜레이;
			this.d = 값;
			
			this.체인기록 = 체인기록;
		}
	}
	
	//===========================================================================================================================================================================
	
	public Unipack(String url, boolean 세부로딩) {
		
		
		info여부 = (new java.io.File(url + "/info")).isFile();
		sounds여부 = (new java.io.File(url + "/sounds")).isDirectory();
		keySound여부 = (new java.io.File(url + "/keySound")).isFile();
		keyLED여부 = (new java.io.File(url + "/keyLED")).isDirectory();
		autoPlay여부 = (new java.io.File(url + "/autoPlay")).isFile();
		
		
		String 한줄;
		
		try {
			if (!info여부) 에러누적("info doesn't exist");
			if (!keySound여부) 에러누적("keySound doesn't exist");
			if (!info여부 && !keySound여부)
				에러누적("It does not seem to be UniPack.");
			
			if (!info여부 || !keySound여부)
				치명적인에러 = true;
			else {
				
				if (info여부) {
					BufferedReader info = new BufferedReader(new InputStreamReader(new FileInputStream(url + "/info")));
					while ((한줄 = info.readLine()) != null) {
						String[] 정보 = 한줄.split("=", 2);
						
						switch (정보[0]) {
							case 구조.제목:
								제목 = 정보[1];
								break;
							case 구조.제작자:
								제작자 = 정보[1];
								break;
							case 구조.가로축:
								가로축 = Integer.parseInt(정보[1]);
								break;
							case 구조.세로축:
								세로축 = Integer.parseInt(정보[1]);
								break;
							case 구조.체인:
								체인 = Integer.parseInt(정보[1]);
								break;
							case 구조.정사각형버튼:
								정사각형버튼 = 정보[1].equals("true");
								break;
						}
						
					}
					
					if (제목 == null)
						에러누적("info : title was missing");
					if (제작자 == null)
						에러누적("info : producerName was missing");
					if (가로축 == 0)
						에러누적("info : buttonX was missing");
					if (세로축 == 0)
						에러누적("info : buttonY was missing");
					if (체인 == 0)
						에러누적("info : chain was missing");
					
				}
				
				
				if (세부로딩) {
					if (keySound여부) {
						소리 = new ArrayList[체인][가로축][세로축];
						BufferedReader keySound = new BufferedReader(new InputStreamReader(new FileInputStream(url + "/keySound")));
						while ((한줄 = keySound.readLine()) != null) {
							String[] 정보 = 한줄.split(" ");
							
							int c;
							int x;
							int y;
							String URL;
							int loop = 0;
							
							try {
								if (정보.length <= 2)
									continue;
								
								c = Integer.parseInt(정보[0]) - 1;
								x = Integer.parseInt(정보[1]) - 1;
								y = Integer.parseInt(정보[2]) - 1;
								URL = 정보[3];
								
								if (정보.length >= 5)
									loop = Integer.parseInt(정보[4]) - 1;
								
							} catch (NumberFormatException | IndexOutOfBoundsException e) {
								에러누적("keySound : [" + 한줄 + "]" + " format is incorrect");
								continue;
							}
							
							
							if (c < 0 || c >= 체인)
								에러누적("keySound : [" + 한줄 + "]" + " chain is incorrect");
							else if (x < 0 || x >= 가로축)
								에러누적("keySound : [" + 한줄 + "]" + " x is incorrect");
							else if (y < 0 || y >= 세로축)
								에러누적("keySound : [" + 한줄 + "]" + " y is incorrect");
							else {
								
								소리요소 요소 = new 소리요소(url + "/sounds/" + URL, loop);
								
								if (!new java.io.File(요소.노래경로).isFile()) {
									에러누적("keySound : [" + 한줄 + "]" + " sound was not found");
									continue;
								}
								
								if (소리[c][x][y] == null)
									소리[c][x][y] = new ArrayList<>();
								요소.번호 = 소리[c][x][y].size();
								소리[c][x][y].add(요소);
								
							}
						}
					}
					
					
					if (keyLED여부) {
						LED = new ArrayList[체인][가로축][세로축];
						java.io.File[] 파일리스트 = FileManager.sortByName(new java.io.File(url + "/keyLED").listFiles());
						for (java.io.File 파일 : 파일리스트) {
							if (파일.isFile()) {
								String 이름 = 파일.getName();
								String[] 정보 = 이름.split(" ");
								
								int c;
								int x;
								int y;
								int loop;
								
								try {
									if (정보.length <= 2)
										continue;
									
									c = Integer.parseInt(정보[0]) - 1;
									x = Integer.parseInt(정보[1]) - 1;
									y = Integer.parseInt(정보[2]) - 1;
									loop = Integer.parseInt(정보[3]);
									
									if (c < 0 || c >= 체인) {
										에러누적("keyLED : [" + 이름 + "]" + " chain is incorrect");
										continue;
									} else if (x < 0 || x >= 가로축) {
										에러누적("keyLED : [" + 이름 + "]" + " x is incorrect");
										continue;
									} else if (y < 0 || y >= 세로축) {
										에러누적("keyLED : [" + 이름 + "]" + " y is incorrect");
										continue;
									} else if (loop < 0) {
										에러누적("keyLED : [" + 이름 + "]" + " loop is incorrect");
										continue;
									}
									
									
								} catch (NumberFormatException | IndexOutOfBoundsException e) {
									에러누적("keyLED : [" + 이름 + "]" + " format is incorrect");
									continue;
								}
								
								ArrayList<LED이벤트.요소> LED목록 = new ArrayList<>();
								
								BufferedReader 리더 = new BufferedReader(new InputStreamReader(new FileInputStream(파일)));
								while ((한줄 = 리더.readLine()) != null) {
									String[] 요소 = 한줄.split(" ");
									
									String option;
									int x_ = -1;
									int y_ = -1;
									int color = -1;
									int velo = 119;
									int delay = -1;
									
									
									try {
										if (요소[0].equals(""))
											continue;
										
										option = 요소[0];
										
										if (option.equals("on") || option.equals("o")) {
											x_ = Integer.parseInt(요소[1]) - 1;
											y_ = Integer.parseInt(요소[2]) - 1;
											
											if (요소.length == 4)
												color = Integer.parseInt(요소[3], 16) + 0xFF000000;
											else if (요소.length == 5) {
												if (요소[3].equals("auto") || 요소[3].equals("a")) {
													velo = Integer.parseInt(요소[4]);
													color = LaunchpadColor.ARGB[velo] + 0xFF000000;
												} else {
													velo = Integer.parseInt(요소[4]);
													color = Integer.parseInt(요소[3], 16) + 0xFF000000;
												}
											} else {
												에러누적("keyLED : [" + 이름 + "].[" + 한줄 + "]" + " format is incorrect");
												continue;
											}
										} else if (option.equals("off") || option.equals("f")) {
											x_ = Integer.parseInt(요소[1]) - 1;
											y_ = Integer.parseInt(요소[2]) - 1;
										} else if (option.equals("delay") || option.equals("d")) {
											delay = Integer.parseInt(요소[1]);
										} else {
											에러누적("keyLED : [" + 이름 + "].[" + 한줄 + "]" + " format is incorrect");
											continue;
										}
										
									} catch (NumberFormatException | IndexOutOfBoundsException e) {
										에러누적("keyLED : [" + 이름 + "].[" + 한줄 + "]" + " format is incorrect");
										continue;
									}
									
									
									if (option.equals("on") || option.equals("o")) {
										LED목록.add(new LED이벤트.요소(x_, y_, color, velo));
									} else if (option.equals("off") || option.equals("f")) {
										LED목록.add(new LED이벤트.요소(x_, y_));
									} else if (option.equals("delay") || option.equals("d")) {
										LED목록.add(new LED이벤트.요소(delay));
									}
								}
								if (LED[c][x][y] == null)
									LED[c][x][y] = new ArrayList<>();
								LED[c][x][y].add(new LED이벤트(LED목록, loop, LED[c][x][y].size()));
							} else
								에러누적("keyLED : " + 파일.getName() + " is not file");
						}
					}
					
					if (autoPlay여부) {
						자동재생 = new ArrayList<>();
						int[][] 맵 = new int[가로축][세로축];
						
						int 체인기록 = 0;
						
						BufferedReader 리더 = new BufferedReader(new InputStreamReader(new FileInputStream(url + "/autoPlay")));
						while ((한줄 = 리더.readLine()) != null) {
							String[] 요소 = 한줄.split(" ");
							
							String option;
							int x = -1;
							int y = -1;
							int chain = -1;
							int delay = -1;
							
							try {
								if (요소[0].equals(""))
									continue;
								
								option = 요소[0];
								
								if (option.equals("on") || option.equals("o")) {
									x = Integer.parseInt(요소[1]) - 1;
									y = Integer.parseInt(요소[2]) - 1;
									if (x < 0 || x >= 가로축) {
										에러누적("autoPlay : [" + 한줄 + "]" + " x is incorrect");
										continue;
									}
									if (y < 0 || y >= 세로축) {
										에러누적("autoPlay : [" + 한줄 + "]" + " y is incorrect");
										continue;
									}
								} else if (option.equals("off") || option.equals("f")) {
									x = Integer.parseInt(요소[1]) - 1;
									y = Integer.parseInt(요소[2]) - 1;
									if (x < 0 || x >= 가로축) {
										에러누적("autoPlay : [" + 한줄 + "]" + " x is incorrect");
										continue;
									} else if (y < 0 || y >= 세로축) {
										에러누적("autoPlay : [" + 한줄 + "]" + " y is incorrect");
										continue;
									}
								} else if (option.equals("chain") || option.equals("c")) {
									chain = Integer.parseInt(요소[1]) - 1;
									if (chain < 0 || chain >= 체인) {
										에러누적("autoPlay : [" + 한줄 + "]" + " chain is incorrect");
										continue;
									}
								} else if (option.equals("delay") || option.equals("d")) {
									delay = Integer.parseInt(요소[1]);
								} else {
									에러누적("autoPlay : [" + 한줄 + "]" + " format is incorrect");
									continue;
								}
								
							} catch (NumberFormatException | IndexOutOfBoundsException e) {
								에러누적("autoPlay : [" + 한줄 + "]" + " format is incorrect");
								continue;
							}
							
							if (option.equals("on") || option.equals("o")) {
								자동재생.add(new 자동재생요소(x, y, 체인기록, 맵[x][y]));
								맵[x][y]++;
							} else if (option.equals("off") || option.equals("f")) {
								자동재생.add(new 자동재생요소(x, y, 체인기록));
							} else if (option.equals("chain") || option.equals("c")) {
								자동재생.add(new 자동재생요소(체인기록 = chain));
								for (int i = 0; i < 가로축; i++)
									for (int j = 0; j < 세로축; j++)
										맵[i][j] = 0;
							} else if (option.equals("delay") || option.equals("d")) {
								자동재생.add(new 자동재생요소(delay, 체인기록));
							}
						}
					}
				}
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	class 구조 {
		static final String 제목 = "title";
		static final String 제작자 = "producerName";
		static final String 가로축 = "buttonX";
		static final String 세로축 = "buttonY";
		static final String 체인 = "chain";
		static final String 정사각형버튼 = "squareButton";
		static final String 화면가로세로 = "landscape";
	}
	
	void 에러누적(String 내용) {
		if (에러내용 == null)
			에러내용 = 내용;
		else
			에러내용 += "\n" + 내용;
	}
}
