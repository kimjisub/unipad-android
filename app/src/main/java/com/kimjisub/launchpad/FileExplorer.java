package com.kimjisub.launchpad;

import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.kimjisub.launchpad.manage.FileManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileExplorer {
	
	Context context;
	
	List<String> mItem;
	List<String> mPath;
	
	LinearLayout LL_explorer;
	TextView TV_path;
	ListView LV_list;
	
	String URL;
	
	
	public FileExplorer(Context context) {
		this.context = context;
	}
	
	public FileExplorer(Context context, String URL) {
		this.context = context;
		this.URL = URL;
	}
	
	public void show() {
		LL_explorer = (LinearLayout) View.inflate(context, R.layout.file_explorer, null);
		TV_path = LL_explorer.findViewById(R.id.path);
		LV_list = LL_explorer.findViewById(R.id.list);
		
		final AlertDialog dialog = (new AlertDialog.Builder(context)).create();
		
		
		LV_list.setOnItemClickListener((parent, view, position, id) -> {
			final File file = new File(mPath.get(position));
			if (file.isDirectory()) {
				if (file.canRead())
					getDir(mPath.get(position));
				else
					showDialog(file.getName(), lang(R.string.cantReadFolder));
			} else {
				if (file.canRead())
					onFileSelected(file.getPath());
				else
					showDialog(file.getName(), lang(R.string.cantReadFile));
				
				
			}
		});
		getDir(URL);
		
		
		dialog.setView(LL_explorer);
		dialog.show();
	}
	
	void getDir(String dirPath) {
		onURLChanged(dirPath);
		
		TV_path.setText(dirPath);
		
		mItem = new ArrayList<>();
		mPath = new ArrayList<>();
		File f = new File(dirPath);
		File[] files = FileManager.sortByName(f.listFiles());
		if (!dirPath.equals("/")) {
			mItem.add("../");
			mPath.add(f.getParent());
		}
		for (File file : files) {
			String name = file.getName();
			if (name.indexOf('.') != 0) {
				if (file.isDirectory()) {
					mPath.add(file.getPath());
					mItem.add(name + "/");
				} else if (name.lastIndexOf(".zip") == name.length() - 4 || name.lastIndexOf(".uni") == name.length() - 4) {
					mPath.add(file.getPath());
					mItem.add(file.getName());
				}
			}
		}
		ArrayAdapter<String> fileList = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, mItem);
		LV_list.setAdapter(fileList);
	}
	
	// BaseActivity.java
	
	public void showDialog(String title, String content) {
		new AlertDialog.Builder(context)
			.setTitle(title)
			.setMessage(content)
			.setPositiveButton(lang(R.string.accept), null)
			.show();
	}
	
	
	public String lang(int id) {
		return context.getResources().getString(id);
	}
	
	// =========================================================================================
	
	private OnEventListener onEventListener = null;
	
	public interface OnEventListener {
		
		void onFileSelected(String fileURL);
		
		void onURLChanged(String folderURL);
	}
	
	public FileExplorer setOnEventListener(OnEventListener listener) {
		this.onEventListener = listener;
		return this;
	}
	
	public void onFileSelected(String fileURL) {
		if (onEventListener != null) onEventListener.onFileSelected(fileURL);
	}
	
	public void onURLChanged(String folderURL) {
		if (onEventListener != null) onEventListener.onURLChanged(folderURL);
	}
}
