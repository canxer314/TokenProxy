package com.ecidi.cim.tokenproxy.util;

import com.ecidi.cim.tokenproxy.config.ProxyConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

/**
 * author:ZhengXing
 * datetime:2018-01-21 17:12
 * 工具类
 */
@Slf4j
public class ProxyUtil {


	/**
	 * 使用ctx发送消息
	 * 会自动检测是否可写
	 *
	 * @param isCloseOnError 异常时是否关闭
	 */
	public static boolean writeAndFlush(ChannelHandlerContext ctx,Object msg, boolean isCloseOnError) {
		if(ctx.channel().isActive()){
			log.info("通道id:{},正在向客户端写入数据.",getChannelId(ctx));
			ctx.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
				if(future.isSuccess())
					log.info("通道id:{},向客户端写入数据成功.",getChannelId(ctx));
				else
					log.info("通道id:{},向客户端写入数据失败.e:{}",getChannelId(ctx),future.cause().getMessage(),future.cause());
			});
			return true;
		}
		else
		if (isCloseOnError)
			ctx.close();
		return false;
	}



	/**
	 * 从channel中解析出客户端ip
	 */
	public static String getIpByChannel(Channel channel) {
		return ((InetSocketAddress)channel.remoteAddress()).getAddress().getHostAddress();
	}

	/**
	 * 从request中获取 客户端请求的目标服务器的 ip和port
	 */
	public static InetSocketAddress getAddressByRequest(FullHttpRequest request) {
		//获取请求的主机和端口
		String[] temp1 = request.headers().get("host").split(ProxyConfig.HOST_SEPARATOR);
		//有些host没有端口,则默认为80
//		return  new InetSocketAddress(temp1[0], temp1.length == 1 ? 80 : Integer.parseInt(temp1[1]));
		return new InetSocketAddress("10.218.51.214", 8090);
	}

	/**
	 * 从ctx中获取到当前通道的id
	 */
	public static String getChannelId(ChannelHandlerContext ctx) {
		return ctx.channel().id().asShortText();
	}

	/**
	 * 使用ctx给客户端发送失败响应,默认就为请求超时
	 */
	public static void responseFailedToClient(ChannelHandlerContext ctx) {
		ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_TIMEOUT));
	}

	public static String extractRequestToken(FullHttpRequest request) {
		String uri = request.uri();
		String token = null;
		String newUri = "";
		int paraIndx = uri.indexOf("?");
		if (paraIndx > -1) {
			int tsIndx = uri.indexOf("token", paraIndx);
			if (tsIndx > -1) {
				int teIndx = uri.length();
				boolean ab = false; // if there '&' before token
				// when there's a '&' in front, omit '&' behind
				if (uri.charAt(tsIndx - 1) == '&') {
					ab = true;
				}
				int aIndx = uri.indexOf("&", tsIndx + 6);
				if (aIndx > -1) {
					teIndx = aIndx;
				}
				token = uri.substring(tsIndx + 6, teIndx);
				newUri = uri.substring(0, ab ? tsIndx - 1 : tsIndx) + uri.substring(ab ? teIndx : aIndx > -1 ? teIndx + 1 : teIndx);
				request.setUri(newUri);
			}
		}
		return token;
	}
}
