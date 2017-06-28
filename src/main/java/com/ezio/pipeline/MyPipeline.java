package com.ezio.pipeline;

import com.ezio.dao.CommentDao;
import com.ezio.dao.MusicDao;
import com.ezio.entity.Comment;
import com.ezio.entity.Music;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Map;

import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;

/**
 * Created by Ezio on 2017/6/28.
 */
@Repository
public class MyPipeline implements Pipeline {
	@Autowired
	protected MusicDao mMusicDao;
	@Autowired
	protected CommentDao mCommentDao;

	@Override
	public void process(ResultItems resultItems, Task task) {

		for (Map.Entry<String, Object> entry : resultItems.getAll().entrySet()) {
			if (entry.getKey().equals("music")){
				Music music = (Music) entry.getValue();
				mMusicDao.save(music);
			}else {
				Comment comment = (Comment) entry.getValue();
				mCommentDao.save(comment);
			}

		}
	}
}
