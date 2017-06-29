package com.ezio.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ezio.entity.Music;

/**
 * Created by Ezio on 2017/6/28.
 */
public interface MusicDao extends JpaRepository<Music, Integer> {
	int countBySongId(String songId);

}
