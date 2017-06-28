package com.ezio.dao;

import com.ezio.entity.Music;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Created by Ezio on 2017/6/28.
 */

public interface MusicDao extends JpaRepository<Music, Integer> {
	int countBySongId(String songId);
}
