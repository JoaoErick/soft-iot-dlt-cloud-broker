package br.uefs.larsid.dlt.iot.soft.models;

import java.util.List;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;

import br.uefs.larsid.dlt.iot.soft.services.Controller;
import br.uefs.larsid.dlt.iot.soft.entities.Device;
import br.uefs.larsid.dlt.iot.soft.entities.Sensor;
import br.uefs.larsid.dlt.iot.soft.services.INode;
import br.uefs.larsid.dlt.iot.soft.tasks.SendTopKRequestTask;

public class Node implements INode {
    private List<Device> devices;
    // private List<String> authenticatedDevicesIds;

    private int sendTopKRequestTaskTime;
    private Timer sendTopKRequestTimer;

    private String deviceAPIAddress;

    private boolean hasCollectRealScoreService;

    private Controller controller;
    private boolean debugModeValue;
    private static final Logger logger = Logger.getLogger(Node.class.getName());

    public Node() {
    }

    public void start() {
        this.devices = new ArrayList<>();
        // this.authenticatedDevicesIds = new ArrayList<>();
        this.sendTopKRequestTimer = new Timer();
        // this.sendTopKRequestTimer.scheduleAtFixedRate(
        //         new SendTopKRequestTask(this),
        //         0,
        //         this.sendTopKRequestTaskTime * 1000);
    }

    /**
     * Executa o que foi definido na função quando o bundle for finalizado.
     */
    public void stop() {
        if (this.sendTopKRequestTimer != null) {
            this.sendTopKRequestTimer.cancel();
        }
    }

    /**
     * Adiciona os dispositivos que foram requisitados na lista de dispositivos.
     *
     * @param strDevices String - Dispositivos requisitados.
     */
    public void loadConnectedDevices() {
        List<Device> devicesTemp = new ArrayList<Device>();

        try {
            JSONArray jsonArrayDevices = new JSONArray(
                    ClientIotService.getApiIot(this.deviceAPIAddress));

            for (int i = 0; i < jsonArrayDevices.length(); i++) {
                JSONObject jsonDevice = jsonArrayDevices.getJSONObject(i);
                ObjectMapper mapper = new ObjectMapper();
                Device device = mapper.readValue(
                        jsonDevice.toString(),
                        Device.class);

                devicesTemp.add(device);

                List<Sensor> tempSensors = new ArrayList<Sensor>();
                JSONArray jsonArraySensors = jsonDevice.getJSONArray(
                        "sensors");

                for (int j = 0; j < jsonArraySensors.length(); j++) {
                    JSONObject jsonSensor = jsonArraySensors.getJSONObject(j);
                    Sensor sensor = mapper.readValue(
                            jsonSensor.toString(),
                            Sensor.class);
                    sensor.setDeviceAPIAddress(deviceAPIAddress);
                    tempSensors.add(sensor);
                }

                device.setSensors(tempSensors);
            }
        } catch (JsonParseException e) {
            printlnDebug("Verify the correct format of 'DevicesConnected' property in configuration file.");
            logger.log(Level.SEVERE, null, e);
        } catch (JsonMappingException e) {
            printlnDebug(
                    "Verify the correct format of 'DevicesConnected' property in configuration file.");
            logger.log(Level.SEVERE, null, e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, null, e);
        }

        this.devices = devicesTemp;

        printlnDebug(this.devices.size() + " devices\n");
    }

    public List<Device> getDevices() {
        return devices;
    }

    public void setDevices(List<Device> devices) {
        this.devices = devices;
    }

    // public List<String> getAuthenticatedDevicesIds() {
    //     return authenticatedDevicesIds;
    // }

    // public void setAuthenticatedDevicesIds(List<String> authenticatedDevicesIds) {
    //     this.authenticatedDevicesIds = authenticatedDevicesIds;
    // }

    public int getSendTopKRequestTaskTime() {
        return sendTopKRequestTaskTime;
    }

    public void setSendTopKRequestTaskTime(int sendTopKRequestTaskTime) {
        this.sendTopKRequestTaskTime = sendTopKRequestTaskTime;
    }

    public String getDeviceAPIAddress() {
        return deviceAPIAddress;
    }

    public void setDeviceAPIAddress(String deviceAPIAddress) {
        this.deviceAPIAddress = deviceAPIAddress;
    }

    public Controller getController() {
        return controller;
    }

    public void setController(Controller controller) {
        this.controller = controller;

        this.sendTopKRequestTimer.scheduleAtFixedRate(
                new SendTopKRequestTask(this, controller, debugModeValue),
                0,
                this.sendTopKRequestTaskTime * 1000);
    }

    public boolean hasCollectRealScoreService() {
      return hasCollectRealScoreService;
    }

    public void setHasCollectRealScoreService(boolean hasCollectRealScoreService) {
      this.hasCollectRealScoreService = hasCollectRealScoreService;
    }

    public boolean isDebugModeValue() {
        return this.debugModeValue;
    }

    public void setDebugModeValue(boolean debugModeValue) {
        this.debugModeValue = debugModeValue;
    }

    private void printlnDebug(String str) {
        if (debugModeValue) {
            logger.info(str);
        }
    }
}
