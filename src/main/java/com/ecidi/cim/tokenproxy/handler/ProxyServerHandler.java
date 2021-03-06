package com.ecidi.cim.tokenproxy.handler;

import com.ecidi.cim.tokenproxy.config.ProxyConfig;
import com.ecidi.cim.tokenproxy.factory.BootstrapFactory;
import com.ecidi.cim.tokenproxy.filter.TokenMatchFilter;
import com.ecidi.cim.tokenproxy.initializer.HttpConnectChannelInitializer;
import com.ecidi.cim.tokenproxy.initializer.HttpsConnectChannelInitializer;
import com.ecidi.cim.tokenproxy.listener.HttpChannelFutureListener;
import com.ecidi.cim.tokenproxy.listener.HttpsChannelFutureListener;
import com.ecidi.cim.tokenproxy.main.ProxyServer;
import com.ecidi.cim.tokenproxy.util.ChannelCacheUtil;
import com.ecidi.cim.tokenproxy.util.ProxyUtil;
import com.ecidi.cim.tokenproxy.util.TokenCaffeineCacheUtil;
import com.ecidi.cim.tokenproxy.util.TokenRedisCacheUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * author:ZhengXing
 * datetime:2018-01-21 14:05
 * 代理服务器 输入事件处理类
 * <p>
 * 可共享,线程安全
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class ProxyServerHandler extends ChannelInboundHandlerAdapter {
    private static final String LOG_PRE = "[代理服务器处理类]通道id:{}";

    //属性
    @Autowired
    private ProxyConfig proxyConfig;

    //bootstrap工厂
    @Autowired
    private BootstrapFactory bootstrapFactory;

    @Autowired
    private TokenMatchFilter tokenMatchFilter;

    @Autowired
    private TokenRedisCacheUtil tokenRedisCacheUtil;

//    public ProxyServerHandler(ProxyConfig proxyConfig, BootstrapFactory bootstrapFactory, TokenMatchFilter tokenMatchFilter) {
//        this.proxyConfig = proxyConfig;
//        this.bootstrapFactory = bootstrapFactory;
//        this.tokenMatchFilter = tokenMatchFilter;
//    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        log.info(LOG_PRE + ",客户端关闭连接.", ProxyUtil.getChannelId(ctx));
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info(LOG_PRE + ",通道未激活.", ProxyUtil.getChannelId(ctx));
        ctx.close();
    }

    /**
     * 通道读取到消息 事件
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //通道id
        String channelId = ProxyUtil.getChannelId(ctx);
        try {


            //HTTP/HTTPS : 如果是 http报文格式的,此时已经被编码解码器转为了该类,如果不是,则表示是https协议建立第一次连接后后续的请求等.
            if (msg instanceof FullHttpRequest) {
                final FullHttpRequest request = (FullHttpRequest) msg;

                //获取ip和端口
                InetSocketAddress address = ProxyUtil.getAddressByRequest(request);

                //HTTPS :
                if (HttpMethod.CONNECT.equals(request.method())) {
                    log.info(LOG_PRE + ",https请求.目标:{}", channelId, request.uri());

                    //存入缓存
                    ChannelCacheUtil.put(channelId, new ChannelCache(address, connect(false, address, ctx, msg)));

                    //此处将用于报文编码解码的处理器去除,因为后面上方的信息都是加密过的,不符合一般报文格式,我们直接转发即可
                    ctx.pipeline().remove(ProxyServer.NAME_HTTP_CODE_HANDLER);
                    ctx.pipeline().remove(ProxyServer.NAME_HTTP_CODE_HANDLER1);
                    ctx.pipeline().remove(ProxyServer.NAME_HTTP_AGGREGATOR_HANDLER);



                    //给客户端响应成功信息 HTTP/1.1 200 Connection Established  .失败时直接退出
                    //此处没有添加Connection Established,似乎也没问题
                    if (!ProxyUtil.writeAndFlush(ctx, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), true))
                        return;

                    //此时 客户端已经和目标服务器 建立连接(其实是 客户端 -> 代理服务器 -> 目标服务器),
                    //直接退出等待下一次双方连接即可.
                    return;
                }
                //HTTP:
                log.info(LOG_PRE + ",http请求.目标:{}", channelId, request.uri() + "  " + request.headers().toString());


                String uri = request.uri();
                if (tokenMatchFilter.isUriAllow(uri)) {
                    if (tokenMatchFilter.isUriAuthc(uri)) {
                        String token = ProxyUtil.extractRequestToken(request);
                        // Caffeine Cache
                        Boolean isAuthorized = TokenCaffeineCacheUtil.getTokenInfo(uri);
                        if (isAuthorized != null) {
                            if (!isAuthorized) {
                               ProxyUtil.responseUnauthorizaionToClient(ctx);
                            }
                        } else {
                            // TODO: 判断token
                            isAuthorized = true;
                            TokenCaffeineCacheUtil.setTokenInfo(uri, true);
                        }

                        // Optional Redis Cache
//                        if (tokenRedisCacheUtil.hasTokenInfo(uri)) {
//                            if (!tokenRedisCacheUtil.getTokenInfo(uri)) {
//                               ProxyUtil.responseUnauthorizaionToClient(ctx);
//                            }
//                        } else {
//                            // TODO: 判断token
//                            boolean isAuthorized = true;
//                            tokenRedisCacheUtil.addTokenInfo(uri, true);
//                        }
                    } else {
                        ProxyUtil.responseUnauthorizaionToClient(ctx);
                        ctx.close();
                        return;
                    }
                } else {
                    ProxyUtil.responseNotFoundToClient(ctx);
                    ctx.close();
                    return;
                }



//                HttpHeaders headers = request.headers();
//                headers.add("Connection", headers.get("Proxy-Connection"));
//                headers.remove("Proxy-Connection");


                //http所有通道都不会复用,无需缓存
                connect(true, address, ctx, msg);
                return;
            }


            //其他格式数据(建立https connect后的客户端再次发送的加密数据):
            //从缓存获取到数据
            ChannelCache cache = ChannelCacheUtil.get(ProxyUtil.getChannelId(ctx));
            //如果缓存为空,应该是缓存已经过期,直接返回客户端请求超时,并关闭连接
            if (Objects.isNull(cache)) {
                log.info(LOG_PRE + ",缓存过期", channelId);
                ProxyUtil.writeAndFlush(ctx, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_TIMEOUT), false);
                ctx.close();
                return;
            }

            //HTTPS: 如果此时 与目标服务器建立的连接通道 为空,则表示是Https协议,客户端第二次传输数据过来
//            if (Objects.isNull(cache.getChannelFuture())) {
//                log.info(LOG_PRE + ",https,正在与目标建立连接",channelId);
//                //连接到目标服务器,获取到 连接通道,并将该通道更新到缓存中
//                ChannelCacheUtil.put(channelId,
//                        cache.setChannelFuture(
//                                connect(false, cache.getAddress(), ctx, msg)));
//                log.info(LOG_PRE + ",https,与目标建立连接成功",channelId);
//            } else
//
//                {

            //此处,表示https协议的请求第x次访问(x > 2; 第一次我们响应200,第二次同目标主机建立连接, 此处直接发送消息即可)
            //如果此时通道是可写的,写入消息
            boolean flag = false;
            log.info(LOG_PRE + ",https,正在向目标发送后续消息", channelId);
            for (int i = 0; i < 100; i++) {
                if ((flag = cache.getChannelFuture().channel().isActive()))
                    break;
                Thread.sleep(10);
            }
            if (flag) {
                cache.getChannelFuture().channel().writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess())
                        log.info("通道id:{},https,向目标发送后续消息成功.", channelId);
                    else
                        log.info("通道id:{},https,向目标发送后续消息失败.e:{}", channelId, future.cause());
                });
                return;
            }


            log.info(LOG_PRE + ",https,与目标通道不可写,关闭与客户端连接", channelId);
            ProxyUtil.responseFailedToClient(ctx);
//            }
        } catch (Exception e) {
            log.info(LOG_PRE + "error:{}", channelId, e.getMessage(), e);
        }

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        log.info(LOG_PRE + "读取完成.", ProxyUtil.getChannelId(ctx));
    }

    /**
     * 处理用户自定义事件
     *
     * @param ctx
     * @param evt
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        //如果不是 空闲状态事件,直接返回
        if (!(evt instanceof IdleStateEvent))
            return;

        IdleStateEvent event = (IdleStateEvent) evt;
        //如果是 所有空闲超时事件
        if (event.state() == IdleState.ALL_IDLE) {
            log.debug(LOG_PRE + ",空闲超时,关闭.", ProxyUtil.getChannelId(ctx));
            ctx.close();
        }
    }

    /**
     * 异常处理
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error(LOG_PRE + ",发生异常:{}", ProxyUtil.getChannelId(ctx), cause.getMessage(), cause);
        //关闭
        ctx.close();
    }

    /**
     * 和 目标主机 建立连接
     */
    @SneakyThrows
    private ChannelFuture connect(boolean isHttp, InetSocketAddress address,
                                  ChannelHandlerContext ctx, Object msg) {
        //用工厂类构建bootstrap,用来建立socket连接
        Bootstrap bootstrap = bootstrapFactory.build();
        //如果是http请求
        if (isHttp) {


            //添加监听器,当连接建立成功后,转发客户端的消息给它
            return bootstrap
                    .handler(new HttpConnectChannelInitializer(ctx))
                    .connect(address)
                    .addListener(new HttpChannelFutureListener(msg, ctx));
        }
        //如果是Https请求
        return bootstrap
                .handler(new HttpsConnectChannelInitializer(ctx))
                .connect(address)
                .addListener(new HttpsChannelFutureListener(msg, ctx));
    }


    /**
     * 用于存储每个通道各自信息的缓存类
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Accessors(chain = true)
    public class ChannelCache {
        //目标服务器的地址
        private InetSocketAddress address;
        //当前请求与目标主机建立的连接通道
        private ChannelFuture channelFuture;
    }
}
