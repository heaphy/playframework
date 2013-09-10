package play;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

class HelloWorldServerHandler extends SimpleChannelHandler {
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        e.getChannel().write("Hello, World");
    }

    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        System.out.println("Unexpected exception from downstream."
                + e.getCause());
        e.getChannel().close();
    }
}

class HelloWorldClientHandler extends SimpleChannelHandler {

    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        String message = (String) e.getMessage();
        System.out.println(message);
        e.getChannel().close();
    }

    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        System.out.println("Unexpected exception from downstream."
                + e.getCause());
        e.getChannel().close();
    }
}


/**
 * Netty VS MinaNetty基于Pipeline处理，Mina基于Filter过滤
 * Netty的事件驱动模型具有更好的扩展性和易用性
 * Https，SSL，PB，RSTP，Text &Binary等协议支持
 * Netty中UDP传输有更好的支持官方测试Netty比Mina性能更好
 * @author Administrator
 *
 */
public class NettyTest {

    public void testServer() {
        //初始化channel的辅助类，为具体子类提供公共数据结构
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", new StringDecoder());
                pipeline.addLast("encoder", new StringEncoder());
                pipeline.addLast("handler", new HelloWorldServerHandler());
                return pipeline;
            }
        });
        //创建服务器端channel的辅助类,接收connection请求
        bootstrap.bind(new InetSocketAddress(8080));
    }



    public void testClient() {
        //创建客户端channel的辅助类,发起connection请求
        ClientBootstrap bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));
        //It means one same HelloWorldClientHandler instance is going to handle multiple Channels and consequently the data will be corrupted.
        //基于上面这个描述，必须用到ChannelPipelineFactory每次创建一个pipeline
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {
                ChannelPipeline pipeline =  Channels.pipeline();
                pipeline.addLast("decoder", new StringDecoder());
                pipeline.addLast("encoder", new StringEncoder());
                pipeline.addLast("handler", new HelloWorldClientHandler());
                return pipeline;
            }
        });
        //创建无连接传输channel的辅助类(UDP),包括client和server
        ChannelFuture future = bootstrap.connect(new InetSocketAddress(
                "localhost", 8080));
        future.getChannel().getCloseFuture().awaitUninterruptibly();
        bootstrap.releaseExternalResources();
    }


    @Test
    public void testNetty(){
        testServer();
        testClient();
    }

}