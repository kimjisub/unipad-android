package com.kimjisub.launchpad.manage;

import android.content.Context;

import com.kimjisub.launchpad.BaseActivity;
import com.kimjisub.launchpad.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class Unipack {
	public String URL;

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
	public String website = null;

	// =============================================================================================

	public ArrayList<Sound>[][][] sound = null;
	public ArrayList<LED>[][][] led = null;
	public ArrayList<AutoPlay> autoPlay = null;

	public Unipack(String url, boolean loadDetail) {

		this.URL = url;

		try {
			isInfo = (new File(URL + "/info")).isFile();
			isSounds = (new File(URL + "/sounds")).isDirectory();
			isKeySound = (new File(URL + "/keySound")).isFile();
			isKeyLED = (new File(URL + "/keyLED")).isDirectory();
			isAutoPlay = (new File(URL + "/autoPlay")).isFile();

			if (!isInfo) addErr("info doesn't exist");
			if (!isKeySound) addErr("keySound doesn't exist");
			if (!isInfo && !isKeySound) addErr("It does not seem to be UniPack.");

			if (!isInfo || !isKeySound) CriticalError = true;
			else {

				if (isInfo) {
					BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(URL + "/info")));
					String s;
					while ((s = reader.readLine()) != null) {

						if (s.length() == 0)
							continue;

						try {
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
								case "website":
									website = value;
									break;
							}
						} catch (ArrayIndexOutOfBoundsException e) {
							e.printStackTrace();
							addErr("info : [" + s + "] format is not found");
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
					if (!(1 <= chain && chain <= 24)) {
						addErr("info : chain out of range");
						CriticalError = true;
					}

					reader.close();
				}


				if (loadDetail) {
					if (isKeySound) {
						sound = new ArrayList[chain][buttonX][buttonY];
						BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(URL + "/keySound")));
						String s;
						while ((s = reader.readLine()) != null) {
							String[] split = s.split(" ");

							int c;
							int x;
							int y;
							String soundURL;
							int loop = 0;
							int wormhole = -1;

							try {
								if (split.length <= 2)
									continue;

								c = Integer.parseInt(split[0]) - 1;
								x = Integer.parseInt(split[1]) - 1;
								y = Integer.parseInt(split[2]) - 1;
								soundURL = split[3];

								if (split.length >= 5)
									loop = Integer.parseInt(split[4]) - 1;
								if (split.length >= 6) {
									loop = Integer.parseInt(split[4]) - 1;
									wormhole = Integer.parseInt(split[5]) - 1;
								}

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

								Sound tmp = new Sound(URL + "/sounds/" + soundURL, loop, wormhole);

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
						reader.close();
					}


					if (isKeyLED) {
						led = new ArrayList[chain][buttonX][buttonY];
						File[] fileList = FileManager.sortByName(new File(URL + "/keyLED").listFiles());
						for (File file : fileList) {
							if (file.isFile()) {
								String fileName = file.getName();
								String[] split1 = fileName.split(" ");

								int c;
								int x;
								int y;
								int loop = 1;

								try {
									if (split1.length <= 2)
										continue;

									c = Integer.parseInt(split1[0]) - 1;
									x = Integer.parseInt(split1[1]) - 1;
									y = Integer.parseInt(split1[2]) - 1;
									if (split1.length >= 4)
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

								ArrayList<LED.Syntax> LEDs = new ArrayList<>();

								BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
								String s;
								while ((s = reader.readLine()) != null) {
									String[] split2 = s.split(" ");

									String option;
									int _x = -1;
									int _y = -1;
									int _color = -1;
									int _velo = 4;
									int _delay = -1;


									try {
										if (split2[0].equals(""))
											continue;

										option = split2[0];

										switch (option) {
											case "on":
											case "o":
												try {
													_x = Integer.parseInt(split2[1]) - 1;
												} catch (NumberFormatException ignore) {
												}
												_y = Integer.parseInt(split2[2]) - 1;

												if (split2.length == 4)
													_color = Integer.parseInt(split2[3], 16) + 0xFF000000;
												else if (split2.length == 5) {
													if (split2[3].equals("auto") || split2[3].equals("a")) {
														_velo = Integer.parseInt(split2[4]);
														_color = LaunchpadColor.ARGB[_velo];
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
												try {
													_x = Integer.parseInt(split2[1]) - 1;
												} catch (NumberFormatException ignore) {
												}
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
											LEDs.add(new LED.Syntax(_x, _y, _color, _velo));
											break;
										case "off":
										case "f":
											LEDs.add(new LED.Syntax(_x, _y));
											break;
										case "delay":
										case "d":
											LEDs.add(new LED.Syntax(_delay));
											break;
									}
								}
								if (led[c][x][y] == null)
									led[c][x][y] = new ArrayList<>();
								led[c][x][y].add(new LED(LEDs, loop, led[c][x][y].size()));
								reader.close();
							} else
								addErr("keyLED : " + file.getName() + " is not file");
						}
					}

					if (isAutoPlay) {
						autoPlay = new ArrayList<>();
						int[][] map = new int[buttonX][buttonY];

						int currChain = 0;

						BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(getMainAutoplay())));
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
									case "touch":
									case "t":
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
									Sound sound = Sound_get(currChain, x, y, map[x][y]);
									map[x][y]++;
									if (sound.wormhole != -1) {
										autoPlay.add(new AutoPlay(currChain = sound.wormhole));
										for (int i = 0; i < buttonX; i++)
											for (int j = 0; j < buttonY; j++)
												map[i][j] = 0;
									}
									break;
								case "off":
								case "f":
									autoPlay.add(new AutoPlay(x, y, currChain));
									break;
								case "touch":
								case "t":
									autoPlay.add(new AutoPlay(x, y, currChain, map[x][y]));
									autoPlay.add(new AutoPlay(x, y, currChain));
									map[x][y]++;
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
						reader.close();
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void Sound_push(int c, int x, int y) {
		//log("Sound_push (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			Sound tmp = sound[c][x][y].get(0);
			sound[c][x][y].remove(0);
			sound[c][x][y].add(tmp);
		} catch (NullPointerException ignored) {
		} catch (IndexOutOfBoundsException ee) {
			Log.err("Sound_push (" + c + ", " + x + ", " + y + ")");
			ee.printStackTrace();
		}
	}

	public void Sound_push(int c, int x, int y, int num) {
		//log("Sound_push (" + c + ", " + buttonX + ", " + buttonY + ", " + num + ")");
		try {
			ArrayList<Sound> e = sound[c][x][y];
			if (sound[c][x][y].get(0).num != num)
				while (true) {
					Sound tmp = e.get(0);
					e.remove(0);
					e.add(tmp);
					if (e.get(0).num == num % e.size())
						break;
				}
		} catch (NullPointerException ignored) {
		} catch (IndexOutOfBoundsException ee) {
			Log.err("Sound_push (" + c + ", " + x + ", " + y + ", " + num + ")");
			ee.printStackTrace();
		} catch (ArithmeticException ee) {
			Log.err("ArithmeticException : Sound_push (" + c + ", " + x + ", " + y + ", " + num + ")");
			ee.printStackTrace();
		}
	}

	// =============================================================================================

	public Sound Sound_get(int c, int x, int y) {
		//log("Sound_get (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			return sound[c][x][y].get(0);
		} catch (NullPointerException ignored) {
			return new Sound();
		} catch (IndexOutOfBoundsException ee) {
			Log.err("Sound_get (" + c + ", " + x + ", " + y + ")");
			ee.printStackTrace();
			return new Sound();
		}
	}

	public Sound Sound_get(int c, int x, int y, int num) {
		//log("Sound_get (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			ArrayList<Sound> e = sound[c][x][y];
			return sound[c][x][y].get(num % e.size());
		} catch (NullPointerException ignored) {
			return new Sound();
		} catch (IndexOutOfBoundsException ee) {
			Log.err("Sound_get (" + c + ", " + x + ", " + y + ")");
			ee.printStackTrace();
			return new Sound();
		}
	}

	public void LED_push(int c, int x, int y) {
		//log("LED_push (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			LED e = led[c][x][y].get(0);
			led[c][x][y].remove(0);
			led[c][x][y].add(e);
		} catch (NullPointerException ignored) {
		} catch (IndexOutOfBoundsException ee) {
			Log.err("LED_push (" + c + ", " + x + ", " + y + ")");
			ee.printStackTrace();
		}
	}

	public void LED_push(int c, int x, int y, int num) {
		//log("LED_push (" + c + ", " + buttonX + ", " + buttonY + ", " + num + ")");
		try {
			ArrayList<LED> e = led[c][x][y];
			if (e.get(0).num != num)
				while (true) {
					LED tmp = e.get(0);
					e.remove(0);
					e.add(tmp);
					if (e.get(0).num == num % e.size())
						break;
				}
		} catch (NullPointerException ignored) {
		} catch (IndexOutOfBoundsException ee) {
			Log.err("LED_push (" + c + ", " + x + ", " + y + ", " + num + ")");
			ee.printStackTrace();
		}
	}

	public LED LED_get(int c, int x, int y) {
		//log("LED_get (" + c + ", " + buttonX + ", " + buttonY + ")");
		try {
			return led[c][x][y].get(0);
		} catch (NullPointerException ignored) {
			return null;
		} catch (IndexOutOfBoundsException ee) {
			Log.err("LED_get (" + c + ", " + x + ", " + y + ")");
			ee.printStackTrace();
			return null;
		}
	}

	// =============================================================================================

	String getMainAutoplay() {
		File[] fileList = FileManager.sortByName(new File(URL).listFiles());
		for (File f : fileList) {
			if (f.isFile() && f.getName().toLowerCase().startsWith("autoplay"))
				return f.getPath();
		}
		return null;
	}

	public String[] getAutoplays() {
		File[] fileList = FileManager.sortByName(new File(URL).listFiles());
		ArrayList autoPlays = new ArrayList();
		for (File f : fileList) {
			if (f.isFile() && (f.getName().toLowerCase().startsWith("autoplay") || f.getName().toLowerCase().startsWith("_autoplay")))
				autoPlays.add(f.getPath());
		}

		return (String[]) autoPlays.toArray(new String[]{""});
	}

	// =============================================================================================

	private void addErr(String content) {
		if (ErrorDetail == null)
			ErrorDetail = content;
		else
			ErrorDetail += "\n" + content;
	}

	public String getInfoText(Context context) {
		return BaseActivity.lang(context, R.string.title) + " : " + this.title + "\n" +
				BaseActivity.lang(context, R.string.producerName) + " : " + this.producerName + "\n" +
				BaseActivity.lang(context, R.string.scale) + " : " + this.buttonX + " x " + this.buttonY + "\n" +
				BaseActivity.lang(context, R.string.chainCount) + " : " + this.chain + "\n" +
				BaseActivity.lang(context, R.string.fileSize) + " : " + FileManager.byteToMB(FileManager.getFolderSize(URL)) + " MB";
	}

	public static class Sound {
		public String URL = null;
		public int loop = -1;
		public int wormhole = -1;

		public int num;
		public int id = -1;


		Sound(String URL, int loop) {
			this.URL = URL;
			this.loop = loop;
		}

		Sound(String URL, int loop, int wormhole) {
			this.URL = URL;
			this.loop = loop;
			this.wormhole = wormhole;
		}

		public Sound() {
		}
	}

	public static class LED {
		public ArrayList<Syntax> syntaxs;
		public int loop;
		public int num;

		LED(ArrayList<Syntax> LED, int loop, int num) {
			this.syntaxs = LED;
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
			public int velo = 4;
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

	public static class AutoPlay {
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

		public AutoPlay(int d, int currChain) {
			this.func = DELAY;
			this.d = d;

			this.currChain = currChain;
		}
	}
}