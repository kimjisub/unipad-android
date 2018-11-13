package com.kimjisub.launchpad.playManager;

import com.kimjisub.launchpad.manage.LaunchpadColor;

public class ColorManager {
	public static final int COUNT = 5;
	
	public static final int UI = 0;
	public static final int UI_UNIPAD = 1;
	public static final int GUIDE = 2;
	public static final int PRESSED = 3;
	public static final int CHAIN = 3;
	public static final int LED = 4;
	
	Item[][][] btn;
	Item[][] cir;
	
	boolean[] btnIgnoreList;
	boolean[] cirIgnoreList;
	
	public static class Item {
		public int x;
		public int y;
		public int chanel;
		public int color;
		public int code;
		
		Item(int x, int y, int chanel, int color, int code) {
			this.x = x;
			this.y = y;
			this.chanel = chanel;
			this.color = color;
			this.code = code;
		}
	}
	
	public ColorManager(int x, int y) {
		btn = new Item[x][y][COUNT];
		cir = new Item[36][COUNT];
		
		btnIgnoreList = new boolean[COUNT];
		cirIgnoreList = new boolean[COUNT];
	}
	
	public Item get(int x, int y) {
		Item ret = null;
		
		if (x != -1) {
			for (int i = 0; i < COUNT; i++) {
				if (btnIgnoreList[i])
					continue;
				if (btn[x][y][i] != null) {
					ret = btn[x][y][i];
					break;
				}
			}
		} else {
			for (int i = 0; i < COUNT; i++) {
				if (cirIgnoreList[i])
					continue;
				
				if (cir[y][i] != null) {
					ret = cir[y][i];
					break;
				}
			}
		}
		
		
		return ret;
	}
	
	public void add(int x, int y, int chanel, int color, int code) {
		if (color == -1)
			color = 0xFF000000 + LaunchpadColor.ARGB[code];
		if (x != -1)
			btn[x][y][chanel] = new Item(x, y, chanel, color, code);
		else
			cir[y][chanel] = new Item(x, y, chanel, color, code);
	}
	
	public void remove(int x, int y, int chanel) {
		if (x != -1)
			btn[x][y][chanel] = null;
		else
			cir[y][chanel] = null;
	}
	
	public void setBtnIgnore(int chanel, boolean ignore) {
		btnIgnoreList[chanel] = ignore;
	}
	
	public void setCirIgnore(int chanel, boolean ignore) {
		cirIgnoreList[chanel] = ignore;
	}
	
}