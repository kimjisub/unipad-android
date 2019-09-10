package com.kimjisub.launchpad.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.kimjisub.launchpad.db.dao.UnipackDAO;
import com.kimjisub.launchpad.db.dao.UnipackOpenDAO;
import com.kimjisub.launchpad.db.ent.UnipackENT;
import com.kimjisub.launchpad.db.ent.UnipackOpenENT;

@Database(entities = {UnipackENT.class, UnipackOpenENT.class}, version = 1)
public abstract class AppDataBase extends RoomDatabase {

	public abstract UnipackDAO unipackDAO();

	public abstract UnipackOpenDAO unipackOpenDAO();
}
