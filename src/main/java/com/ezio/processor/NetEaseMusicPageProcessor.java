package com.ezio.processor;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.ezio.entity.Comment;
import com.ezio.entity.Music;
import com.ezio.pipeline.NetEaseMusicPipeline;
import com.ezio.service.MusicService;

import sun.misc.BASE64Encoder;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.downloader.HttpClientDownloader;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.proxy.Proxy;
import us.codecraft.webmagic.proxy.SimpleProxyProvider;

/**
 * Created by Ezio on 2017/6/27.
 */
@Component
public class NetEaseMusicPageProcessor implements PageProcessor {

	// 正则表达式\\. \\转义java中的\ \.转义正则中的.

	// 主域名
	public static final String BASE_URL = "http://music.163.com/";

	// 匹配专辑URL
	public static final String ALBUM_URL = "http://music\\.163\\.com/playlist\\?id=\\d+";

	// 匹配歌曲URL
	public static final String MUSIC_URL = "http://music\\.163\\.com/song\\?id=\\d+";

	// 初始地址, 褐言喜欢的音乐
	public static final String START_URL = "http://music.163.com/playlist?id=148174530";
	public static final int ONE_PAGE = 20;

	private Site site = Site.me()
			.setDomain("http://music.163.com")
			.setSleepTime(1000)
			.setRetryTimes(30)
			.setCharset("utf-8")
			.setTimeOut(30000)
			.setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_2) AppleWebKit/537.31 (KHTML, like Gecko) Chrome/26.0.1410.65 Safari/537.31");

	@Override
	public Site getSite() {
		return site;
	}
	@Autowired
	MusicService mMusicService;

	@Override
	public void process(Page page) {
		// 根据URL判断页面类型
		if (page.getUrl().regex(ALBUM_URL).match()) {
			System.out.println("歌曲总数----->" + page.getHtml().xpath("//span[@id='playlist-track-count']/text()").toString());
			// 爬取歌曲URl加入队列
			page.addTargetRequests(page.getHtml().xpath("//div[@id=\"song-list-pre-cache\"]").links().regex(MUSIC_URL).all());
		} else {
			String url = page.getUrl().toString();
			Music music = new Music();
			// 单独对AJAX请求获取评论数, 使用JSON解析返回结果
			String songId = url.substring(url.indexOf("id=") + 3);
			int commentCount = getComment(page, songId, 0);
			// music 保存到数据库
			music.setSongId(songId);
			music.setCommentCount(commentCount);
			music.setTitle(page.getHtml().xpath("//em[@class='f-ff2']/text()").toString());
			music.setAuthor(page.getHtml().xpath("//p[@class='des s-fc4']/span/a/text()").toString());
			music.setAlbum(page.getHtml().xpath("//p[@class='des s-fc4']/a/text()").toString());
			music.setURL(url);
			//page.putField("music", music);
			mMusicService.addMusic(music);


		}
	}

	private int getComment(Page page, String songId, int offset) {
		int commentCount;
		String s = crawlAjaxUrl(songId, offset);

		if (s.contains("503 Service Temporarily Unavailable")) {
			commentCount = -1;
		} else {
			JSONObject jsonObject = JSON.parseObject(s);
			commentCount = (Integer) JSONPath.eval(jsonObject, "$.total");
			for (; offset < commentCount; offset = offset + ONE_PAGE) {
				JSONObject obj = JSON.parseObject(crawlAjaxUrl(songId, offset));
				List<String> contents = (List<String>) JSONPath.eval(obj, "$.comments.content");
				List<Integer> likedCounts = (List<Integer>) JSONPath.eval(obj, "$.comments.likedCount");
				List<String> nicknames = (List<String>) JSONPath.eval(obj, "$.comments.user.nickname");
				List<Long> times = (List<Long>) JSONPath.eval(obj, "$.comments.time");
				List<Comment> comments = new ArrayList<>();
				for (int i = 0; i < contents.size(); i++) {
					// 保存到数据库
					Comment comment = new Comment();
					comment.setSongId(songId);
					comment.setContent(filterEmoji(contents.get(i)));
					comment.setLikedCount(likedCounts.get(i));
					comment.setNickname(nicknames.get(i));
					comment.setTime(stampToDate(times.get(i)));
					comments.add(comment);
					//page.putField("comment", comment);
				}
				mMusicService.addComments(comments);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		return commentCount;
	}


	public void start(NetEaseMusicPageProcessor processor, NetEaseMusicPipeline netEaseMusicPipeline) {

		HttpClientDownloader httpClientDownloader = new HttpClientDownloader();
		Proxy proxy1 = new Proxy("125.38.39.43", 9797);
		Proxy proxy2 = new Proxy("127.0.0.1", 1080);

		httpClientDownloader.setProxyProvider(SimpleProxyProvider.from(proxy2));

		long start = System.currentTimeMillis();
		Spider.create(processor)
				.addUrl(START_URL)
				.addPipeline(netEaseMusicPipeline)
				//.setDownloader(httpClientDownloader)
				.run();
		long end = System.currentTimeMillis();
		System.out.println("爬虫结束,耗时--->" + parseMillisecone(end - start));

	}

	private String crawlAjaxUrl(String songId, int offset) {

		CloseableHttpClient httpclient = HttpClients.createDefault();
		CloseableHttpResponse response = null;
		String first_param = "{rid:\"\", offset:\"offset_param\", total:\"true\", limit:\"20\", csrf_token:\"\"}";
		first_param = first_param.replace("offset_param", offset + "");
		//first_param = first_param.replace("limit_param", ONE_PAGE + "");
		try {
			// 参数加密
			// 16位随机字符串，直接FFF
			// String secKey = new BigInteger(100, new SecureRandom()).toString(32).substring(0, 16);
			String secKey = "FFFFFFFFFFFFFFFF";
			// 两遍ASE加密
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
		// 使用CBC模式，需要一个向量vi，增加加密算法强度
		cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
		byte[] encrypted = cipher.doFinal(src.getBytes(encodingFormat));
		return new BASE64Encoder().encode(encrypted);

	}

	private String rsaEncrypt() {
		String secKey = "257348aecb5e556c066de214e531faadd1c55d814f9be95fd06d6bff9f4c7a41f831f6394d5a3fd2e3881736d94a02ca919d952872e7d0a50ebfa1769a7a62d512f5f1ca21aec60bc3819a9c3ffca5eca9a0dba6d6f7249b06f5965ecfff3695b54e1c28f3f624750ed39e7de08fc8493242e26dbc4484a01c76f739e135637c";
		return secKey;
	}

	public static String parseMillisecone(long millisecond) {
		String time = null;
		try {
			long yushu_day = millisecond % (1000 * 60 * 60 * 24);
			long yushu_hour = (millisecond % (1000 * 60 * 60 * 24))
					% (1000 * 60 * 60);
			long yushu_minute = millisecond % (1000 * 60 * 60 * 24)
					% (1000 * 60 * 60) % (1000 * 60);
			@SuppressWarnings("unused")
			long yushu_second = millisecond % (1000 * 60 * 60 * 24)
					% (1000 * 60 * 60) % (1000 * 60) % 1000;
			if (yushu_day == 0) {
				return (millisecond / (1000 * 60 * 60 * 24)) + "天";
			} else {
				if (yushu_hour == 0) {
					return (millisecond / (1000 * 60 * 60 * 24)) + "天"
							+ (yushu_day / (1000 * 60 * 60)) + "时";
				} else {
					if (yushu_minute == 0) {
						return (millisecond / (1000 * 60 * 60 * 24)) + "天"
								+ (yushu_day / (1000 * 60 * 60)) + "时"
								+ (yushu_hour / (1000 * 60)) + "分";
					} else {
						return (millisecond / (1000 * 60 * 60 * 24)) + "天"
								+ (yushu_day / (1000 * 60 * 60)) + "时"
								+ (yushu_hour / (1000 * 60)) + "分"
								+ (yushu_minute / 1000) + "秒";

					}

				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return time;
	}

	/*
     * 将时间戳转换为时间
     */
	public static String stampToDate(long s){
		String res;
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		long lt = s;
		Date date = new Date(lt);
		res = simpleDateFormat.format(date);
		return res;
	}
	/**
	 * 将emoji表情替换成*
	 *
	 * @param source
	 * @return 过滤后的字符串
	 */
	public static String filterEmoji(String source) {
		if(StringUtils.isNotBlank(source)){
			return source.replaceAll("[\\ud800\\udc00-\\udbff\\udfff\\ud800-\\udfff]", "*");
		}else{
			return source;
		}
	}
}
