package com.ezio.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ezio.entity.Comment;

/**
 * Created by Ezio on 2017/6/28.
 */
public interface CommentDao extends JpaRepository<Comment, Integer> {
	int countBySongId(String songId);
}
