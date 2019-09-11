package com.kimjisub.launchpad.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.kimjisub.launchpad.db.ent.UnipackENT;
import com.kimjisub.manager.Log;

import java.util.Date;

@Dao
public abstract class UnipackDAO {

	/*@Query("SELECT * FROM UnipackENT")
	public abstract List<UnipackENT> getAll();*/

	@Query("SELECT * FROM UnipackENT WHERE path=:path LIMIT 1")
	public abstract UnipackENT find(String path);

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	public abstract void insert(UnipackENT item);

	@Update
	public abstract void update(UnipackENT item);

	public UnipackENT getOrCreate(String path) {
		UnipackENT item = find(path);
		Log.test("getOrCreate: " + path);

		if (item == null) {
			item = new UnipackENT(path, 0, false, false, new Date());
			insert(item);
			Log.test("insert");

		}

		return item;
	}
}
