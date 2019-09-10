package com.kimjisub.launchpad.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.kimjisub.launchpad.db.ent.UnipackENT;

import java.util.Date;
import java.util.List;

@Dao
public abstract class UnipackDAO {

	@Query("SELECT * FROM UnipackENT")
	public abstract List<UnipackENT> getAll();

	@Query("SELECT * FROM UnipackENT WHERE path=:path LIMIT 1")
	public abstract UnipackENT get(String path);

	@Insert
	public abstract void insert(UnipackENT item);

	@Update
	public abstract void update(UnipackENT item);

	public UnipackENT getOrCreate(String path) {
		UnipackENT item = get(path);

		if (item == null) {
			item = new UnipackENT(path, 0, false, false, new Date());
			insert(item);
		}

		return item;
	}
}
