package com.kimjisub.launchpad.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.kimjisub.launchpad.db.ent.UnipackOpenENT;

@Dao
public abstract class UnipackOpenDAO {

	@Insert
	public abstract void insert(UnipackOpenENT item);

	@Query("SELECT COUNT(*) FROM UnipackOpenENT")
	public abstract LiveData<Integer> getCount();

	@Query("SELECT COUNT(*) FROM UnipackOpenENT WHERE path=:path")
	public abstract LiveData<Integer> getCount(String path);

}
