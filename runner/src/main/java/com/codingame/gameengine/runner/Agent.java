package com.codingame.gameengine.runner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

abstract class Agent {

    public static final Charset UTF8 = Charset.forName("UTF-8");
    public static final int AGENT_MAX_BUFFER_SIZE = 10_000;
    // cg-patched: env-overridable stderr drain + throttle (see cg-patches/README.md)
    public static final int THRESHOLD_LIMIT_STDERR_SIZE = cgPatchedEnvInt("CG_STDERR_THRESHOLD", 4096 * 50);
    private static final int STDERR_DRAIN_PER_TURN = cgPatchedEnvInt("CG_STDERR_DRAIN_PER_TURN", 65536);

    private static int cgPatchedEnvInt(String key, int defaultVal) {
        String v = System.getenv(key);
        if (v == null || v.isEmpty()) return defaultVal;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return defaultVal; }
    }

    private static Log log = LogFactory.getLog(Agent.class);

    private OutputStream processStdin;
    private InputStream processStdout;
    private InputStream processStderr;
    private int totalStderrBytesSent = 0;
    private int agentId;
    private boolean lastAgentByteIsCarriageReturn = false;
    private boolean failed = false;

    private String nickname;
    private String avatar;

    public long lastExecutionTimeMs;
    public Agent() {
    }

    abstract protected OutputStream getInputStream();

    abstract protected InputStream getOutputStream();

    abstract protected InputStream getErrorStream();

    /**
     * Initialize an agent given global properties. A call to this function is needed before-all
     *
     * @param conf
     *            Global configuration
     */
    public void initialize(Properties conf) {
        this.lastExecutionTimeMs = 0;
    }

    /**
     * Compile and run an agent. After this, agent is ready for input / output
     */
    public void execute() {
        try {
            this.processStdin = getInputStream();
            this.processStdout = getOutputStream();
            this.processStderr = getErrorStream();
            runInputOutput();
        } catch (Exception e) {
            setFailed(true);
            log.error("" + e.getMessage(), e);
        }
    }

    public void destroy() {
    }

    /**
     * Launch the agent. After the call, agent is ready to process input / output
     *
     * @throws Exception
     *             if an error occurs
     */
    protected abstract void runInputOutput() throws Exception;

    /**
     * Write 'input' to standard input of agent
     *
     * @param input
     *            an input to write
     */
    public void sendInput(String input) {
        if (processStdin != null) {
            try {
                if (log.isTraceEnabled()) {
                    log.trace("Send input to agent " + this.agentId + " : " + input);
                }
                processStdin.write(input.getBytes(UTF8));
                processStdin.flush();
            } catch (IOException e) {
                processStdin = null;
            }
        }
    }

    /**
     * Get the output of an agent
     *
     * @param nbLine
     *            Number of lines wanted
     * @param timeout
     *            Stop reading after timeout milliseconds
     * @return the agent output
     */
    public String getOutput(int nbLine, long timeout) {
        if (processStdout == null) {
            return null;
        }

        try {
            byte[] tmp = new byte[AGENT_MAX_BUFFER_SIZE];
            int offset = 0;
            int nbOccurences = 0;

            long t0 = System.nanoTime();

            while ((offset < AGENT_MAX_BUFFER_SIZE) && (nbOccurences < nbLine)) {
                long current = System.nanoTime();
                if ((current - t0) > (timeout * 1_000_000l)) {
                    break;
                }

                if (processStdout.available() > 0) {
                    int nbRead = processStdout.read(tmp, offset, 1);
                    if (nbRead < 0) {
                        // Should not happen, just in case...
                        break;
                    }
                    byte curByte = tmp[offset];
                    if (!((curByte == '\n') && lastAgentByteIsCarriageReturn)) {
                        offset += nbRead;
                        if ((curByte == '\n') || (curByte == '\r')) {
                            ++nbOccurences;
                        }
                    }
                    lastAgentByteIsCarriageReturn = curByte == '\r';
                } else {
                    if ((offset < AGENT_MAX_BUFFER_SIZE) && (nbOccurences < nbLine)) {
                        Thread.sleep(1);
                    }
                }
            }

            return new String(tmp, 0, offset, UTF8);
        } catch (IOException e1) {
            processStdout = null;
        } catch (InterruptedException e) {
            // wtf
        }
        return null;
    }

    /**
     * Read all errors from standard error stream
     *
     * @return all errors
     */
    public String readError() {
        if (processStderr == null) {
            return null;
        }
        try {
            if (processStderr.available() <= 0) {
                return null;
            }
            // cg-patched: drain up to STDERR_DRAIN_PER_TURN bytes per turn (was a single
            // 4096-byte read upstream, which let verbose bots accumulate in the OS pipe
            // until cerr blocked and the bot timed out). 200KB throttle preserved exactly
            // as upstream — totalStderrBytesSent is never incremented in upstream and we
            // do not change that here (the throttle is effectively vestigial; raising it
            // via CG_STDERR_THRESHOLD is still supported for forward compat).
            int limit = (totalStderrBytesSent > THRESHOLD_LIMIT_STDERR_SIZE) ? 1024 : STDERR_DRAIN_PER_TURN;
            byte[] tmp = new byte[limit];
            int total = 0;
            while (total < limit) {
                if (processStderr.available() <= 0) break;
                int nbRead = processStderr.read(tmp, total, limit - total);
                if (nbRead <= 0) break;
                total += nbRead;
            }
            return new String(tmp, 0, total, UTF8);
        } catch (IOException e) {
            return null;
        }
    }

    public int getAgentId() {
        return agentId;
    }

    public void setAgentId(int agentId) {
        this.agentId = agentId;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public boolean isFailed() {
        return this.failed;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getOutput(int nbLine, long timeout, boolean extraBufferSpace) {
        return getOutput(nbLine, timeout);
    }
}