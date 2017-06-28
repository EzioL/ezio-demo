package com.ezio.dao;

import com.ezio.entity.Comment;
import com.ezio.entity.Music;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Created by Ezio on 2017/6/28.
 */
public interface CommentDao extends JpaRepository<Comment, Integer> {
	int countBySongId(String songId);
}
