package com.kimjisub.launchpad.manage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class Unipack {
	public String ErrorDetail = null;
	public boolean CriticalError = false;
	
	public boolean isInfo = false;
	public boolean isSounds = false;
	public boolean isKeySound = false;
	public boolean isKeyLED = false;
	public boolean isAutoPlay = false;
	
	public String title = null;
	public String producerName = null;
	public int buttonX = 0;
	public int buttonY = 0;
	public int chain = 0;
	public boolean squareButton = true;
	
	//==========================================================================================
	
	public ArrayList<Sound>[][][] sound = null;
	
	public static class Sound {
		public String URL = null;
		public int loop = -1;
		
		public int id = -1;
		public int num;
		
		Sound(String URL, int loop) {
			this.URL = URL;
			this.loop = loop;
		}
		
		public Sound() {
		}
	}
	
	//==========================================================================================
	
	public ArrayList<LED>[][][] led = null;
	
	public static class LED {
		public ArrayList<Syntax> syntax;
		public int loop;
		public int num;
		
		LED(ArrayList<Syntax> LED, int loop, int num) {
			this.syntax = LED;
			this.loop = loop;
			this.num = num;
		}
		
		public static class Syntax {
			public static final int ON = 1;
			public static final int OFF = 2;
			public static final int DELAY = 3;
			
			public int func = 0;
			public int x;
			public int y;
			public int color = -1;
			public int velo = 119;
			public int delay = -1;
			
			Syntax(int x, int y, int color, int velo) {
				this.func = ON;
				this.x = x;
				this.y = y;
				this.color = color;
				this.velo = velo;
			}
			
			Syntax(int x, int y) {
				this.func = OFF;
				this.x = x;
				this.y = y;
			}
			
			Syntax(int d) {
				this.func = DELAY;
				this.delay = d;
			}
		}
	}
	
	//==========================================================================================
	
	public ArrayList<AutoPlay> autoPlay = null;
	
	public class AutoPlay {
		public static final int ON = 1;
		public static final int OFF = 2;
		public static final int CHAIN = 3;
		public static final int DELAY = 4;
		
		public int func = 0;
		public int currChain = 0;
		public int num = 0;
		public int x;
		public int y;
		public int c;
		public int d;
		
		AutoPlay(int x, int y, int currChain, int num) {
			this.func = ON;
			
			this.x = x;
			this.y = y;
			this.currChain = currChain;
			this.num = num;
		}
		
		AutoPlay(int x, int y, int currChain) {
			this.func = OFF;
			
			this.x = x;
			this.y = y;
			this.currChain = currChain;
		}
		
		AutoPlay(int c) {
			this.func = CHAIN;
			
			this.c = c;
		}
		
		AutoPlay(int d, int currChain) {
			this.func = DELAY;
			this.d = d;
			
			this.currChain = currChain;
		}
	}
	
	//==========================================================================================
	
	public Unipack(String url, boolean loadDetail) {
		
		
		isInfo = (new File(url + "/info")).isFile();
		isSounds = (new File(url + "/sounds")).isDirectory();
		isKeySound = (new File(url + "/keySound")).isFile();
		isKeyLED = (new File(url + "/keyLED")).isDirectory();
		isAutoPlay = (new File(url + "/autoPlay")).isFile();
		
		
		try {
			if (!isInfo) addErr("info doesn't exist");
			if (!isKeySound) addErr("keySound doesn't exist");
			if (!isInfo && !isKeySound) addErr("It does not seem to be UniPack.");
			
			if (!isInfo || !isKeySound) CriticalError = true;
			else {
				
				if (isInfo) {
					BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(url + "/info")));
					String s;
					while ((s = reader.readLine()) != null) {
						String[] split = s.split("=", 2);
						
						String key = split[0];
						String value = split[1];
						
						
						switch (key) {
							case "title":
								title = value;
								break;
							case "producerName":
								producerName = value;
								break;
							case "buttonX":
								buttonX = Integer.parseInt(value);
								break;
							case "buttonY":
								buttonY = Integer.parseInt(value);
								break;
							case "chain":
								chain = Integer.parseInt(value);
								break;
							case "squareButton":
								squareButton = value.equals("true");
								break;
						}
						
					}
					
					if (title == null)
						addErr("info : title was missing");
					if (producerName == null)
						addErr("info : producerName was missing");
					if (buttonX == 0)
						addErr("info : buttonX was missing");
					if (buttonY == 0)
						addErr("info : buttonY was missing");
					if (chain == 0)
						addErr("info : chain was missing");
					
				}
				
				
				if (loadDetail) {
					if (isKeySound) {
						sound = new ArrayList[chain][buttonX][buttonY];
						BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(url + "/keySound")));
						String s;
						while ((s = reader.readLine()) != null) {
							String[] split = s.split(" ");
							
							int c;
							int x;
							int y;
							String URL;
							int loop = 0;
							
							try {
								if (split.length <= 2)
									continue;
								
								c = Integer.parseInt(split[0]) - 1;
								x = Integer.parseInt(split[1]) - 1;
								y = Integer.parseInt(split[2]) - 1;
								URL = split[3];
								
								if (split.length >= 5)
									loop = Integer.parseInt(split[4]) - 1;
								
							} catch (NumberFormatException | IndexOutOfBoundsException e) {
								addErr("keySound : [" + s + "]" + " format is incorrect");
								continue;
							}
							
							
							if (c < 0 || c >= chain)
								addErr("keySound : [" + s + "]" + " chain is incorrect");
							else if (x < 0 || x >= buttonX)
								addErr("keySound : [" + s + "]" + " x is incorrect");
							else if (y < 0 || y >= buttonY)
								addErr("keySound : [" + s + "]" + " y is incorrect");
							else {
								
								Sound tmp = new Sound(url + "/sounds/" + URL, loop);
								
								if (!new File(tmp.URL).isFile()) {
									addErr("keySound : [" + s + "]" + " sound was not found");
									continue;
								}
								
								if (sound[c][x][y] == null)
									sound[c][x][y] = new ArrayList();
								tmp.num = sound[c][x][y].size();
								sound[c][x][y].add(tmp);
								
							}
						}
					}
					
					
					if (isKeyLED) {
						led = new ArrayList[chain][buttonX][buttonY];
						File[] fileList = FileManager.sortByName(new File(url + "/keyLED").listFiles());
						for (File file : fileList) {
							if (file.isFile()) {
								String fileName = file.getName();
								String[] split1 = fileName.split(" ");
								
								int c;
								int x;
								int y;
								int loop;
								
								try {
									if (split1.length <= 2)
										continue;
									
									c = Integer.parseInt(split1[0]) - 1;
									x = Integer.parseInt(split1[1]) - 1;
									y = Integer.parseInt(split1[2]) - 1;
									loop = Integer.parseInt(split1[3]);
									
									if (c < 0 || c >= chain) {
										addErr("keyLED : [" + fileName + "]" + " chain is incorrect");
										continue;
									} else if (x < 0 || x >= buttonX) {
										addErr("keyLED : [" + fileName + "]" + " x is incorrect");
										continue;
									} else if (y < 0 || y >= buttonY) {
										addErr("keyLED : [" + fileName + "]" + " y is incorrect");
										continue;
									} else if (loop < 0) {
										addErr("keyLED : [" + fileName + "]" + " loop is incorrect");
										continue;
									}
									
									
								} catch (NumberFormatException | IndexOutOfBoundsException e) {
									addErr("keyLED : [" + fileName + "]" + " format is incorrect");
									continue;
								}
								
								ArrayList<LED.Syntax> LED목록 = new ArrayList<>();
								
								BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
								String s;
								while ((s = reader.readLine()) != null) {
									String[] split2 = s.split(" ");
									
									String option;
									int _x = -1;
									int _y = -1;
									int _color = -1;
									int _velo = 119;
									int _delay = -1;
									
									
									try {
										if (split2[0].equals(""))
											continue;
										
										option = split2[0];
										
										switch (option) {
											case "on":
											case "o":
												_x = Integer.parseInt(split2[1]) - 1;
												_y = Integer.parseInt(split2[2]) - 1;
												
												if (split2.length == 4)
													_color = Integer.parseInt(split2[3], 16) + 0xFF000000;
												else if (split2.length == 5) {
													if (split2[3].equals("auto") || split2[3].equals("a")) {
														_velo = Integer.parseInt(split2[4]);
														_color = LaunchpadColor.ARGB[_velo] + 0xFF000000;
													} else {
														_velo = Integer.parseInt(split2[4]);
														_color = Integer.parseInt(split2[3], 16) + 0xFF000000;
													}
												} else {
													addErr("keyLED : [" + fileName + "].[" + s + "]" + " format is incorrect");
													continue;
												}
												break;
											case "off":
											case "f":
												_x = Integer.parseInt(split2[1]) - 1;
												_y = Integer.parseInt(split2[2]) - 1;
												break;
											case "delay":
											case "d":
												_delay = Integer.parseInt(split2[1]);
												break;
											default:
												addErr("keyLED : [" + fileName + "].[" + s + "]" + " format is incorrect");
												continue;
										}
										
									} catch (NumberFormatException | IndexOutOfBoundsException e) {
										addErr("keyLED : [" + fileName + "].[" + s + "]" + " format is incorrect");
										continue;
									}
									
									
									switch (option) {
										case "on":
										case "o":
											LED목록.add(new LED.Syntax(_x, _y, _color, _velo));
											break;
										case "off":
										case "f":
											LED목록.add(new LED.Syntax(_x, _y));
											break;
										case "delay":
										case "d":
											LED목록.add(new LED.Syntax(_delay));
											break;
									}
								}
								if (led[c][x][y] == null)
									led[c][x][y] = new ArrayList<>();
								led[c][x][y].add(new LED(LED목록, loop, led[c][x][y].size()));
							} else
								addErr("keyLED : " + file.getName() + " is not file");
						}
					}
					
					if (isAutoPlay) {
						autoPlay = new ArrayList<>();
						int[][] map = new int[buttonX][buttonY];
						
						int currChain = 0;
						
						BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(url + "/autoPlay")));
						String s;
						while ((s = reader.readLine()) != null) {
							String[] split = s.split(" ");
							
							String option;
							int x = -1;
							int y = -1;
							int chain = -1;
							int delay = -1;
							
							try {
								if (split[0].equals(""))
									continue;
								
								option = split[0];
								
								switch (option) {
									case "on":
									case "o":
										x = Integer.parseInt(split[1]) - 1;
										y = Integer.parseInt(split[2]) - 1;
										if (x < 0 || x >= buttonX) {
											addErr("autoPlay : [" + s + "]" + " x is incorrect");
											continue;
										}
										if (y < 0 || y >= buttonY) {
											addErr("autoPlay : [" + s + "]" + " y is incorrect");
											continue;
										}
										break;
									case "off":
									case "f":
										x = Integer.parseInt(split[1]) - 1;
										y = Integer.parseInt(split[2]) - 1;
										if (x < 0 || x >= buttonX) {
											addErr("autoPlay : [" + s + "]" + " x is incorrect");
											continue;
										} else if (y < 0 || y >= buttonY) {
											addErr("autoPlay : [" + s + "]" + " y is incorrect");
											continue;
										}
										break;
									case "chain":
									case "c":
										chain = Integer.parseInt(split[1]) - 1;
										if (chain < 0 || chain >= this.chain) {
											addErr("autoPlay : [" + s + "]" + " chain is incorrect");
											continue;
										}
										break;
									case "delay":
									case "d":
										delay = Integer.parseInt(split[1]);
										break;
									default:
										addErr("autoPlay : [" + s + "]" + " format is incorrect");
										continue;
								}
								
							} catch (NumberFormatException | IndexOutOfBoundsException e) {
								addErr("autoPlay : [" + s + "]" + " format is incorrect");
								continue;
							}
							
							switch (option) {
								case "on":
								case "o":
									autoPlay.add(new AutoPlay(x, y, currChain, map[x][y]));
									map[x][y]++;
									break;
								case "off":
								case "f":
									autoPlay.add(new AutoPlay(x, y, currChain));
									break;
								case "chain":
								case "c":
									autoPlay.add(new AutoPlay(currChain = chain));
									for (int i = 0; i < buttonX; i++)
										for (int j = 0; j < buttonY; j++)
											map[i][j] = 0;
									break;
								case "delay":
								case "d":
									autoPlay.add(new AutoPlay(delay, currChain));
									break;
							}
						}
					}
				}
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void addErr(String content) {
		if (ErrorDetail == null)
			ErrorDetail = content;
		else
			ErrorDetail += "\n" + content;
	}
}
