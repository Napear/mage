package org.mage.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mage.choices.Choice;
import mage.view.AbilityPickerView;
import mage.view.CardsView;
import mage.view.ChatMessage;
import mage.view.DeckView;
import mage.view.DraftPickView;
import mage.view.DraftView;
import mage.view.GameClientMessage;
import mage.view.GameEndView;
import mage.view.GameView;
import mage.view.UserRequestMessage;
import org.apache.log4j.Logger;
import org.mage.network.handlers.PingMessageHandler;
import org.mage.network.handlers.WriteListener;
import org.mage.network.handlers.server.ConnectionHandler;
import org.mage.network.handlers.server.HeartbeatHandler;
import org.mage.network.handlers.server.ServerExceptionHandler;
import org.mage.network.handlers.server.ServerRequestHandler;
import org.mage.network.interfaces.MageServer;
import org.mage.network.messages.MessageType;
import org.mage.network.messages.callback.ChatMessageCallback;
import org.mage.network.messages.callback.ConstructCallback;
import org.mage.network.messages.callback.DraftInitCallback;
import org.mage.network.messages.callback.DraftOverCallback;
import org.mage.network.messages.callback.DraftPickCallback;
import org.mage.network.messages.callback.DraftUpdateCallback;
import org.mage.network.messages.callback.GameAskCallback;
import org.mage.network.messages.callback.GameChooseAbilityCallback;
import org.mage.network.messages.callback.GameChooseChoiceCallback;
import org.mage.network.messages.callback.GameChoosePileCallback;
import org.mage.network.messages.callback.GameEndInfoCallback;
import org.mage.network.messages.callback.GameErrorCallback;
import org.mage.network.messages.callback.GameInformCallback;
import org.mage.network.messages.callback.GameInformPersonalCallback;
import org.mage.network.messages.callback.GameInitCallback;
import org.mage.network.messages.callback.GameOverCallback;
import org.mage.network.messages.callback.GamePlayManaCallback;
import org.mage.network.messages.callback.GamePlayXManaCallback;
import org.mage.network.messages.callback.GameSelectAmountCallback;
import org.mage.network.messages.callback.GameSelectCallback;
import org.mage.network.messages.callback.GameStartedCallback;
import org.mage.network.messages.callback.GameTargetCallback;
import org.mage.network.messages.callback.GameUpdateCallback;
import org.mage.network.messages.callback.InformClientCallback;
import org.mage.network.messages.callback.JoinedTableCallback;
import org.mage.network.messages.callback.ShowTournamentCallback;
import org.mage.network.messages.callback.SideboardCallback;
import org.mage.network.messages.callback.StartDraftCallback;
import org.mage.network.messages.callback.TournamentStartedCallback;
import org.mage.network.messages.callback.UserRequestDialogCallback;
import org.mage.network.messages.callback.WatchGameCallback;

/**
 *
 * @author BetaSteward
 */
public class Server {

    private static final Logger logger = Logger.getLogger(Server.class);

    private static final int IDLE_PING_TIME = 30;
    private static final int IDLE_TIMEOUT = 60;
//    private static final PingMessage ping = new PingMessage();

    public static final ChannelGroup clients = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private SslContext sslCtx;

    private final MageServer server;
//    private final MessageHandler h;
    private final PingMessageHandler pingMessageHandler = new PingMessageHandler();
    private final EventExecutorGroup handlersExecutor = new DefaultEventExecutorGroup(Runtime.getRuntime().availableProcessors() * 2);
    private final ServerRequestHandler serverMessageHandler;

    private final ServerExceptionHandler exceptionHandler;

    public Server(MageServer server) {
        this.server = server;
//        h = new MessageHandler();
        serverMessageHandler = new ServerRequestHandler(server);
        exceptionHandler = new ServerExceptionHandler(server);
    }

