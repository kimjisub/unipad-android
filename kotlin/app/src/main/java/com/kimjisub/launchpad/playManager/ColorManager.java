package com.kimjisub.launchpad.playManager;

import java.util.ArrayList;

public class ColorManager {
	public static final int GUIDE = 0;
	public static final int PRESSED = 1;
	public static final int LED = 2;
	
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
		btn = new Item[x][y][3];
		cir = new Item[36][3];
		
		btnIgnoreList = new boolean[]{false, false, false};
		cirIgnoreList = new boolean[]{false, false, false};
	}
	
	public Item get(int x, int y) {
		Item ret = null;
		
		if (x != -1) {
			for (int i = 0; i < 3; i++) {
				if (btnIgnoreList[i])
					continue;
				if (btn[x][y][i] != null) {
					ret = btn[x][y][i];
					break;
				}
			}
		} else {
			for (int i = 0; i < 3; i++) {
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