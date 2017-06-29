package com.ezio.pipeline;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import com.ezio.dao.CommentDao;
import com.ezio.dao.MusicDao;
import com.ezio.entity.Comment;
import com.ezio.entity.Music;

import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;

/**
 * Created by Ezio on 2017/6/28.
 */
@Component
public class NetEaseMusicPipeline implements Pipeline {

	@Autowired
	public MusicDao mMusicDao;

	@Autowired
	public CommentDao mCommentDao;

	@Override
	public void process(ResultItems resultItems, Task task) {


		System.out.println(mMusicDao == null);

		for (Map.Entry<String, Object> entry : resultItems.getAll().entrySet()) {
			if (entry.getKey().equals("music")) {
				Music music = (Music) entry.getValue();
				System.out.println("mMusicDao--->null" + mMusicDao == null);
				if (mMusicDao.countBySongId(music.getSongId()) == 0) {
					mMusicDao.save(music);
				}
			} else {
				Comment comment = (Comment) entry.getValue();
				System.out.println("mCommentDao--->null" + mCommentDao == null);
				if (mCommentDao.countBySongId(comment.getSongId()) == 0) {
					mCommentDao.save(comment);
				}
			}

		}
	}


}
