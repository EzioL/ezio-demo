package com.ezio.processor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.ezio.dao.MusicDao;
import com.ezio.entity.Comment;
import com.ezio.entity.Music;
import com.ezio.pipeline.MyPipeline;
import com.ezio.service.MusicService;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import sun.misc.BASE64Encoder;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

/**
 * Created by Ezio on 2017/6/27.
 */
@Component
public class NetEaseMusicPageProcessor implements PageProcessor {

	//正则表达式\\. \\转义java中的\  \.转义正则中的.

	//主域名
	public static final String BASE_URL = "http://music.163.com/";

	//匹配专辑URL
	public static final String ALBUM_URL = "http://music\\.163\\.com/playlist\\?id=\\d+";

	//匹配歌曲URL
	public static final String MUSIC_URL = "http://music\\.163\\.com/song\\?id=\\d+";

	//初始地址, 褐言喜欢的音乐
	public static final String START_URL = "http://music.163.com/playlist?id=148174530";

	//加密使用到的文本
	public static final String TEXT = "{\"username\": \"\", \"rememberLogin\": \"true\", \"password\": \"\"}";

	private Site site = Site.me()
			.setDomain("http://music.163.com")
			.setSleepTime(1000)
			.setRetryTimes(30)
			.setCharset("utf-8")
			.setTimeOut(30000)
			.setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_2) AppleWebKit/537.31 (KHTML, like Gecko) Chrome/26.0.1410.65 Safari/537.31");

	@Autowired
	private MusicService mMusicService;
	@Autowired
	private MusicDao mMusicDao;

	@Override
	public Site getSite() {
		return site;
	}

	@Override
	public void process(Page page) {
		//根据URL判断页面类型
		if (page.getUrl().regex(ALBUM_URL).match()) {
			System.out.println("歌曲总数----->" + page.getHtml().xpath("//span[@id='playlist-track-count']/text()").toString());
			//爬取歌曲URl加入队列
			page.addTargetRequests(page.getHtml().xpath("//div[@id=\"song-list-pre-cache\"]").links().regex(MUSIC_URL).all());

		} else {
			String url = page.getUrl().toString();
			Music music = new Music();
//			page.putField("title", page.getHtml().xpath("//em[@class='f-ff2']/text()"));
//			page.putField("author", page.getHtml().xpath("//p[@class='des s-fc4']/span/a/text()"));
//			page.putField("album", page.getHtml().xpath("//p[@class='des s-fc4']/a/text()"));
//			page.putField("URL", url);
			//单独对AJAX请求获取评论数, 使用JSON解析返回结果
			String songId = url.substring(url.indexOf("id=") + 3);
			int commentCount = getComment(page,songId, 0);
			//page.putField("commentCount", getComment(songId, 0));
			//page.putField("commentCount", JSONPath.eval(JSON.parse(crawlAjaxUrl(url.substring(url.indexOf("id=") + 3))), "$.total"));
			//music 保存到数据库
			music.setSongId(songId);
			music.setCommentCount(commentCount);
			music.setTitle(page.getHtml().xpath("//em[@class='f-ff2']/text()").toString());
			music.setAuthor(page.getHtml().xpath("//p[@class='des s-fc4']/span/a/text()").toString());
			music.setAlbum(page.getHtml().xpath("//p[@class='des s-fc4']/a/text()").toString());
			music.setURL(url);
			page.putField("music", music);
		}
	}

	private int getComment(Page page, String songId, int offset) {
		JSONObject jsonObject = JSON.parseObject(crawlAjaxUrl(songId, offset));
		int commentCount;
		commentCount = (Integer) JSONPath.eval(jsonObject, "$.total");
		for (; offset < commentCount; offset = offset + 50) {
			JSONObject obj = JSON.parseObject(crawlAjaxUrl(songId, offset));
			List<String> contents = (List<String>) JSONPath.eval(obj, "$.comments.content");
			List<Integer> likedCounts = (List<Integer>) JSONPath.eval(obj, "$.comments.likedCount");
			List<String> nicknames = (List<String>) JSONPath.eval(obj, "$.comments.user.nickname");
			//JSONPath.eval(jsonObject, "$.comments");
			for (int i = 0; i < contents.size(); i++) {
				//保存到数据库
				Comment comment = new Comment();
				comment.setSongId(songId);
				comment.setContent(contents.get(i));
				comment.setLikedCount(likedCounts.get(i));
				comment.setNickname(nicknames.get(i));

				page.putField("comment",comment);
			}
		}
		return commentCount;
	}

	public static void main(String[] args) {

		Spider.create(new NetEaseMusicPageProcessor())
				//初始URL
				.addUrl(START_URL)
				.addPipeline(new MyPipeline())
				.run();
		System.out.println("爬虫结束");

	}

	public static void start() {
		Spider.create(new NetEaseMusicPageProcessor())
				//初始URL
				.addUrl(START_URL)
				.addPipeline(new MyPipeline())
				.run();
		System.out.println("爬虫结束");
	}


	private String crawlAjaxUrl(String songId, int offset) {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		CloseableHttpResponse response = null;
		String first_param = "{rid:\"\", offset:\"first_param\", total:\"true\", limit:\"50\", csrf_token:\"\"}";
		first_param = first_param.replace("first_param", offset + "");

		try {
			//参数加密
			//16位随机字符串，直接FFF
			//String secKey = new BigInteger(100, new SecureRandom()).toString(32).substring(0, 16);
			String secKey = "FFFFFFFFFFFFFFFF";
			//两遍ASE加密
			String encText = aesEncrypt(aesEncrypt(first_param, "0CoJUm6Qyw8W8jud"), secKey);
			//
			String encSecKey = rsaEncrypt();

			HttpPost httpPost = new HttpPost("http://music.163.com/weapi/v1/resource/comments/R_SO_4_" + songId + "/?csrf_token=");
			httpPost.addHeader("Referer", BASE_URL);

			List<NameValuePair> ls = new ArrayList<NameValuePair>();
			ls.add(new BasicNameValuePair("params", encText));
			ls.add(new BasicNameValuePair("encSecKey", encSecKey));

			UrlEncodedFormEntity paramEntity = new UrlEncodedFormEntity(ls, "utf-8");
			httpPost.setEntity(paramEntity);

			response = httpclient.execute(httpPost);
			HttpEntity entity = response.getEntity();

			if (entity != null) {
				return EntityUtils.toString(entity, "utf-8");
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				response.close();
				httpclient.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return "";
	}

	/**
	 * ASE-128-CBC加密模式可以需要16位
	 *
	 * @param src 加密内容
	 * @param key 密钥
	 * @return
	 */
	private static String aesEncrypt(String src, String key) throws Exception {
		String encodingFormat = "UTF-8";
		String iv = "0102030405060708";

		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		byte[] raw = key.getBytes();
		SecretKeySpec secretKeySpec = new SecretKeySpec(raw, "AES");
		IvParameterSpec ivParameterSpec = new IvParameterSpec(iv.getBytes());
		//使用CBC模式，需要一个向量vi，增加加密算法强度
		cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
		byte[] encrypted = cipher.doFinal(src.getBytes(encodingFormat));
		return new BASE64Encoder().encode(encrypted);

	}

	private String rsaEncrypt() {
		String secKey = "257348aecb5e556c066de214e531faadd1c55d814f9be95fd06d6bff9f4c7a41f831f6394d5a3fd2e3881736d94a02ca919d952872e7d0a50ebfa1769a7a62d512f5f1ca21aec60bc3819a9c3ffca5eca9a0dba6d6f7249b06f5965ecfff3695b54e1c28f3f624750ed39e7de08fc8493242e26dbc4484a01c76f739e135637c";
		return secKey;
	}


}