    public void start(int port, boolean ssl) throws Exception {

        if (ssl) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());
        } else {
            sslCtx = null;
        }

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ServerInitializer());

            b.bind(port).sync().channel().closeFuture().sync();
        } catch (InterruptedException ex) {
            logger.fatal("Error starting server", ex);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

    }

    private class ServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        public void initChannel(SocketChannel ch) throws Exception {

            if (sslCtx != null) {
                ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()));
            }
            ch.pipeline().addLast(new ObjectDecoder(10 * 1024 * 1024, ClassResolvers.cacheDisabled(null)));
            ch.pipeline().addLast(new ObjectEncoder());

//            ch.pipeline().addLast("h", h);
            ch.pipeline().addLast("idleStateHandler", new IdleStateHandler(IDLE_TIMEOUT, IDLE_PING_TIME, 0));

            ch.pipeline().addLast(handlersExecutor, "heartbeatHandler", new HeartbeatHandler(server));
            ch.pipeline().addLast("pingMessageHandler", pingMessageHandler);

            ch.pipeline().addLast("connectionHandler", new ConnectionHandler());
            ch.pipeline().addLast(handlersExecutor, "serverMessageHandler", serverMessageHandler);
            ch.pipeline().addLast("exceptionHandler", exceptionHandler);
        }

    }

    private Channel findChannel(String sessionId) {
        for (Channel channel : clients) {
            if (channel.id().asLongText().equals(sessionId)) {
                return channel;
            }
        }
        return null;
    }

    public void sendChatMessage(String sessionId, UUID chatId, ChatMessage message) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new ChatMessageCallback(chatId, message)).addListener(WriteListener.getInstance());
        }
    }

    public void informClient(String sessionId, String title, String message, MessageType type) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new InformClientCallback(title, message, type)).addListener(WriteListener.getInstance());
        }
    }

    public void informClients(String title, String message, MessageType type) {
        clients.writeAndFlush(new InformClientCallback(title, message, type)).addListener(WriteListener.getInstance());
    }

    public void pingClient(String sessionId) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            HeartbeatHandler heartbeatHandler = (HeartbeatHandler) ch.pipeline().get("heartbeatHandler");
            heartbeatHandler.pingClient();
        }
    }

    public void joinedTable(String sessionId, UUID roomId, UUID tableId, UUID chatId, boolean owner, boolean tournament) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new JoinedTableCallback(roomId, tableId, chatId, owner, tournament)).addListener(WriteListener.getInstance());
        }
    }

    public void gameStarted(String sessionId, UUID gameId, UUID playerId) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameStartedCallback(gameId, playerId)).addListener(WriteListener.getInstance());
        }
    }

    public void initGame(String sessionId, UUID gameId, GameView gameView) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameInitCallback(gameId, gameView)).addListener(WriteListener.getInstance());
        }
    }

    public void gameAsk(String sessionId, UUID gameId, GameView gameView, String question, Map<String, Serializable> options) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameAskCallback(gameId, gameView, question, options)).addListener(WriteListener.getInstance());
        }
    }

    public void gameTarget(String sessionId, UUID gameId, GameView gameView, String question, CardsView cardView, Set<UUID> targets, boolean required, Map<String, Serializable> options) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameTargetCallback(gameId, gameView, question, cardView, targets, required, options)).addListener(WriteListener.getInstance());
        }
    }

    public void gameSelect(String sessionId, UUID gameId, GameView gameView, String message, Map<String, Serializable> options) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameSelectCallback(gameId, gameView, message, options)).addListener(WriteListener.getInstance());
        }
    }

    public void gameChooseAbility(String sessionId, UUID gameId, AbilityPickerView abilities) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameChooseAbilityCallback(gameId, abilities)).addListener(WriteListener.getInstance());
        }
    }

    public void gameChoosePile(String sessionId, UUID gameId, String message, CardsView pile1, CardsView pile2) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameChoosePileCallback(gameId, message, pile1, pile2)).addListener(WriteListener.getInstance());
        }
    }

    public void gameChooseChoice(String sessionId, UUID gameId, Choice choice) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameChooseChoiceCallback(gameId, choice)).addListener(WriteListener.getInstance());
        }
    }

    public void gamePlayMana(String sessionId, UUID gameId, GameView gameView, String message, Map<String, Serializable> options) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GamePlayManaCallback(gameId, gameView, message, options)).addListener(WriteListener.getInstance());
        }
    }

    public void gamePlayXMana(String sessionId, UUID gameId, GameView gameView, String message) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GamePlayXManaCallback(gameId, gameView, message)).addListener(WriteListener.getInstance());
        }
    }

    public void gameSelectAmount(String sessionId, UUID gameId, String message, int min, int max) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameSelectAmountCallback(gameId, message, min, max)).addListener(WriteListener.getInstance());
        }
    }

    public void endGameInfo(String sessionId, UUID gameId, GameEndView view) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameEndInfoCallback(gameId, view)).addListener(WriteListener.getInstance());
        }
    }

    public void userRequestDialog(String sessionId, UUID gameId, UserRequestMessage userRequestMessage) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new UserRequestDialogCallback(gameId, userRequestMessage)).addListener(WriteListener.getInstance());
        }
    }

    public void gameUpdate(String sessionId, UUID gameId, GameView view) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameUpdateCallback(gameId, view)).addListener(WriteListener.getInstance());
        }
    }

    public void gameInform(String sessionId, UUID gameId, GameClientMessage message) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameInformCallback(gameId, message)).addListener(WriteListener.getInstance());
        }
    }

    public void gameInformPersonal(String sessionId, UUID gameId, GameClientMessage message) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameInformPersonalCallback(gameId, message)).addListener(WriteListener.getInstance());
        }
    }

    public void gameOver(String sessionId, UUID gameId, String message) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameOverCallback(gameId, message)).addListener(WriteListener.getInstance());
        }
    }

    public void gameError(String sessionId, UUID gameId, String message) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new GameErrorCallback(gameId, message)).addListener(WriteListener.getInstance());
        }
    }

    public void startDraft(String sessionId, UUID draftId, UUID playerId) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new StartDraftCallback(draftId, playerId)).addListener(WriteListener.getInstance());
        }
    }

    public void draftInit(String sessionId, UUID draftId, DraftPickView draftPickView) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new DraftInitCallback(draftId, draftPickView)).addListener(WriteListener.getInstance());
        }
    }

    public void draftUpdate(String sessionId, UUID draftId, DraftView draftView) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new DraftUpdateCallback(draftId, draftView)).addListener(WriteListener.getInstance());
        }
    }

    public void draftOver(String sessionId, UUID draftId) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new DraftOverCallback(draftId)).addListener(WriteListener.getInstance());
        }
    }

    public void draftPick(String sessionId, UUID draftId, DraftPickView draftPickView) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new DraftPickCallback(draftId, draftPickView)).addListener(WriteListener.getInstance());
        }
    }

    public void sideboard(String sessionId, UUID tableId, DeckView deck, int time, boolean limited) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new SideboardCallback(tableId, deck, time, limited)).addListener(WriteListener.getInstance());
        }
    }

    public void construct(String sessionId, UUID tableId, DeckView deck, int time) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new ConstructCallback(tableId, deck, time)).addListener(WriteListener.getInstance());
        }
    }

    public void tournamentStarted(String sessionId, UUID tournamentId, UUID playerId) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new TournamentStartedCallback(tournamentId, playerId)).addListener(WriteListener.getInstance());
        }
    }

    public void showTournament(String sessionId, UUID tournamentId) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new ShowTournamentCallback(tournamentId)).addListener(WriteListener.getInstance());
        }
    }

    public void watchGame(String sessionId, UUID gameId, UUID chatId, GameView game) {
        Channel ch = findChannel(sessionId);
        if (ch != null) {
            ch.writeAndFlush(new WatchGameCallback(gameId, chatId, game)).addListener(WriteListener.getInstance());
        }
    }

    public void replayGame(String sessionId, UUID gameId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public void replayInit(String sessionId, UUID gameId, GameView gameView) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public void replayDone(String sessionId, UUID gameId, String result) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public void replayUpdate(String sessionId, UUID gameId, GameView gameView) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
