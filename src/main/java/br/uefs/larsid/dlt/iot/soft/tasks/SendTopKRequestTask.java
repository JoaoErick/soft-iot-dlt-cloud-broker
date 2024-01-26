package br.uefs.larsid.dlt.iot.soft.tasks;

import java.util.TimerTask;
import java.util.logging.Logger;

import br.uefs.larsid.dlt.iot.soft.models.Node;
import br.uefs.larsid.dlt.iot.soft.services.Controller;

public class SendTopKRequestTask extends TimerTask {
    private final Node node;
    private Controller controllerImpl;
    private boolean debugModeValue;
    
    private static final Logger logger = Logger.getLogger(
        SendTopKRequestTask.class.getName()
    );

    /**
     * Método construtor.
     *
     * @param node           NodeType - Nó que verificará os dispositivos que estão
     *                       conectados.
     * @param controllerImpl Controller - Controller that will make use of this
     *                       task.
     * @param debugModeValue boolean - How to debug the code.
     */
    public SendTopKRequestTask(
            Node node,
            Controller controllerImpl,
            boolean debugModeValue) {
        this.node = node;
        this.controllerImpl = controllerImpl;
        this.debugModeValue = debugModeValue;
    }

    @Override
    public void run() {
        printlnDebug("(Cloud Broker) Sending a Top-K request...");

        try {
            if (this.controllerImpl.getNodes() > 0) {
                this.controllerImpl.sendTopK();
            } else {
                printlnDebug("(Cloud Broker) There are no nodes connected yet!");
            }
        } catch (Exception e) {
            logger.severe("Error: Unable to send Top-K request!");
            logger.severe(e.getStackTrace().toString());
            logger.severe(e.getMessage());
            this.cancel();
        }
    }

    private void printlnDebug(String str) {
        if (debugModeValue) {
            logger.info(str);
        }
    }
}
