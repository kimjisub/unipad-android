package com.kimjisub.launchpad.db.ent;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.kimjisub.launchpad.db.converter.DateConverter;

import java.util.Date;

@Entity
@TypeConverters(DateConverter.class)
public class UnipackOpenENT {
	@PrimaryKey(autoGenerate = true)
	private int id;
	private String path;
	private Date created_at;


	// Constructor

	public UnipackOpenENT(String path, Date created_at) {
		this.path = path;
		this.created_at = created_at;
	}


	// Getter Setter
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Date getCreated_at() {
		return created_at;
	}

	public void setCreated_at(Date created_at) {
		this.created_at = created_at;
	}

	@Override
	public String toString() {
		return "UnipackOpenENT{" +
				"id=" + id +
				", path='" + path + '\'' +
				", created_at=" + created_at +
				'}';
	}
}
