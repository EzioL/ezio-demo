package com.ezio.service;

import com.ezio.dao.CommentDao;
import com.ezio.dao.MusicDao;
import com.ezio.entity.Comment;
import com.ezio.entity.Music;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Ezio on 2017/6/28.
 */
@Service
public class MusicService {
	@Autowired
	private MusicDao mMusicDao;
	@Autowired
	private CommentDao mCommentDao;

	public void addMusic(Music music) {
		//判断数据是否存在
		if (mMusicDao.countBySongId(music.getSongId()) == 0) {
			mMusicDao.save(music);
		}
	}

	public void addComment(Comment comment) {
		//判断数据是否存在
		if (mCommentDao.countBySongId(comment.getSongId()) == 0) {
			mCommentDao.save(comment);
		}
	}

	public void del() {
		mCommentDao.deleteAll();
		mMusicDao.deleteAll();
	}
}
