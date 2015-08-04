package org.openas2.processor.receiver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.message.InvalidMessageException;
import org.openas2.message.Message;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.MessageParameters;
import org.openas2.util.IOUtilOld;


public abstract class NetModule extends BaseReceiverModule {
    public static final String PARAM_ADDRESS = "address";
    public static final String PARAM_PORT = "port";
    public static final String PARAM_ERROR_DIRECTORY = "errordir";
    public static final String PARAM_ERRORS = "errors";
    public static final String DEFAULT_ERRORS = "$date.yyyyMMddhhmmss$"; 
    
    private MainThread mainThread;

    public void doStart() throws OpenAS2Exception {
        try {
            mainThread = new MainThread(this, getParameter(PARAM_ADDRESS, false),
                    getParameterInt(PARAM_PORT, true));
            mainThread.start();
        } catch (IOException ioe) {
            throw new WrappedException(ioe);
        }
    }

    public void doStop() throws OpenAS2Exception {
        if (mainThread != null) {
            mainThread.terminate();
            mainThread = null;
        }
    }

    public void init(Session session, Map<String,String> options) throws OpenAS2Exception {
        super.init(session, options);
        getParameter(PARAM_PORT, true);
    }

    protected abstract NetModuleHandler getHandler();

    protected void handleError(Message msg, OpenAS2Exception oae) {
        oae.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
        oae.terminate();

        try {
        	CompositeParameters params = new CompositeParameters(false) .
        		add("date", new DateParameters()) .
        		add("msg", new MessageParameters(msg));

        	String name = params.format(getParameter(PARAM_ERRORS, DEFAULT_ERRORS));
        	String directory = getParameter(PARAM_ERROR_DIRECTORY, true);
        	
            File msgFile = IOUtilOld.getUnique(IOUtilOld.getDirectoryFile(directory),
            						IOUtilOld.cleanFilename(name));
            String msgText = msg.toString();
            FileOutputStream fOut = new FileOutputStream(msgFile);

            fOut.write(msgText.getBytes());
            fOut.close();

            // make sure an error of this event is logged
            InvalidMessageException im = new InvalidMessageException("Stored invalid message to " +
                    msgFile.getAbsolutePath());
            im.terminate();
        } catch (OpenAS2Exception oae2) {
            oae2.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            oae2.terminate();
        } catch (IOException ioe) {
            WrappedException we = new WrappedException(ioe);
            we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            we.terminate();
        }
    }

    protected class ConnectionThread extends Thread {
        private NetModule owner;
        private Socket socket;

        public ConnectionThread(NetModule owner, Socket socket) {
            super();
            this.owner = owner;
            this.socket = socket;
            start();
        }

        public void setOwner(NetModule owner) {
            this.owner = owner;
        }

        public NetModule getOwner() {
            return owner;
        }

        public Socket getSocket() {
            return socket;
        }

        public void run() {
            Socket s = getSocket();

            getOwner().getHandler().handle(getOwner(), s);

            try {
                s.close();
            } catch (IOException sce) {
                new WrappedException(sce).terminate();
            }
        }
    }

    protected class MainThread extends Thread {
        private NetModule owner;
        private ServerSocket socket;
        private boolean terminated;

        public MainThread(NetModule owner, String address, int port)
            throws IOException {
            super();
            this.owner = owner;

            socket = new ServerSocket();

            if (address != null) {
                socket.bind(new InetSocketAddress(address, port));
            } else {
                socket.bind(new InetSocketAddress(port));
            }
        }

        public void setOwner(NetModule owner) {
            this.owner = owner;
        }

        public NetModule getOwner() {
            return owner;
        }

        public ServerSocket getSocket() {
            return socket;
        }

        public void setTerminated(boolean terminated) {
            this.terminated = terminated;

            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    owner.forceStop(e);
                }
            }
        }

        public boolean isTerminated() {
            return terminated;
        }

        public void run() {
            while (!isTerminated()) {
                try {
                    Socket conn = socket.accept();
                    conn.setSoLinger(true, 60);
                    new ConnectionThread(getOwner(), conn);
                } catch (IOException e) {
                    if (!isTerminated()) {
                        owner.forceStop(e);
                    }
                }
            }

            System.out.println("exited");
        }

        
        public void terminate() {
            setTerminated(true);
        }
    }
}
