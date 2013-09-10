package play;

import static org.jboss.netty.buffer.ChannelBuffers.dynamicBuffer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.junit.Test;

/**
 * 用POJO代替ChannelBuffer
 */
class TimeServerHandler extends SimpleChannelHandler {

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        Persons person = new Persons("周杰伦123", 31, 10000.44);
        ChannelFuture future = e.getChannel().write(person);
        future.addListener(ChannelFutureListener.CLOSE);
    }
}

class TimeClientHandler extends SimpleChannelHandler{

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        Persons person = (Persons)e.getMessage();
        System.out.println(person);
        e.getChannel().close();
    }
}

/**
 * FrameDecoder and ReplayingDecoder allow you to return an object of any type.
 *
 */
class TimeDecoder extends FrameDecoder {
    private final ChannelBuffer buffer = dynamicBuffer();

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel,
                            ChannelBuffer channelBuffer) throws Exception {
        if(channelBuffer.readableBytes() < 4) {
            return null;
        }
        if (channelBuffer.readable()) {
            // 读到,并写入buf
            channelBuffer.readBytes(buffer, channelBuffer.readableBytes());
        }
        int namelength = buffer.readInt();
        String name = new String(buffer.readBytes(namelength).array(), "UTF-8");
        int age = buffer.readInt();
        double salary = buffer.readDouble();
        Persons person = new Persons(name, age, salary);
        return person;
    }

}

class TimeEncoder extends SimpleChannelHandler {
    private final ChannelBuffer buffer = dynamicBuffer();

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        Persons person = (Persons)e.getMessage();
        buffer.writeInt(person.getName().getBytes("UTF-8").length);
        buffer.writeBytes(person.getName().getBytes("UTF-8"));
        buffer.writeInt(person.getAge());
        buffer.writeDouble(person.getSalary());
        Channels.write(ctx, e.getFuture(), buffer);
    }
}

class Persons {
    private String name;
    private int age;
    private double salary;

    public Persons(String name,int age,double salary){
        this.name = name;
        this.age = age;
        this.salary = salary;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getAge() {
        return age;
    }
    public void setAge(int age) {
        this.age = age;
    }
    public double getSalary() {
        return salary;
    }
    public void setSalary(double salary) {
        this.salary = salary;
    }

    @Override
    public String toString() {
        return "Persons [name=" + name + ", age=" + age + ", salary=" + salary
                + "]";
    }
}

public class NettyPojoTest {
    public void testServer() {
        ChannelFactory factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
        ServerBootstrap bootstrap = new ServerBootstrap(factory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(new TimeEncoder(), new TimeServerHandler());
            }
        });

        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);

        bootstrap.bind(new InetSocketAddress("localhost", 9999));
    }

    public void testClient() {
        //创建客户端channel的辅助类,发起connection请求
        ClientBootstrap bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {
                ChannelPipeline pipeline =  Channels.pipeline();
                pipeline.addLast("decoder", new TimeDecoder());
                pipeline.addLast("encoder", new TimeEncoder());
                pipeline.addLast("handler", new TimeClientHandler());
                return pipeline;
            }
        });
        //创建无连接传输channel的辅助类(UDP),包括client和server
        ChannelFuture future = bootstrap.connect(new InetSocketAddress(
                "localhost", 9999));
        future.getChannel().getCloseFuture().awaitUninterruptibly();
        bootstrap.releaseExternalResources();
    }

    @Test
    public void testNetty() {
        testServer();
        testClient();
    }
}